"""PERF-01 technical map baseline over the unchanged guarded Related driver.

Only a newly owned software emulator and the isolated verification APK are used.
Ready times include frame validation and are conservative instrumented bounds.
"""
from __future__ import annotations

import argparse
import base64
from collections import defaultdict
import csv
from datetime import datetime, timezone
import hashlib
import json
import math
import os
from pathlib import Path
import shutil
import statistics
import sys
import uuid

from mobile_test_support import PACKAGE
from mobile_native_log import start_native_log_capture
from run_mobile_tests import Verification, NativeCheckFailure

ROOT = Path(__file__).resolve().parents[1]
CLASS = "com.nextgis.mobile.rendering.Perf01MapBenchmarkTest"
AREA = "perf01-map-baseline"
COHORTS = {"ordinary": (20, 480), "heavy": (80, 6480)}
STYLE = "green/cyan alpha170; blue outline2; two fixed label123 red2.5 Open Sans Bold"


def number(value, *, positive=False):
    if (isinstance(value, bool) or not isinstance(value, (int, float))
            or not math.isfinite(value) or value < 0 or positive and value == 0):
        raise ValueError("Missing, nonfinite or invalid numeric observation")
    return value


def metric(values):
    values = sorted(number(value) for value in values)
    if not values:
        raise ValueError("An empty series has no percentile")
    return {"n": len(values), "p50": statistics.median(values),
            "p95": values[math.ceil(.95 * len(values)) - 1] if len(values) >= 20 else None,
            "min": values[0], "max": values[-1]}


def summarize(records, expected_n=20):
    if expected_n not in (1, 20):
        raise ValueError("Choose a one-sample smoke or twenty-sample technical baseline")
    cells, fixtures, windows = defaultdict(list), {}, set()
    run_ids = set()
    for record in records:
        cohort, mode = record.get("cohort"), record.get("mode")
        if cohort not in COHORTS or mode not in ("fresh", "warm"):
            raise ValueError("Unknown cohort or scene")
        if record.get("status") != "passed" or record.get("input_hashes_verified_after_measurement") is not True:
            raise ValueError("Failed or unverified native measurement")
        fixture = record["fixture"]
        side, features = COHORTS[cohort]
        if (fixture.get("schema"), fixture.get("cohort"), fixture.get("side"),
                fixture.get("features"), fixture.get("vertices"), fixture.get("crs"),
                fixture.get("radius_m"), fixture.get("spacing_m"), fixture.get("style_contract")) != (
                2, cohort, side, features, 65, "EPSG:3857", 140, 320, STYLE):
            raise ValueError("Fixture differs from the independently declared workload")
        if fixture.get("label_ids") != ([1, 80] if cohort == "ordinary" else [2431, 80]):
            raise ValueError("Control labels differ from the independently authored positions")
        hashes = fixture.get("input_sha256", [])
        if len(hashes) != 2 or any(not isinstance(h, str) or len(h) != 64
                                  or any(c not in "0123456789abcdef" for c in h) for h in hashes):
            raise ValueError("Missing original SQLite input hashes")
        ids = fixture.get("ids", [])
        if len(ids) != 2 or len(set(ids)) != 2 or any(type(i) is not int or i < 0 for i in ids):
            raise ValueError("Two distinct synthetic layer identities are required")
        signature = json.dumps(fixture, sort_keys=True)
        if cohort in fixtures and fixtures[cohort] != signature:
            raise ValueError("Cannot pool different fixture revisions")
        fixtures[cohort] = signature
        run_ids.add(fixture["run"])
        samples = record.get("samples", [])
        if not samples or mode == "fresh" and len(samples) != 1:
            raise ValueError("Incomplete phase")
        for sample in samples:
            if sample.get("mode") != mode:
                raise ValueError("Sample and phase scenes differ")
            index = sample.get("index")
            if isinstance(index, bool) or not isinstance(index, int) or not 0 <= index < expected_n:
                raise ValueError("Invalid trial index")
            ready = number(sample["validated_ready_upper_ms"], positive=True)
            for key in ("validated_sdk_frame_candidate_ms", "observer_attach_delay_ms", "quality_probe_ms"):
                if number(sample[key]) > ready:
                    raise ValueError("Frame/observer timestamps exceed the ready bound")
            number(sample["process_cpu_upper_ms"])
            quality = sample["quality"]
            window = tuple(number(quality[key], positive=True)
                           for key in ("width_px", "height_px", "density_dpi", "font_scale"))
            if tuple(quality[key] for key in ("zoom", "longitude", "latitude", "bearing", "tilt")) != (11, 0, 0, 0, 0):
                raise ValueError("Camera scenes differ")
            windows.add(window)
            coverage = quality["coverage"]
            if len(coverage) != 2 or len({row["source"] for row in coverage}) != 2:
                raise ValueError("Two independent live sources are required")
            for count, row in zip((side * side, 80), coverage):
                if not 0 < number(row["required"]) <= number(row["seen"]) <= count or number(row["placed_labels"]) <= 0:
                    raise ValueError("Incomplete native geometry/label coverage")
            if mode == "warm" and sample.get("same_live_providers") is not True:
                raise ValueError("Warm trial replaced its live providers")
            memory = sample["memory"]
            for key in ("java_used_bytes", "native_allocated_bytes", "pss_bytes", "rss_bytes", "observed_peak_rss_bytes", "rss_samples"):
                number(memory[key], positive=True)
            if memory["observed_peak_rss_bytes"] < memory["rss_bytes"]:
                raise ValueError("RSS endpoints must be included in the sampled maximum")
            process = sample.get("process")
            if not isinstance(process, str) or len(process.split(":")) != 2 or not all(p.isdigit() for p in process.split(":")):
                raise ValueError("Missing PID/start-tick process identity")
            if mode == "fresh" and process == fixture["seed_process"]:
                raise ValueError("Fresh scene reused the seed process")
            cells[cohort, mode].append(sample)
    if len(windows) != 1 or len(run_ids) != 1 or set(cells) != {(c, m) for c in COHORTS for m in ("fresh", "warm")}:
        raise ValueError("Do not aggregate incomplete or incomparable scenes")
    result = []
    for (cohort, mode), samples in sorted(cells.items()):
        if len(samples) != expected_n or {s["index"] for s in samples} != set(range(expected_n)):
            raise ValueError("Missing or duplicated trials")
        processes = {s["process"] for s in samples}
        if len(processes) != (expected_n if mode == "fresh" else 1):
            raise ValueError("Fresh/warm process ownership differs from the experiment")
        if mode == "warm" and len({tuple(row["source"] for row in s["quality"]["coverage"]) for s in samples}) != 1:
            raise ValueError("Warm scene source identity changed")
        row = {"cohort": cohort, "features": COHORTS[cohort][1], "scene": mode, "n": len(samples)}
        for key in ("validated_ready_upper_ms", "validated_sdk_frame_candidate_ms", "quality_probe_ms", "process_cpu_upper_ms"):
            row[key] = metric([s[key] for s in samples])
        for key in ("java_used_bytes", "native_allocated_bytes", "pss_bytes", "rss_bytes", "observed_peak_rss_bytes"):
            row[key] = metric([s["memory"][key] for s in samples])
        result.append(row)
    return {"schema": 1, "driver_cleanup": "pending", "stage": "technical_emulator_baseline" if expected_n == 20 else "smoke_only",
            "samples_per_cell": expected_n, "total_samples": expected_n * 4,
            "window": dict(zip(("width_px", "height_px", "density_dpi", "font_scale"), next(iter(windows)))),
            "estimators": {"p50": "sample median", "p95": "empirical nearest rank; omitted below20 samples"},
            "cells": result,
            "limits": ["Instrumented Activity launch/live return and observer-inclusive ready bounds; not ordinary APK launch time",
                       "Fresh process clears only private derived PBF cache; OS/font caches remain uncontrolled",
                       "Warm is return within one live provider namespace; cross-process SESSION keys are never called warm hits",
                       "SDK candidate alone does not establish a correct frame; validation overhead remains explicit",
                       "Bounded diagnostic scopes cannot supply an all-tile p95 or cache hit denominator",
                       "Sampled RSS/heap/PSS include instrumentation and validator; no leak, native-layer allocation or hard RSS cap claim",
                       "Software emulator timing does not establish phone speed, energy, real projects or field/sync acceptance"]}


def write_summary(output, summary, source_fingerprint):
    summary["source_fingerprint"] = source_fingerprint
    (output / "perf01-summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
    lines = ["# PERF-01: технические карты на эмуляторе", "",
             "Время включает наблюдателя и проверку полноты кадра. Это верхняя оценка для тестового приложения.", "",
             "Статус штатной очистки: " + summary.get("driver_cleanup", "pending") + ". См. result.json и cleanup.json.", "",
             "| Карта | Сцена | n | p50, мс | p95, мс | Наблюдаемый максимум RSS, МиБ |",
             "|---|---|---:|---:|---:|---:|"]
    rows = []
    for cell in summary["cells"]:
        ready = cell["validated_ready_upper_ms"]
        p95 = "—" if ready["p95"] is None else f'{ready["p95"]:.1f}'
        label = "новый процесс, пустой PBF-кэш" if cell["scene"] == "fresh" else "возврат в том же процессе"
        name = "обычная" if cell["cohort"] == "ordinary" else "тяжёлая"
        lines.append(f'| {name}: {cell["features"]} объектов | {label} | {cell["n"]} | {ready["p50"]:.1f} | {p95} | {cell["observed_peak_rss_bytes"]["max"] / 2**20:.1f} |')
        rows.append({"cohort": cell["cohort"], "features": cell["features"], "scene": cell["scene"], "n": cell["n"],
                     "p50_ready_upper_ms": ready["p50"], "p95_ready_upper_ms": ready["p95"],
                     "max_sampled_rss_bytes": cell["observed_peak_rss_bytes"]["max"]})
    lines += ["", "p50 — медиана; p95 — эмпирический nearest-rank, только от20 измерений. Выборка не гарантирует параметры всей будущей нагрузки.", "",
              "Общая PERF-01 остаётся открытой: реальные проекты/устройства, синхронизация, полная статистика тайлов и энергия сюда не входят.", "",
              "Исходные результаты фаз, SHA исходных SQLite-геометрий и контрольных подписей, APK/source/driver receipts и снимки находятся рядом с отчётом."]
    (output / "perf01-summary.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    with (output / "perf01-summary.csv").open("w", encoding="utf-8", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=list(rows[0])); writer.writeheader(); writer.writerows(rows)


class Perf01Verification(Verification):
    def __init__(self, args, samples):
        self.sample_count = samples
        self.run_id = uuid.uuid4().hex
        self.measurements = []
        super().__init__(args)
        if not self.emulator_profile or not self.guest_angle():
            raise ValueError("PERF-01 requires the verified local GuestAngle profile; no graphics fallback")

    def private_json(self, serial, relative):
        size = int(self.shell(serial, "run-as", PACKAGE, "stat", "-c", "%s", relative))
        if not 0 < size <= 1048576:
            raise ValueError("Native evidence exceeds the declared one-MiB bound")
        return json.loads(self.shell(serial, "run-as", PACKAGE, "cat", relative))

    def screenshot(self, serial, label):
        relative = "files/perf01-scene.png"
        size = int(self.shell(serial, "run-as", PACKAGE, "stat", "-c", "%s", relative))
        if not 0 < size <= 12 * 2**20:
            raise ValueError("Unexpected native screenshot size")
        raw = self.command([self.adb, "-s", serial, "shell", "run-as", PACKAGE, "base64", relative], timeout=30)
        data = base64.b64decode(raw)
        if len(data) != size or not data.startswith(b"\x89PNG\r\n\x1a\n"):
            raise ValueError("Incomplete native screenshot transfer")
        (self.output / (label + ".png")).write_bytes(data)

    def native(self, serial, selectors, name, **options):
        if selectors != [CLASS]:
            return super().native(serial, selectors, name, **options)
        if (self.args.scope != "related" or self.args.area != [AREA]
                or serial != self.owned_emulator or serial != self.emulator
                or getattr(self, "phone", None) is not None):
            raise ValueError("PERF-01 owns only this Related run's new isolated emulator")
        results = []
        for cohort in COHORTS:
            method = CLASS + "#" + cohort + "TechnicalMap"
            seed = super().native(serial, [method], "perf01-" + cohort + "-seed",
                                  extra={"perfStage": "seed", "perfRun": self.run_id})
            results.append(seed)
            if seed.get("failed") or seed.get("skipped"):
                raise NativeCheckFailure("The technical seed did not pass completely")
            marker = self.private_json(serial, "files/perf01-fixture.json")
            (self.output / (cohort + "-fixture.json")).write_text(json.dumps(marker, indent=2), encoding="utf-8")
            for mode in ("fresh", "warm"):
                for index in range(self.sample_count if mode == "fresh" else 1):
                    self.assert_build_pair()
                    self.shell(serial, "am", "force-stop", PACKAGE)
                    self.shell(serial, "am", "force-stop", PACKAGE + ".test")
                    label = f"perf01-{cohort}-{mode}-{index:02}"
                    extra = {"perfStage": "sample", "perfMode": mode, "perfRun": self.run_id,
                             "perfSample": index, "perfSamples": self.sample_count if mode == "warm" else 1,
                             "perfReadyTimeoutSeconds": 120,
                             "perfCleanup": str(mode == "warm").lower(),
                             "perfScreenshot": str(index == 0).lower()}
                    capture = None
                    if self.sample_count == 1:
                        try:
                            capture = start_native_log_capture(self.adb, serial, self.output / (label + "-uid-logcat.txt"))
                        except Exception as log_error:
                            self.checks.setdefault("perf01_diagnostic_failures", []).append(type(log_error).__name__)
                    try:
                        result = super().native(serial, [method], label, reset=False, extra=extra)
                    except Exception:
                        try:
                            failed = self.private_json(serial, "files/perf01-samples.json")
                            if (failed.get("cohort"), failed.get("mode"), failed.get("phase_index"),
                                    failed.get("fixture", {}).get("run")) != (cohort, mode, index, self.run_id):
                                raise ValueError("Rejected stale phase evidence")
                            (self.output / (label + "-failed.json")).write_text(json.dumps(failed, indent=2), encoding="utf-8")
                            self.screenshot(serial, label + "-failed")
                        except Exception as evidence_error:
                            self.checks.setdefault("perf01_diagnostic_failures", []).append(type(evidence_error).__name__)
                        raise
                    finally:
                        if capture is not None:
                            try:
                                self.checks.setdefault("perf01_uid_logs", []).append(capture.stop())
                            except Exception as log_error:
                                self.checks.setdefault("perf01_diagnostic_failures", []).append(type(log_error).__name__)
                    results.append(result)
                    if result.get("failed") or result.get("skipped"):
                        raise NativeCheckFailure("A required benchmark trial failed or was skipped")
                    data = self.private_json(serial, "files/perf01-samples.json")
                    if (data.get("cohort"), data.get("mode"), data.get("phase_index"),
                            data.get("fixture", {}).get("run")) != (cohort, mode, index, self.run_id):
                        raise ValueError("Native evidence belongs to a different phase")
                    self.measurements.append(data)
                    (self.output / (label + ".json")).write_text(json.dumps(data, indent=2), encoding="utf-8")
                    if index == 0:
                        self.screenshot(serial, cohort + "-" + mode)
                    print(f"PERF-01 {cohort}/{mode}: {index + 1 if mode == 'fresh' else self.sample_count} measurements", flush=True)
        summary = summarize(self.measurements, self.sample_count)
        write_summary(self.output, summary, self.fingerprint)
        self.checks["perf01"] = {"total_samples": summary["total_samples"], "samples_per_cell": self.sample_count,
                                "summary": str(self.output / "perf01-summary.json"), "scope": summary["stage"]}
        return {"passed": [case for result in results for case in result["passed"]],
                "skipped": [], "failed": False, "expected_crash": False}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--samples", type=int, choices=(1, 20), default=20)
    parser.add_argument("--skip-build", action="store_true")
    parser.add_argument("--sdk", default=os.environ.get("ANDROID_SDK_ROOT") or os.environ.get("ANDROID_HOME")
                        or str(Path.home() / "AppData/Local/Android/Sdk"))
    supplied = parser.parse_args()
    output = supplied.output.resolve()
    if output.exists() or output.is_relative_to(ROOT) or os.name == "nt" and output.drive.upper() != "Q:":
        parser.error("Use a new owned output directory on Q outside the source checkout")
    existing = output.parent
    while not existing.exists():
        existing = existing.parent
    if shutil.disk_usage(existing).free < 30 * 2**30:
        parser.error("Less than30 GiB free; review reuse/cleanup before starting another run")
    output.mkdir(parents=True)
    storage = {"owner_task": "android-20261115-perf01-emulator-baseline", "state": "active",
               "created_at": datetime.now(timezone.utc).isoformat(),
               "purpose": "Synthetic ordinary/heavy map timings and bounded compact evidence",
               "disposable": "No SDK/runtime copies; owned read-only VM lifecycle belongs to the unchanged guarded driver",
               "retained": "JSON/CSV/Markdown/raw native logs and four synthetic PNGs; keep for baseline review",
               "retention": "Retain useful baseline evidence through delivery/final acceptance; review failed run within7 days"}
    def storage_receipt():
        (output / "storage-manifest.json").write_text(json.dumps(storage, indent=2), encoding="utf-8")
    storage_receipt()
    provenance = {"scope": "PERF01 only; original APK/source/ANGLE/WHPX/RAM/Job/cleanup guards retained",
                  "created_at": datetime.now(timezone.utc).isoformat(), "samples_per_cell": supplied.samples,
                  "driver_sha256": hashlib.sha256((ROOT / "tools/run_mobile_tests.py").read_bytes()).hexdigest(),
                  "harness_sha256": hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
                  "native_sha256": hashlib.sha256((ROOT / "app/src/androidTest/java/com/nextgis/mobile/rendering/Perf01MapBenchmarkTest.java").read_bytes()).hexdigest()}
    (output / "benchmark-provenance.json").write_text(json.dumps(provenance, indent=2), encoding="utf-8")
    args = argparse.Namespace(scope="related", area=[AREA], phone_serial=None, emulator_serial=None,
        phone_only=False, allow_emulator=True, emulator_graphics="guest-angle", emulator_runtime=None, avd=None,
        test_project=None, test_project_fixture=None, sdk=supplied.sdk, output=str(output), skip_build=supplied.skip_build)
    try:
        Perf01Verification(args, supplied.samples).run()
        summary = json.loads((output / "perf01-summary.json").read_text(encoding="utf-8"))
        summary["driver_cleanup"] = "passed_after_guarded_driver_completed"
        write_summary(output, summary, summary["source_fingerprint"])
        storage.update(state="completed", closed_at=datetime.now(timezone.utc).isoformat(),
                       resource_cleanup_receipt="cleanup.json; guarded driver completed before success")
        storage_receipt()
    except Exception as error:
        storage.update(state="held", failure=str(error), review="Retain compact diagnostics; review within7 days")
        storage_receipt()
        (output / "benchmark-failure.json").write_text(json.dumps({"status": "failed", "error": str(error)}, indent=2), encoding="utf-8")
        print("PERF-01 failed: " + str(error), file=sys.stderr, flush=True)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
