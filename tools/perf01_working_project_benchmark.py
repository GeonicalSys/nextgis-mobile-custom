"""Opt-in physical-phone PERF01 against approved project482, without business writes.

Probe checks isolated-account GET access. Explicit smoke/baseline stages download
one private layer and measure incoming-only refresh and independently checked map
frames. No working app is installed, cleared or stopped.
"""
from __future__ import annotations
import argparse
import json
import os
from pathlib import Path
import uuid
import hashlib
import base64
import statistics
import math
from mobile_test_support import PACKAGE, phone_unlocked
from run_mobile_tests import Verification, NativeCheckFailure

CLASS = "com.nextgis.mobile.rendering.Perf01WorkingProjectBenchmarkTest"
AREA = "perf01-working-project"
SOURCE_ROOT = "/sdcard/Android/data/com.nextgis.mobile.geonical/files/map/collector_projects/collector_482_840b9486"


def summarize(records, n):
    if n not in (1, 20):
        raise ValueError("Use one-sample calibration or twenty-sample baseline")
    cells = {}
    signatures = set()
    runs = set()
    for record in records:
        stage = record.get("stage")
        if (stage not in ("pull", "fresh", "warm") or record.get("status") != "passed"
                or record.get("project_id") != 482 or record.get("resource_id") != 400
                or record.get("server_write_authority") is not False):
            raise ValueError("Failed or differently authorized observation")
        runs.add(record["run"])
        signatures.add(json.dumps(record["fixture"], sort_keys=True))
        cells.setdefault(stage, []).extend(record["samples"])
    if len(runs) != 1 or len(signatures) != 1 or set(cells) != {"pull", "fresh", "warm"}:
        raise ValueError("Missing cells or mixed fixture revisions")
    result = []
    for stage, samples in sorted(cells.items()):
        if len(samples) != n or {s["index"] for s in samples} != set(range(n)):
            raise ValueError("Missing or duplicated observations")
        key = "pull_upper_ms" if stage == "pull" else "validated_ready_upper_ms"
        values = [s[key] for s in samples]
        if any(type(v) not in (int, float) or not math.isfinite(v) or v <= 0 for v in values):
            raise ValueError("Invalid timing observation")
        values.sort()
        if stage == "pull" and any(s["input_hash_after"] != json.loads(next(iter(signatures)))["input_sha256"] for s in samples):
            raise ValueError("Pull inputs changed")
        if stage != "pull":
            for s in samples:
                if not 0 < s["quality"]["required"] <= s["quality"]["seen"]:
                    raise ValueError("Incomplete visible geometry coverage")
        processes = {s["process"] for s in samples} if stage != "pull" else set()
        if stage == "fresh" and len(processes) != n or stage == "warm" and len(processes) != 1:
            raise ValueError("Fresh/warm process ownership differs")
        result.append(dict(scene=stage, n=n, p50_ms=statistics.median(values),
                           p95_ms=values[math.ceil(.95*n)-1] if n == 20 else None,
                           min_ms=values[0], max_ms=values[-1],
                           max_sampled_rss_mib=max(s["memory"]["observed_peak_rss_bytes"] for s in samples)/2**20))
    return dict(stage="baseline" if n == 20 else "calibration_only", cells=result,
                fixture=json.loads(next(iter(signatures))), total_samples=3*n,
                limits=["Instrumented isolated APK; not ordinary signed-app launch time",
                        "One real PostGIS layer; project district filter preserved; no server business writes",
                        "Map first Activity launch or live return includes validation; original-bbox/FID oracle, labels not independently accepted",
                        "OS/font caches uncontrolled; fresh process uses a new provider namespace without clearing user caches",
                        "Pull-only API on a private copy; not whole project/account sync or outgoing/edit matrix",
                        "UID network bytes include background activity; exact HTTP request counts remain unknown",
                        "No phone settings, production app/data, release, general Full or energy acceptance"])


class WorkingVerification(Verification):
    def __init__(self, args, stage, samples=1, run_id=None):
        self.stage = stage
        self.sample_count = samples
        self.run_id = run_id or uuid.uuid4().hex
        self.measurements = []
        super().__init__(args)

    def prepare(self, serial):
        raise RuntimeError("This authenticated read-only stage must preserve verification accounts/data")

    def phone_native(self, selector, name):
        if (self.args.scope != "related" or self.args.area != [AREA]
                or selector != CLASS or not self.args.phone_only
                or self.phone != self.args.phone_serial or hasattr(self, "emulator")):
            raise RuntimeError("Use only the explicitly selected phone and PERF01 area")
        if self.stage in ("smoke", "baseline"):
            self.stage_input()
            for stage, count in (("seed", 1), ("pull", self.sample_count)):
                self.phase(stage, name+"-"+stage, count=count)
            for index in range(self.sample_count):
                self.phase("fresh", name+f"-fresh-{index:02}", index=index)
            self.phase("warm", name+"-warm", count=self.sample_count)
            summary = summarize(self.measurements, self.sample_count)
            (self.output/"working-baseline-summary.json").write_text(json.dumps(summary,ensure_ascii=False,indent=2),encoding="utf-8")
            self.phase("cleanup", name+"-cleanup")
            return self.results[-1]
        if self.stage == "seed": self.stage_input()
        return self.phase(self.stage, name)

    def stage_input(self):
        existing=self.shell(self.phone,"run-as",PACKAGE,"ls","files").splitlines()
        if "perf01-working-state.json" in existing: raise RuntimeError("An existing owned fixture must be reviewed/cleaned before reseeding")
        def read(relative):
            return self.shell(self.phone,"cat",SOURCE_ROOT+"/"+relative)
        group_raw=read("map.ngm");config_raw=read("layer_bfb3b78546c6483e8ec079074a980b9c/config.json");project=json.loads(read("project.json"))
        group,config=json.loads(group_raw),json.loads(config_raw)
        if (project.get("project_remote_id"),project.get("project_uid"),config.get("id"),config.get("ngw_layer_type"),config.get("sync_direction")) != (482,"collector:setnovo.nextgis.com:482",400,4,2):
            raise ValueError("Approved source identity or read-only direction changed")
        config.pop("account",None)
        source=dict(project_id=482,project_uid=project["project_uid"],config=config,
                    district=group.get("collector_district") or (group.get("collector_project") or {}).get("district") or "",
                    source_config_sha256=hashlib.sha256(config_raw.encode()).hexdigest(),
                    source_group_sha256=hashlib.sha256(group_raw.encode()).hexdigest())
        local=self.output/"source-input-private.json";local.write_text(json.dumps(source,ensure_ascii=False,indent=2),encoding="utf-8")
        if local.stat().st_size > 1048576: raise ValueError("Source metadata exceeds oneMiB")
        remote="/data/local/tmp/lisa-perf01-input-"+self.run_id+".json"
        self.command([self.adb,"-s",self.phone,"push",str(local),remote])
        self.shell(self.phone,"run-as",PACKAGE,"cp",remote,"files/perf01-working-input.json")
        expected=hashlib.sha256(local.read_bytes()).hexdigest()
        if self.shell(self.phone,"sha256sum",remote).split()[0]!=expected: raise ValueError("Input transfer changed; remote retained")
        self.shell(self.phone,"rm",remote)

    def phase(self, stage, name, *, count=1, index=0):
        if self.shell(self.phone, "am", "get-current-user").strip() != "0":
            raise RuntimeError("Only the primary phone user is authorized")
        if not phone_unlocked(self.shell(self.phone, "dumpsys", "trust")):
            raise RuntimeError("Unlock the selected phone; no test has started")
        self.shell(self.phone, "cmd", "statusbar", "collapse")
        self.shell(self.phone, "am", "force-stop", PACKAGE)
        self.shell(self.phone, "am", "force-stop", PACKAGE + ".test")
        try:
            result = self.native(self.phone, [CLASS], name, reset=False,
                extra={"perfStage":stage,"perfProject":"482","perfRun":self.run_id,"perfSamples":count,
                       "perfSample":index,"perfScreenshot":str(index==0 and stage in ("fresh","warm")).lower()})
        except Exception:
            try:
                failed=json.loads(self.shell(self.phone,"run-as",PACKAGE,"cat","files/perf01-working-result.json"))
                if failed.get("run")==self.run_id and failed.get("stage")==stage:
                    (self.output/(name+"-failed.json")).write_text(json.dumps(failed,ensure_ascii=False,indent=2),encoding="utf-8")
            except Exception: pass
            raise
        if result.get("failed") or result.get("skipped"):
            raise NativeCheckFailure("Required PERF01 read-only probe failed or skipped")
        relative = "files/perf01-working-result.json"
        size = int(self.shell(self.phone, "run-as", PACKAGE, "stat", "-c", "%s", relative))
        if not 0 < size <= 1048576:
            raise ValueError("Unexpected bounded result size")
        data = json.loads(self.shell(self.phone, "run-as", PACKAGE, "cat", relative))
        if (data.get("stage"), data.get("project_id"), data.get("resource_id"), data.get("run"),
                data.get("server"), data.get("server_write_authority")) != (
                stage, 482, 400, self.run_id, "setnovo.nextgis.com", False):
            raise ValueError("Stale or differently authorized result")
        (self.output / (name+".json")).write_text(
            json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
        self.checks["perf01_readonly_probe"] = data
        expected_status={"probe":"authenticated_data_read","seed":"seeded_read_only_copy","cleanup":"owned_fixture_cleaned_account_preserved"}.get(stage,"passed")
        if data.get("schema") != 1 or data.get("status") != expected_status:
            raise NativeCheckFailure("The requested read-only stage did not complete")
        if stage == "probe" and (data.get("status") != "authenticated_data_read"
                or data.get("metadata_http_status") != 200 or data.get("data_read_http_status") != 200
                or data.get("resource_class") != "postgis_layer"
                or type(data.get("unfiltered_server_count")) is not int or data["unfiltered_server_count"] < 0):
            raise NativeCheckFailure("The isolated account has not confirmed authenticated read access")
        if stage in ("pull","fresh","warm"): self.measurements.append(data)
        if index==0 and stage in ("fresh","warm"):
            image_size=int(self.shell(self.phone,"run-as",PACKAGE,"stat","-c","%s","files/perf01-scene.png"))
            if not 0 < image_size <= 12*2**20: raise ValueError("Scene image exceeds transfer bound")
            encoded=self.command([self.adb,"-s",self.phone,"shell","run-as",PACKAGE,"base64","files/perf01-scene.png"])
            image=base64.b64decode(encoded)
            if len(image)>12*2**20 or not image.startswith(b"\x89PNG\r\n\x1a\n"): raise ValueError("Invalid bounded scene image")
            (self.output/(stage+".png")).write_bytes(image)
        print("PERF01 read-only "+stage+" completed; observations="+str(len(data.get("samples",[]))),flush=True)
        return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--phone-serial", required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--stage", choices=("probe","seed","pull","fresh","warm","cleanup","smoke","baseline"), default="probe")
    parser.add_argument("--run", help="Exact32hex owner nonce for resuming an existing private fixture")
    parser.add_argument("--skip-build", action="store_true")
    options = parser.parse_args()
    output = options.output.resolve()
    if output.exists() or os.name == "nt" and output.drive.upper() != "Q:":
        parser.error("Use a new task-owned output directory on Q")
    args = argparse.Namespace(scope="related", area=[AREA], phone_serial=options.phone_serial,
        emulator_serial=None, phone_only=True, allow_emulator=False, emulator_graphics="native",
        emulator_runtime=None, avd=None, test_project=None, test_project_fixture=None,
        sdk=os.environ.get("ANDROID_SDK_ROOT") or os.environ.get("ANDROID_HOME"),
        output=str(output), skip_build=options.skip_build)
    if options.run and (len(options.run)!=32 or any(c not in "0123456789abcdef" for c in options.run)):
        parser.error("Invalid fixture owner nonce")
    WorkingVerification(args, options.stage,20 if options.stage=="baseline" else 1,options.run).run()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
