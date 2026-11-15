"""Independent numeric examples and rejection of incomparable native measurements."""
from copy import deepcopy
from pathlib import Path
import sys
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from perf01_emulator_benchmark import COHORTS, STYLE, Perf01Verification, Verification, metric, summarize


def observations(n=20):
    records = []
    for cohort, (side, features) in COHORTS.items():
        fixture = dict(schema=2, run="technical-run", cohort=cohort, side=side,
                       features=features, vertices=65, crs="EPSG:3857", radius_m=140,
                       spacing_m=320, style_contract=STYLE, seed_process="9:100", ids=[1, 2],
                       label_ids=[1, 80] if cohort == "ordinary" else [2431, 80],
                       input_sha256=["a" * 64, "b" * 64], root="synthetic-only")
        def sample(index, mode):
            return dict(index=index, mode=mode, process=f"{100+index}:200" if mode == "fresh" else "500:300",
                        validated_ready_upper_ms=index + 1, validated_sdk_frame_candidate_ms=index + .5,
                        quality_probe_ms=.1, observer_attach_delay_ms=0, process_cpu_upper_ms=index,
                        same_live_providers=True,
                        quality=dict(width_px=900, height_px=1200, density_dpi=400, font_scale=1,
                                     zoom=11, longitude=0, latitude=0, bearing=0, tilt=0,
                                     coverage=[dict(required=10, seen=12, placed_labels=2, source="main"),
                                               dict(required=8, seen=9, placed_labels=1, source="overlay")]),
                        memory=dict(java_used_bytes=10, native_allocated_bytes=20, pss_bytes=30,
                                    rss_bytes=40, observed_peak_rss_bytes=45, rss_samples=2, graphics_pss_kib=None))
        for index in range(n):
            records.append(dict(cohort=cohort, mode="fresh", fixture=deepcopy(fixture),
                                status="passed", input_hashes_verified_after_measurement=True,
                                samples=[sample(index, "fresh")]))
        records.append(dict(cohort=cohort, mode="warm", fixture=deepcopy(fixture), status="passed",
                            input_hashes_verified_after_measurement=True,
                            samples=[sample(index, "warm") for index in range(n)]))
    return records


class Perf01Evidence(unittest.TestCase):
    def test_missing_profile_and_graphics_fallback_are_refused_before_launch(self):
        for profile, mode, allowed in (({}, "guest-angle", False), ({"avd": "verified"}, "native", False),
                                       ({"avd": "verified"}, "guest-angle", True)):
            def initialize(instance, args):
                instance.emulator_profile, instance.graphics_mode = profile, mode
                instance.args = args
            with self.subTest(profile=profile, mode=mode), patch.object(Verification, "__init__", initialize):
                if allowed:
                    self.assertEqual(1, Perf01Verification(None, 1).sample_count)
                else:
                    with self.assertRaises(ValueError):
                        Perf01Verification(None, 1)

    def test_known_percentiles_and_four_independent_cells(self):
        result = summarize(observations())
        self.assertEqual(80, result["total_samples"])
        self.assertEqual(4, len(result["cells"]))
        for cell in result["cells"]:
            self.assertEqual(10.5, cell["validated_ready_upper_ms"]["p50"])
            self.assertEqual(19, cell["validated_ready_upper_ms"]["p95"])
            self.assertEqual(45, cell["observed_peak_rss_bytes"]["max"])

    def test_smoke_never_claims_a_p95_baseline(self):
        result = summarize(observations(1), 1)
        self.assertEqual("smoke_only", result["stage"])
        self.assertTrue(all(c["validated_ready_upper_ms"]["p95"] is None for c in result["cells"]))

    def test_nonfinite_negative_boolean_and_empty_series_are_rejected(self):
        for values in ([], [float("nan")], [float("inf")], [-1], [True]):
            with self.subTest(values=values), self.assertRaises(ValueError):
                metric(values)

    def test_missing_and_duplicate_trials_are_not_imputed(self):
        for records in (observations()[1:], observations() + observations()[:1]):
            with self.assertRaises(ValueError):
                summarize(records)

    def test_changed_fixture_window_and_failed_phase_cannot_be_pooled(self):
        mutations = [
            lambda r: r[1]["fixture"]["input_sha256"].__setitem__(0, "c" * 64),
            lambda r: r[1]["samples"][0]["quality"].__setitem__("width_px", 901),
            lambda r: r[1].__setitem__("status", "failed"),
            lambda r: r[1].__setitem__("input_hashes_verified_after_measurement", False),
            lambda r: r[1]["samples"][0]["quality"]["coverage"][0].__setitem__("placed_labels", 0),
        ]
        for change in mutations:
            records = observations(); change(records)
            with self.subTest(change=change), self.assertRaises(ValueError):
                summarize(records)

    def test_fresh_process_and_live_warm_provider_requirements_are_independent(self):
        for change in (
            lambda r: r[1]["samples"][0].__setitem__("process", r[0]["samples"][0]["process"]),
            lambda r: r[20]["samples"][1].__setitem__("same_live_providers", False),
            lambda r: r[20]["samples"][1].__setitem__("process", "501:300"),
            lambda r: r[20]["samples"][1]["quality"]["coverage"][0].__setitem__("source", "replacement"),
        ):
            records = observations(); change(records)
            with self.subTest(change=change), self.assertRaises(ValueError):
                summarize(records)


if __name__ == "__main__":
    unittest.main()
