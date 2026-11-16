from pathlib import Path
from types import SimpleNamespace
import json
import copy
import sys
import tempfile
import unittest
from unittest.mock import Mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from perf01_working_project_benchmark import AREA, CLASS, WorkingVerification, NativeCheckFailure, summarize


class WorkingReadOnlyBoundaries(unittest.TestCase):
    def instance(self, output):
        value = object.__new__(WorkingVerification)
        value.args = SimpleNamespace(scope="related", area=[AREA], phone_only=True, phone_serial="phone-1")
        value.phone, value.stage, value.run_id = "phone-1", "probe", "a" * 32
        value.output, value.checks = Path(output), {}
        data = dict(schema=1, stage="probe", project_id=482, resource_id=400, run="a" * 32,
                    server="setnovo.nextgis.com", server_write_authority=False,
                    status="authenticated_data_read", metadata_http_status=200, data_read_http_status=200,
                    resource_class="postgis_layer", unfiltered_server_count=23)
        def shell(serial, *args):
            if args == ("am", "get-current-user"): return "0"
            if args == ("dumpsys", "trust"): return 'User "Owner" (id=0, flags=0x4c13) (current): deviceLocked=0'
            if args[0:3] == ("run-as", "com.nextgis.mobile.verification.debug", "stat"): return str(len(json.dumps(data)))
            if args[0:3] == ("run-as", "com.nextgis.mobile.verification.debug", "cat"): return json.dumps(data)
            return ""
        value.shell = Mock(side_effect=shell)
        value.native = Mock(return_value=dict(passed=[CLASS + "#readOnlyWorkingProject"], failed=False, skipped=[]))
        return value, data

    def test_account_data_can_never_be_reset_by_generic_preparation(self):
        with self.assertRaisesRegex(RuntimeError, "preserve"):
            object.__new__(WorkingVerification).prepare("phone-1")

    def test_only_isolated_processes_stop_and_native_never_resets_data(self):
        with tempfile.TemporaryDirectory() as directory:
            value, _ = self.instance(directory)
            value.phone_native(CLASS, "probe")
            self.assertIs(value.native.call_args.kwargs["reset"], False)
            stopped = [c.args[3] for c in value.shell.call_args_list if c.args[1:3] == ("am", "force-stop")]
            self.assertEqual(["com.nextgis.mobile.verification.debug", "com.nextgis.mobile.verification.debug.test"], stopped)
            self.assertFalse(any("clear" in c.args for c in value.shell.call_args_list))

    def test_other_user_wrong_device_and_emulator_are_rejected_before_native(self):
        mutations = [lambda v: setattr(v.args, "phone_serial", "other"),
                     lambda v: setattr(v, "emulator", "emulator-1"),
                     lambda v: setattr(v.args, "area", ["layers"]),
                     lambda v: setattr(v.shell, "side_effect", lambda *a: "10")]
        for change in mutations:
            with self.subTest(change=change), tempfile.TemporaryDirectory() as directory:
                value, _ = self.instance(directory); change(value)
                with self.assertRaises(RuntimeError): value.phone_native(CLASS, "probe")
                value.native.assert_not_called()

    def test_wrong_dataset_stale_nonce_and_unconfirmed_access_cannot_pass(self):
        mutations = [lambda d: d.update(project_id=1403), lambda d: d.update(resource_id=398),
                     lambda d: d.update(run="b" * 32), lambda d: d.update(server_write_authority=True),
                     lambda d: d.update(status="auth_required"), lambda d: d.update(status="data_read_rejected"),
                     lambda d: d.update(metadata_http_status=403), lambda d: d.update(data_read_http_status=403),
                     lambda d: d.update(resource_class="vector_layer"),
                     lambda d: d.update(unfiltered_server_count=True), lambda d: d.update(unfiltered_server_count=-1)]
        for change in mutations:
            with self.subTest(change=change), tempfile.TemporaryDirectory() as directory:
                value, data = self.instance(directory); change(data)
                with self.assertRaises((ValueError, NativeCheckFailure)): value.phone_native(CLASS, "probe")


class WorkingSummary(unittest.TestCase):
    def records(self, n=20):
        records=[]
        for stage in ("pull","fresh","warm"):
            samples=[]
            for i in range(n):
                samples.append(dict(index=i,pull_upper_ms=i+1,validated_ready_upper_ms=i+1,
                    input_hash_after="abc",process=str(i) if stage=="fresh" else "one",
                    quality=dict(required=2,seen=2),memory=dict(observed_peak_rss_bytes=2**20)))
            records.append(dict(stage=stage,status="passed",run="a"*32,project_id=482,
                resource_id=400,server_write_authority=False,fixture=dict(input_sha256="abc"),samples=samples))
        return records

    def test_percentiles_are_from_twenty_complete_observations(self):
        summary=summarize(self.records(),20)
        self.assertEqual(60,summary["total_samples"])
        for cell in summary["cells"]:
            self.assertEqual((10.5,19,1),(cell["p50_ms"],cell["p95_ms"],cell["max_sampled_rss_mib"]))
        self.assertIsNone(summarize(self.records(1),1)["cells"][0]["p95_ms"])

    def test_missing_mixed_failed_and_invalid_observations_cannot_be_pooled(self):
        mutations=[lambda r:r.pop(),lambda r:r[0]["samples"].pop(),
            lambda r:r[0]["samples"][1].update(index=0),lambda r:r[0].update(status="failed"),
            lambda r:r[0].update(server_write_authority=True),lambda r:r[0].update(run="b"*32),
            lambda r:r[0]["fixture"].update(input_sha256="changed"),
            lambda r:r[0]["samples"][0].update(input_hash_after="changed"),
            lambda r:r[1]["samples"][0]["quality"].update(seen=0),
            lambda r:r[1]["samples"][1].update(process="0"),
            lambda r:r[2]["samples"][1].update(process="other")]
        for value in (True,0,-1,float("nan"),float("inf"),"3"):
            mutations.append(lambda r,v=value:r[0]["samples"][0].update(pull_upper_ms=v))
        for mutate in mutations:
            with self.subTest(mutate=mutate),self.assertRaises(ValueError):
                records=copy.deepcopy(self.records());mutate(records);summarize(records,20)


if __name__ == "__main__":
    unittest.main()
