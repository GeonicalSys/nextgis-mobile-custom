"""Opt-in synthetic night checks on one explicit physical verification device.

The inherited runner binds source/APK identities. No generic preparation or data
reset is allowed. WebGIS credentials stay on the host; device HTTP uses an owned
loopback gateway and exact new resource IDs. This is Related, never publication.
"""
from __future__ import annotations
import argparse
import base64
import copy
import hashlib
import json
import math
import os
from pathlib import Path
import re
import statistics
import threading
import time
import uuid
from urllib.parse import urlsplit
from http.server import ThreadingHTTPServer
import requests
from mobile_test_support import PACKAGE, expand_selectors, phone_unlocked
from run_mobile_tests import Verification, NativeCheckFailure
from perf01_owned_gateway import OwnedGateway
from perf01_tile_profile_summary import summarize as tile_summary

SYNC="com.nextgis.mobile.rendering.Perf01NightSyncTest#authorizedSyntheticNightStage"
MAP="com.nextgis.mobile.rendering.Perf01MapBenchmarkTest#heavyTechnicalMap"
FIELDS=("fixture_key","revision","test_label","nullable_text")


def polygon_coordinates(wkt):
    match=re.fullmatch(r"POLYGON\s*\(\s*\(([^()]+)\)\s*\)",wkt,re.I)
    if not match:raise ValueError("Only the authored single-ring polygon fixture is accepted")
    points=[]
    for pair in match[1].split(","):
        values=[float(value) for value in pair.split()]
        if len(values)!=2 or not all(math.isfinite(v) for v in values):raise ValueError("Invalid fixture coordinates")
        points.append(values)
    if len(points)<4 or points[0]!=points[-1]:raise ValueError("An original closed polygon is required")
    return points


def server_rows(features):
    result={}
    for feature in features:
        identity=feature["id"]
        if type(identity) is not int or identity in result:raise ValueError("Duplicate or invalid server FID")
        fields={key:feature["fields"][key] for key in FIELDS}
        result[identity]={"fields":fields,"coordinates":polygon_coordinates(feature["geom"])}
    return result


def compare_native(content,expected):
    rows=content["rows"]
    if content["count"]!=len(expected) or len(rows)!=len(expected):raise ValueError("Native feature count differs")
    seen=set()
    for row in rows:
        identity=row["id"]
        if type(identity) is not int or identity in seen or identity not in expected:raise ValueError("Native FIDs differ")
        seen.add(identity);wanted=expected[identity]
        if type(row["revision"]) is not int or {key:row[key] for key in FIELDS}!=wanted["fields"]:raise ValueError("Saved fields, including NULL/empty, differ")
        geometry=row["geom"]
        if geometry["type"]!="Polygon" or len(geometry["coordinates"])!=1:raise ValueError("Original geometry role differs")
        points=geometry["coordinates"][0]
        if len(points)!=len(wanted["coordinates"]):raise ValueError("Original polygon vertices differ")
        for actual,want in zip(points,wanted["coordinates"]):
            if len(actual)!=2 or any(type(v) not in (int,float) or not math.isfinite(v) for v in actual) or any(abs(a-b)>0.00001 for a,b in zip(actual,want)):
                raise ValueError("Original geometry changed")
    if seen!=set(expected):raise ValueError("Missing native features")


def distribution(values):
    if any(type(value) not in (int,float) or not math.isfinite(value) or value<=0 for value in values):
        raise ValueError("Invalid complete timing population")
    values=sorted(values)
    return dict(n=len(values),p50_ms=statistics.median(values) if values else None,
                p95_ms=values[math.ceil(.95*len(values))-1] if len(values)>=20 else None)


class NightVerification(Verification):
    def __init__(self,args,mode,*,fixture=None,samples=1,sides=(100,224),run=None,soak_minutes=0):
        self.mode=mode;self.fixture=fixture;self.samples=samples;self.sides=sides;self.run_id=run or uuid.uuid4().hex
        self.phase_index=0;self.measurements=[];self.native_artifacts=[]
        self.soak_minutes=soak_minutes
        super().__init__(args)

    def prepare(self,serial):
        raise RuntimeError("Night checks preserve existing verification accounts/data; generic preparation is forbidden")

    def ready(self):
        if self.phone!=self.args.phone_serial or hasattr(self,"emulator") or not self.args.phone_only:
            raise RuntimeError("One explicitly selected physical device is required")
        if self.shell(self.phone,"am","get-current-user").strip()!="0":raise RuntimeError("Only primary user0 is authorized")
        if not phone_unlocked(self.shell(self.phone,"dumpsys","trust")):raise RuntimeError("Selected device must remain unlocked")

    def invoke(self,selector,name,extra=None):
        self.ready()
        self.shell(self.phone,"am","force-stop",PACKAGE);self.shell(self.phone,"am","force-stop",PACKAGE+".test")
        try:result=self.native(self.phone,[selector],name,reset=False,extra={"nightRun":self.run_id,**(extra or {})})
        except Exception:
            if self.mode=="mvt":
                try:
                    self.private_json("verification-mvt-failure.json",name+"-failure.json")
                    (self.output/(name+"-failure.png")).write_bytes(self.private_bytes("verification-mvt-failure.png",12*2**20))
                except Exception:pass
            if self.mode=="script":
                try:
                    for filename in self.shell(self.phone,"run-as",PACKAGE,"ls","files").splitlines():
                        if re.fullmatch(r"verification-script-notice-"+self.run_id+r"-failure-[a-z0-9.-]+\.png",filename):(self.output/filename).write_bytes(self.private_bytes(filename,12*2**20))
                except Exception:pass
            raise
        if result.get("failed") or result.get("skipped"):raise NativeCheckFailure("A required night case failed or skipped")
        return result

    def private_bytes(self,relative,limit=8*2**20):
        if not re.fullmatch(r"[a-zA-Z0-9_.-]+",relative):raise ValueError("Only an exact private evidence filename is allowed")
        size=int(self.shell(self.phone,"run-as",PACKAGE,"stat","-c","%s","files/"+relative))
        if not 0<size<=limit:raise ValueError("Private evidence exceeds its bound")
        value=base64.b64decode(self.command([self.adb,"-s",self.phone,"shell","run-as",PACKAGE,"base64","files/"+relative]),validate=False)
        if len(value)!=size:raise ValueError("Incomplete private evidence transfer")
        return value

    def private_json(self,relative,name):
        raw=self.private_bytes(relative);value=json.loads(raw)
        (self.output/name).write_bytes(raw);return value

    def stage_input(self,value):
        local=self.output/"owned-input.json";local.write_text(json.dumps(value),encoding="utf-8")
        remote="/data/local/tmp/lisa-night-"+self.run_id+".json"
        self.command([self.adb,"-s",self.phone,"push",local,remote])
        if self.shell(self.phone,"sha256sum",remote).split()[0]!=hashlib.sha256(local.read_bytes()).hexdigest():raise ValueError("Input changed during transfer")
        self.shell(self.phone,"run-as",PACKAGE,"cp",remote,"files/perf01-night-input.json");self.shell(self.phone,"rm",remote)

    def phase(self,stage,*,extra=None,gateway=None,label=None):
        self.phase_index+=1;name=f"night-{self.phase_index:03}-{label or stage}"
        if gateway:
            with gateway.lock:gateway.phase=name
        try:self.invoke(SYNC,name,{"nightStage":stage,"nightRun":self.run_id,**(extra or {})})
        except Exception:
            try:self.private_json("perf01-night-result.json",name+"-failed.json")
            except Exception:pass
            raise
        value=self.private_json("perf01-night-result.json",name+".json")
        if (value.get("schema"),value.get("run"),value.get("stage"),value.get("status"),value.get("data_authority"))!=(1,self.run_id,stage,"passed","authored_synthetic"):
            raise ValueError("Stale or failed native night result")
        self.measurements.append(value);return value

    def phone_native(self,selector,name):
        expected="night-"+self.mode
        if self.args.area!=[expected]:raise RuntimeError("Night area differs from its explicit mode")
        if self.mode in ("auth","script","mvt"):
            for case in sorted(expand_selectors([selector],self.catalog)):self.invoke(case,name+"-"+case.split("#")[1])
            self.collect_ui_evidence()
            return self.results[-1]
        if self.mode=="sync":return self.sync_workflow()
        if self.mode=="map":return self.map_workflow()
        raise ValueError("Unknown night mode")

    def collect_ui_evidence(self):
        if self.mode=="mvt":
            result=self.private_json("verification-mvt-freshness.json","mvt-freshness.json")
            if result.get("status")!="passed" or result.get("run")!=self.run_id:raise ValueError("MVT evidence did not pass for this run")
            filename=result["screenshot"]
            if filename!="verification-mvt-freshness-"+self.run_id+".png":raise ValueError("Unexpected screenshot owner")
            (self.output/filename).write_bytes(self.private_bytes(filename,12*2**20))
        if self.mode=="script":
            names=self.shell(self.phone,"run-as",PACKAGE,"ls","files").splitlines()
            for filename in names:
                if re.fullmatch(r"verification-script-notice-"+self.run_id+r"-(?:portrait|landscape)-(?:dark|light)\.png",filename):
                    (self.output/filename).write_bytes(self.private_bytes(filename,12*2**20))

    def api(self,method,path,**kwargs):
        # Only independent host-side reads may retry a transient DNS/transport failure.
        # Never replay a mutation or conceal the Android client's measured request outcome.
        attempts=3 if method=="GET" else 1
        for attempt in range(attempts):
            try:
                response=requests.request(method,self.fixture["base"]+path,auth=(os.environ["LISA_NIGHT_NGW_LOGIN"],os.environ["LISA_NIGHT_NGW_PASSWORD"]),timeout=(15,120),allow_redirects=False,**kwargs)
                break
            except (requests.ConnectionError,requests.Timeout) as error:
                if attempt+1==attempts:raise
                self.checks.setdefault("host_oracle_read_retries",[]).append({"path":urlsplit(path).path,"category":type(error).__name__,"failed_attempt":attempt+1})
                time.sleep(1)
        if not response.ok:raise ValueError("Owned independent API returned HTTP "+str(response.status_code))
        return response.json() if response.content else None

    def snapshot(self):return server_rows(self.api("GET",f"/api/resource/{self.fixture['layer_id']}/feature/?srs=3857"))

    def assert_server(self,expected):
        observed=self.snapshot()
        if observed!=expected:raise ValueError("Independent full server snapshot differs from authored mutation expectations")

    def revise(self,expected,ids,revision):
        for identity in ids:expected[identity]["fields"].update(revision=revision,test_label=f"Night {revision} / {identity}",nullable_text=None if identity%2==0 else "")

    def sync_workflow(self):
        if not self.fixture:raise ValueError("An exact owned synthetic WebGIS manifest is required")
        gateway=OwnedGateway(self.fixture,self.output/"http",allow_writes=True)
        ownership=gateway.verify_remote_ownership();(self.output/"remote-ownership.json").write_text(json.dumps(ownership,indent=2),encoding="utf-8")
        expected=self.snapshot()
        if len(expected)!=1000 or any(not row["fields"]["fixture_key"].startswith("night-") or row["fields"]["revision"]!=0 for row in expected.values()):
            raise ValueError("Use the untouched 1000-row authored fixture; inspect/recover a previous run first")
        initial=copy.deepcopy(expected);ids=sorted(expected)
        (self.output/"initial-server-oracle.json").write_text(json.dumps(initial),encoding="utf-8")
        server=ThreadingHTTPServer(("127.0.0.1",0),gateway.handler());server.daemon_threads=True
        thread=threading.Thread(target=server.serve_forever,daemon=True);thread.start();port=server.server_port;reverse=f"tcp:{port}"
        self.command([self.adb,"-s",self.phone,"reverse",reverse,reverse]);seeded=False
        self.stage_input(dict(run=self.run_id,data_authority="authored_synthetic",endpoint=f"http://127.0.0.1:{port}",resource_id=self.fixture["layer_id"],initial_count=1000))
        try:
            seed=self.phase("seed",gateway=gateway);seeded=True;compare_native(seed["content"],expected)
            for count in (1,10,100):
                timings=[]
                for index in range(self.samples):
                    revision=10000+count*100+index
                    result=self.phase("patch",gateway=gateway,label=f"patch-{count}-{index}",extra={"nightCount":count,"nightRevision":revision})
                    if result["changed_ids"]!=ids[:count]:raise ValueError("Changed IDs differ from independent selection")
                    self.revise(expected,ids[:count],revision);compare_native(result["content"],expected);self.assert_server(expected);timings.append(result["operation_upper_ms"])
                self.checks[f"outgoing_{count}"]=distribution(timings)
                timings=[]
                for index in range(self.samples):
                    revision=50000+count*100+index
                    self.revise(expected,ids[:count],revision)
                    self.api("PATCH",f"/api/resource/{self.fixture['layer_id']}/feature/",json=[{"id":identity,"fields":expected[identity]["fields"]} for identity in ids[:count]])
                    result=self.phase("pull",gateway=gateway,label=f"pull-{count}-{index}")
                    compare_native(result["content"],expected);self.assert_server(expected);timings.append(result["operation_upper_ms"])
                self.checks[f"incoming_{count}"]=distribution(timings)
                self.phase("full-compare",gateway=gateway,label=f"full-compare-{count}")
            held=self.phase("hold",gateway=gateway,extra={"nightCount":1,"nightRevision":77777})
            local=copy.deepcopy(expected);self.revise(local,ids[:1],77777);compare_native(held["content"],local);self.assert_server(expected)
            self.revise(expected,ids[1:2],88888);local[ids[1]]=copy.deepcopy(expected[ids[1]])
            self.api("PUT",f"/api/resource/{self.fixture['layer_id']}/feature/{ids[1]}",json={"fields":expected[ids[1]]["fields"]})
            pulled=self.phase("pull",gateway=gateway,label="pull-preserves-local");compare_native(pulled["content"],local)
            if pulled["pending"]!=1:raise ValueError("Pull lost the unsent local change")
            sent=self.phase("send",gateway=gateway,label="send-held-change");expected=local;compare_native(sent["content"],expected);self.assert_server(expected)
            with gateway.lock:gateway.fault={"mode":"before_forward","method":"PUT","until_clear":True}
            failed=self.phase("patch-fail",gateway=gateway,extra={"nightCount":1,"nightRevision":99999});self.assert_server(expected)
            self.revise(expected,ids[:1],99999);compare_native(failed["content"],expected)
            with gateway.lock:gateway.fault=None
            retried=self.phase("send",gateway=gateway,label="retry-after-disconnect");compare_native(retried["content"],expected);self.assert_server(expected)
            created=self.phase("create",gateway=gateway);observed=self.snapshot();added=set(observed)-set(expected)
            if len(added)!=1:raise ValueError("Creation did not produce exactly one new FID")
            created_id=added.pop();row=observed[created_id]
            wanted={"fields":dict(fixture_key=created["fixture_key"],revision=1,test_label="Night client",nullable_text=None),"coordinates":polygon_coordinates("POLYGON ((3339000 8400000,3339100 8400000,3339100 8400100,3339000 8400100,3339000 8400000))")}
            if row!=wanted:raise ValueError("Created server content differs from authored input")
            expected[created_id]=wanted;compare_native(created["content"],expected);self.assert_server(expected)
            photo=self.phase("photo",gateway=gateway);self.assert_server(expected)
            path=f"/api/resource/{self.fixture['layer_id']}/feature/{photo['photo_feature_id']}/attachment/"
            attachments=self.api("GET",path);matches=[a for a in attachments if a.get("name")=="night-"+self.run_id+".png"]
            if len(matches)!=1:raise ValueError("Synthetic photo metadata did not arrive")
            response=requests.get(self.fixture["base"]+path+str(matches[0]["id"])+"/download",auth=gateway.auth,timeout=(15,60),allow_redirects=False)
            if response.status_code!=200 or len(response.content)!=photo["photo_bytes"] or hashlib.sha256(response.content).hexdigest()!=photo["photo_sha256"]:
                raise ValueError("Photo readback differs")
            deleted=self.phase("delete",gateway=gateway,extra={"nightFeatureId":created_id});expected.pop(created_id);compare_native(deleted["content"],expected);self.assert_server(expected)
            self.phase("full-compare",gateway=gateway,label="final-full-compare")
            with gateway.lock:gateway.fault={"mode":"after_commit","method":"POST","path":f"/api/resource/{gateway.layer}/feature/"}
            before_calls=len(gateway.calls);ambiguous=self.phase("ambiguous-create",gateway=gateway);observed=self.snapshot()
            additions=set(observed)-set(expected)
            if len(additions)!=1 or observed[next(iter(additions))]["fields"]["fixture_key"]!=ambiguous["fixture_key"]:raise ValueError("Lost response created zero or duplicate objects")
            if sum(row["method"]=="POST" and row["path"]==f"/api/resource/{gateway.layer}/feature/" for row in gateway.calls[before_calls:])!=1:raise ValueError("Creation was replayed after response loss")
            before_calls=len(gateway.calls);self.phase("retry-ambiguous",gateway=gateway)
            if any(row["method"]=="POST" for row in gateway.calls[before_calls:]):raise ValueError("Ambiguous creation was automatically repeated")
            if self.snapshot()!=observed:raise ValueError("Retry changed the independently checked server snapshot")
            self.checks["synthetic_write_matrix"]={"status":"passed","initial_features":len(initial),"samples_per_timing_cell":self.samples,"local_edit_preserved":True,"photo_readback_equal":True,"full_reimport_equal":True,"ambiguous_creation":"one remote object; one local operation retained for review; no automatic POST replay"}
            self.restore_server_baseline(initial)
        finally:
            try:
                if seeded:self.phase("cleanup",gateway=gateway)
            finally:
                try:self.command([self.adb,"-s",self.phone,"reverse","--remove",reverse],allow_failure=False)
                finally:
                    server.shutdown();server.server_close();thread.join(10)
                    (self.output/"http-summary.json").write_text(json.dumps(gateway.summary(),indent=2),encoding="utf-8")
        return self.results[-1]

    def restore_server_baseline(self,initial):
        current=self.snapshot();owner="night-client-"+self.run_id+"-";prefix=f"/api/resource/{self.fixture['layer_id']}/feature/"
        restore=[]
        for identity,row in current.items():
            if identity not in initial:
                if not row["fields"]["fixture_key"].startswith(owner):raise ValueError("Unknown new data must be retained")
                self.api("DELETE",prefix+str(identity))
            else:
                if row["coordinates"]!=initial[identity]["coordinates"]:raise ValueError("Unexpected geometry change retained for review")
                if row["fields"]!=initial[identity]["fields"]:restore.append({"id":identity,"fields":initial[identity]["fields"]})
                attachments=self.api("GET",prefix+str(identity)+"/attachment/") if identity==min(initial) else []
                for attachment in attachments:
                    if attachment.get("name")=="night-"+self.run_id+".png":self.api("DELETE",prefix+str(identity)+"/attachment/"+str(attachment["id"]))
        if restore:self.api("PATCH",prefix,json=restore)
        self.assert_server(initial);self.checks["server_baseline_restored"]=True

    def map_workflow(self):
        self.stage_input(dict(run=self.run_id,data_authority="authored_synthetic",endpoint="http://127.0.0.1:9999",resource_id=0,initial_count=0))
        initialized=False;profiles=[]
        try:
            self.phase("map-init");initialized=True
            for side in self.sides:
                common={"perfRun":self.run_id,"perfExtendedSide":side,"perfPhysicalOptIn":"true","perfTileProfile":"true","perfRevision":self.fingerprint,"perfClearDerivedCache":"false"}
                self.invoke(MAP,f"map-{side}-seed",{**common,"perfStage":"seed"})
                timings=[]
                for index in range(self.samples):
                    self.invoke(MAP,f"map-{side}-fresh-{index}",{**common,"perfStage":"sample","perfMode":"fresh","perfSample":index})
                    value=self.private_json("perf01-samples.json",f"map-{side}-fresh-{index}.json")
                    self.accept_map(value,side,"fresh",1);timings.append(value["samples"][0]["validated_ready_upper_ms"]);profiles.append(self.profile(value,f"map-{side}-fresh-{index}"))
                self.checks[f"map_{side}_fresh"]=distribution(timings)
                self.invoke(MAP,f"map-{side}-warm",{**common,"perfStage":"sample","perfMode":"warm","perfSamples":self.samples,"perfCleanup":"true","perfScreenshot":"true"})
                value=self.private_json("perf01-samples.json",f"map-{side}-warm.json");self.accept_map(value,side,"warm",self.samples)
                self.checks[f"map_{side}_warm"]=distribution([s["validated_ready_upper_ms"] for s in value["samples"]]);profiles.append(self.profile(value,f"map-{side}-warm"))
                (self.output/f"map-{side}.png").write_bytes(self.private_bytes("perf01-scene.png",12*2**20))
            self.checks["tile_profile_summaries"]=profiles
            if self.soak_minutes:
                import time
                side=self.sides[-1];common={"perfRun":self.run_id,"perfExtendedSide":side,"perfPhysicalOptIn":"true","perfTileProfile":"true","perfRevision":self.fingerprint,"perfClearDerivedCache":"false"}
                self.invoke(MAP,"soak-seed",{**common,"perfStage":"seed"});began=time.monotonic();batch=0
                while time.monotonic()-began<self.soak_minutes*60:
                    batch+=1;name=f"soak-{batch:03}"
                    self.invoke(MAP,name,{**common,"perfStage":"sample","perfMode":"warm","perfSamples":20,"perfPostFrameIdleMs":20000})
                    value=self.private_json("perf01-samples.json",name+".json");self.accept_map(value,side,"warm",20);self.profile(value,name)
                    print(f"SOAK batch={batch} validated_returns={batch*20} elapsed_minutes={(time.monotonic()-began)/60:.1f}",flush=True)
                self.checks["soak"]={"elapsed_minutes":(time.monotonic()-began)/60,"batches":batch,"validated_returns":batch*20,"process_lifetime":"One independent process per bounded batch; same live providers within each batch","memory":"100ms RSS sampler and per-return/post-idle Java/native/PSS/RSS observations; no pressure budget or battery acceptance"}
        finally:
            if initialized:self.phase("map-cleanup")
        return self.results[-1]

    def accept_map(self,value,side,mode,count):
        fixture=value["fixture"]
        if (value.get("status"),fixture.get("run"),fixture.get("side"),fixture.get("features"),value.get("mode"),value.get("input_hashes_verified_after_measurement"))!=("passed",self.run_id,side,side*side+80,mode,True):
            raise ValueError("Failed or differently owned map fixture")
        samples=value["samples"]
        if len(samples)!=count:raise ValueError("Missing map observations")
        if mode=="warm" and ({s["index"] for s in samples}!=set(range(count)) or any(s.get("same_live_providers") is not True for s in samples)):
            raise ValueError("Warm providers or observation indices differ")

    def profile(self,value,name):
        filename=value["tile_profile_file"]
        if not re.fullmatch(r"perf01-tile-profile-perf01-[0-9a-f-]{36}\.json",filename):raise ValueError("Unknown profiler evidence owner")
        profile=self.private_json(filename,name+"-tiles.json")
        if profile["session"]!=value["tile_profile_session"]:raise ValueError("Profiler session differs")
        result=tile_summary(profile["records"],profile["summary"])
        (self.output/(name+"-tiles-summary.json")).write_text(json.dumps(result,indent=2),encoding="utf-8");return result


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--phone-serial",required=True);parser.add_argument("--output",type=Path,required=True)
    parser.add_argument("--mode",choices=("auth","script","mvt","sync","map"),required=True)
    parser.add_argument("--fixture",type=Path);parser.add_argument("--samples",type=int,choices=(1,20),default=1)
    parser.add_argument("--sides",type=int,nargs="+",choices=(100,224),default=[100,224]);parser.add_argument("--skip-build",action="store_true")
    parser.add_argument("--soak-minutes",type=int,default=0)
    options=parser.parse_args();output=options.output.resolve()
    if output.exists() or os.name=="nt" and output.drive.upper()!="Q:":parser.error("Use a new owned output directory on Q")
    if not 0<=options.soak_minutes<=240 or options.soak_minutes and options.mode!="map":parser.error("Map soak must be between0 and240 minutes")
    fixture=json.loads(options.fixture.read_text(encoding="utf-8-sig")) if options.fixture else None
    args=argparse.Namespace(scope="related",area=["night-"+options.mode],phone_serial=options.phone_serial,emulator_serial=None,
        phone_only=True,allow_emulator=False,emulator_graphics="native",emulator_runtime=None,avd=None,test_project=None,
        test_project_fixture=None,sdk=os.environ.get("ANDROID_SDK_ROOT") or os.environ.get("ANDROID_HOME"),output=str(output),skip_build=options.skip_build)
    NightVerification(args,options.mode,fixture=fixture,samples=options.samples,sides=options.sides,soak_minutes=options.soak_minutes).run()
    return 0


if __name__=="__main__":raise SystemExit(main())
