"""Bounded, exact-package Android native allocation profiling. No install or publication.

Capture from startup with --start-app, or attach to the already running app (then
pre-existing allocations are intentionally outside the profile). Only the owned
Perfetto PID and two fresh remote files are stopped/removed. A real recorder is
never stopped. Profiling uses metadata/callstacks, not raw heap or business data.
"""
from __future__ import annotations
import argparse
import csv
from datetime import datetime, timezone
import io
import json
from pathlib import Path
import re
import subprocess
import time
import uuid

from prepare_native_heap_apk import plain_directory, sha256
from profile_mobile_memory import PACKAGE, parse_memory

PROCESSOR_SHA256 = 'adfa6bad3d72be3ba9b83fa2b17b69fa13b3ab1cad0f42e52b86188bd5f0f997'
PROCESSOR = Path(r'Q:\Codex\Cache\perfetto\v58.2\windows-amd64\trace_processor_shell.exe')
SESSION = r'lisa-heap-[0-9a-f]{16}'


def remote_paths(name: str) -> tuple[str,str]:
    if not re.fullmatch(SESSION,name): raise ValueError('Invalid owned session name')
    return '/data/misc/perfetto-configs/'+name+'.pbtxt','/data/misc/perfetto-traces/'+name+'.pftrace'


def validate_remote_pair(config: str, trace: str, *, allow_legacy: bool=False) -> str:
    match=re.fullmatch(r'/data/misc/perfetto-configs/('+SESSION+r')\.pbtxt',config)
    if match and (config,trace)==remote_paths(match.group(1)): return match.group(1)
    if allow_legacy:
        match=re.fullmatch(r'/data/local/tmp/('+SESSION+r')\.pbtxt',config)
        if match and trace=='/data/local/tmp/'+match.group(1)+'.pftrace': return match.group(1)
    raise ValueError('Unknown or mismatched owned remote path pair')


def configuration(duration: int, name: str) -> str:
    if not 20 <= duration <= 300 or not re.fullmatch(r'lisa-heap-[0-9a-f]{16}', name):
        raise ValueError('Invalid bounded trace configuration')
    return f'''unique_session_name: "{name}"
duration_ms: {duration * 1000}
write_into_file: true
file_write_period_ms: 5000
flush_period_ms: 5000
max_file_size_bytes: 67108864
buffers {{ size_kb: 32768 fill_policy: RING_BUFFER }}
data_sources {{ config {{ name: "android.heapprofd" target_buffer: 0
  heapprofd_config {{
    process_cmdline: "{PACKAGE}"
    sampling_interval_bytes: 1048576
    shmem_size_bytes: 67108864
    block_client: false
    continuous_dump_config {{ dump_interval_ms: 10000 }}
  }}
}} }}
'''


def parse_perfetto_pid(text: str) -> int:
    pids = re.findall(r'^\s*([1-9][0-9]*)\s*$', text, re.M)
    if len(pids) != 1: raise ValueError('Perfetto did not return exactly one owned PID')
    return int(pids[0])


def owns_perfetto(commandline: str, config: str, trace: str) -> bool:
    fields = commandline.rstrip('\x00\r\n').split('\x00')
    return bool(fields and fields[0].rsplit('/', 1)[-1] == 'perfetto' and config in fields and trace in fields)


def assert_no_recorder(services: str) -> None:
    for line in services.splitlines():
        if 'ServiceRecord{' in line and re.search(r'(?:TrackerService|WalkEditService)\b', line):
            raise ValueError('Real Track/Walk recording is running; cold profiling cannot stop the application')


class Adb:
    def __init__(self, binary: Path, serial: str): self.binary, self.serial = binary, serial
    def invoke(self, *words: str, timeout: int = 30, check: bool = True):
        result = subprocess.run([str(self.binary), '-s', self.serial, *map(str, words)], capture_output=True, timeout=timeout)
        if check and result.returncode: raise AdbFailure(words, result)
        return result
    def shell(self, *words: str, timeout: int = 30) -> str:
        return self.invoke('shell', *words, timeout=timeout).stdout.decode('utf-8', 'replace').strip()
    def attempt(self, *words: str, timeout: int = 10): return self.invoke('shell', *words, timeout=timeout, check=False)


class AdbFailure(RuntimeError):
    def __init__(self, command, result):
        # This tool has no signing/credential commands. Preserve bounded diagnostic
        # output instead of turning a concrete Android/Perfetto error into "shell".
        self.details = {'command':list(map(str,command)), 'exit_code':result.returncode,
                        'stdout_tail':result.stdout.decode('utf-8','replace')[-4096:],
                        'stderr_tail':result.stderr.decode('utf-8','replace')[-4096:]}
        detail=self.details['stderr_tail'].strip() or self.details['stdout_tail'].strip()
        super().__init__('ADB '+ ' '.join(self.details['command'])+' failed (exit '+str(result.returncode)+')'+ ('\n'+detail if detail else ''))


def process_pid(adb: Adb, required: bool = True) -> int | None:
    result = adb.attempt('pidof', PACKAGE)
    value = result.stdout.decode().strip()
    if not value and not required: return None
    if result.returncode or not re.fullmatch('[1-9][0-9]*', value): raise RuntimeError('Expected one target application process')
    return int(value)


def foreground(adb: Adb) -> None:
    lines = adb.shell('dumpsys', 'activity', 'activities').splitlines()
    focused = [line for line in lines if 'topResumedActivity=' in line or 'mResumedActivity=' in line]
    if not any(PACKAGE+'/' in line for line in focused): raise RuntimeError('Target application is no longer foreground')


def remote_absent(result) -> bool:
    # ADB transport failures also use exit1. They are not a successful remote test.
    return result.returncode == 1 and not getattr(result, 'stderr', b'').strip()


def stop_owned(adb: Adb, pid: int, config: str, trace: str, *, wait_seconds: float = 15) -> str:
    state = adb.attempt('cat', '/proc/'+str(pid)+'/cmdline')
    if state.returncode:
        present = adb.attempt('test', '-d', '/proc/'+str(pid))
        if remote_absent(present): return 'already-exited'
        raise RuntimeError('Owned Perfetto PID exists but ownership cannot be verified; not signalling it')
    if not owns_perfetto(state.stdout.decode('utf-8', 'replace'), config, trace):
        raise RuntimeError('Perfetto PID identity changed; not signalling an unrelated process')
    try:
        adb.shell('kill', '-TERM', str(pid))
    except AdbFailure:
        # A duration-bounded Perfetto can finish between ownership verification
        # and TERM. Accept that race only after a fresh remote absence test.
        if remote_absent(adb.attempt('test', '-d', '/proc/'+str(pid))):
            return 'already-exited-before-signal'
        raise
    until = time.monotonic()+wait_seconds
    while time.monotonic() < until:
        present = adb.attempt('test', '-d', '/proc/'+str(pid), timeout=5)
        if remote_absent(present): return 'terminated-and-exited'
        if present.returncode not in (0, 1): break
        time.sleep(.25)
    raise RuntimeError('Owned Perfetto PID has not exited; retain remote files')


def remove_owned(adb: Adb, paths: list[str], *, allow_legacy: bool=False) -> None:
    if len(paths)!=2: raise ValueError('Unknown remote cleanup target')
    validate_remote_pair(*paths,allow_legacy=allow_legacy)
    adb.shell('rm', '-f', '--', *paths)
    for path in paths:
        if not remote_absent(adb.attempt('test', '-e', path)): raise RuntimeError('Remote cleanup absence was not verified')


def find_owned_profilers(adb: Adb, config: str, trace: str) -> list[int]:
    result=adb.attempt('pidof','perfetto')
    if remote_absent(result): return []
    if result.returncode: raise AdbFailure(('shell','pidof','perfetto'),result)
    values=result.stdout.decode().split()
    if not values or any(not re.fullmatch('[1-9][0-9]*',value) for value in values):
        raise RuntimeError('Cannot enumerate candidate Perfetto PIDs safely')
    found=[]
    for value in values:
        process=adb.attempt('cat','/proc/'+value+'/cmdline')
        if process.returncode:
            if remote_absent(adb.attempt('test','-d','/proc/'+value)): continue
            raise RuntimeError('Candidate Perfetto ownership is unreadable; retain evidence')
        if owns_perfetto(process.stdout.decode('utf-8','replace'),config,trace): found.append(int(value))
    return found


def recover(args) -> dict:
    source=args.recover.resolve(strict=True)
    original=json.loads(source.read_text(encoding='utf-8'))
    directory=plain_directory(source.parent)
    name=original.get('session_name','')
    config,trace=original.get('remote_config',''),original.get('remote_trace','')
    if (validate_remote_pair(config,trace,allow_legacy=True)!=name or original.get('package')!=PACKAGE
            or original.get('serial')!=args.serial):
        raise ValueError('Recovery requires the exact owned capture receipt/device/paths')
    local_config=directory/'config.pbtxt'
    if not local_config.is_file(): raise ValueError('Original owned config evidence is missing')
    token='recovery-'+uuid.uuid4().hex[:12]
    destination=directory/(token+'.json')
    report={'schema_version':1,'status':'active','source_receipt':str(source),'source_receipt_sha256':sha256(source),
            'created_utc':datetime.now(timezone.utc).isoformat(),'serial':args.serial,'session_name':name,
            'remote_config':config,'remote_trace':trace,'cleanup':'pending',
            'purpose':'Recover own failed profiler resources; never certifies a native capture'}
    adb=Adb(args.adb,args.serial)
    try:
        present=adb.attempt('test','-e',config)
        if present.returncode==0:
            if adb.shell('sha256sum',config).split()[0]!=sha256(local_config):
                raise RuntimeError('Remote config differs from owned evidence; do not remove it')
        elif not remote_absent(present): raise RuntimeError('Cannot verify owned remote config state')
        owners=find_owned_profilers(adb,config,trace);report['owned_pids_found']=owners
        report['stopped']={str(pid):stop_owned(adb,pid,config,trace) for pid in owners}
        if find_owned_profilers(adb,config,trace): raise RuntimeError('Owned profiler remains active; retain remote files')
        present=adb.attempt('test','-e',trace)
        if present.returncode==0:
            first=int(adb.shell('stat','-c','%s',trace));time.sleep(.5)
            second=int(adb.shell('stat','-c','%s',trace))
            if first!=second: raise RuntimeError('Owned trace is still changing; retain it')
            digest=adb.shell('sha256sum',trace).split()[0]
            copy=directory/(token+'.pftrace');adb.invoke('pull',trace,str(copy),timeout=60)
            if copy.stat().st_size!=second or sha256(copy)!=digest: raise RuntimeError('Recovery trace copy/hash is incomplete')
            report.update(trace_bytes=second,trace_sha256=digest,retained_trace=str(copy),empty_trace=second==0)
        elif not remote_absent(present): raise RuntimeError('Cannot verify owned remote trace state')
        remove_owned(adb,[config,trace],allow_legacy=True)
        report.update(status='recovered-cleanup',cleanup='remote-files-removed-and-absence-verified')
    except Exception as error:
        report.update(status='failed',error_type=type(error).__name__,error=str(error))
        if isinstance(error,AdbFailure):report['command_failure']=error.details
        raise
    finally:
        destination.write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    return report


def analysis_sql(pid: int) -> str:
    if type(pid) is not int or pid < 1: raise ValueError('Invalid profile target PID')
    return f'''WITH RECURSIVE
target AS (SELECT upid FROM process WHERE pid={pid}),
a AS (SELECT h.* FROM heap_profile_allocation h JOIN target USING(upid)),
top AS (SELECT callsite_id, SUM(size) AS retained FROM a GROUP BY callsite_id HAVING retained>0 ORDER BY retained DESC LIMIT 150),
stacks(callsite_id,node_id,level,frame_id,parent_id) AS (
  SELECT t.callsite_id,c.id,0,c.frame_id,c.parent_id FROM top t JOIN stack_profile_callsite c ON c.id=t.callsite_id
  UNION ALL
  SELECT s.callsite_id,c.id,s.level+1,c.frame_id,c.parent_id FROM stacks s JOIN stack_profile_callsite c ON c.id=s.parent_id WHERE s.level<64
),
timeline AS (SELECT ts,SUM(size) AS delta,SUM(SUM(size)) OVER(ORDER BY ts) AS retained FROM a GROUP BY ts),
records(section,payload) AS (
SELECT 'summary' AS section,json_object('pid',{pid},'rows',COUNT(*),'process_count',COUNT(DISTINCT upid),
  'net_bytes',COALESCE(SUM(size),0),'allocated_bytes',COALESCE(SUM(CASE WHEN size>0 THEN size ELSE 0 END),0),
  'freed_bytes',COALESCE(SUM(CASE WHEN size<0 THEN -size ELSE 0 END),0),'first_ts',MIN(ts),'last_ts',MAX(ts)) AS payload FROM a
UNION ALL SELECT 'heap',json_object('heap_name',heap_name,'rows',COUNT(*),'net_bytes',SUM(size)) FROM a GROUP BY heap_name
UNION ALL SELECT 'callsite',json_object('callsite_id',callsite_id,'net_bytes',SUM(size),
  'allocated_bytes',SUM(CASE WHEN size>0 THEN size ELSE 0 END),'freed_bytes',SUM(CASE WHEN size<0 THEN -size ELSE 0 END)) FROM a GROUP BY callsite_id
UNION ALL SELECT 'frame',json_object('callsite_id',s.callsite_id,'level',s.level,'frame_id',f.id,'name',f.name,
  'mapping',m.name,'build_id',m.build_id,'rel_pc',f.rel_pc) FROM stacks s LEFT JOIN stack_profile_frame f ON f.id=s.frame_id LEFT JOIN stack_profile_mapping m ON m.id=f.mapping
UNION ALL SELECT 'timeline',json_object('ts',ts,'delta_bytes',delta,'net_bytes',retained) FROM timeline
UNION ALL SELECT 'stat',json_object('name',name,'idx',idx,'value',value,'severity',severity,'source',source) FROM stats
  WHERE name GLOB '*heapprofd*' OR severity IN ('error','data_loss') OR name GLOB '*buffer*'
)
SELECT section,HEX(payload) AS payload_hex FROM records;
'''


def parse_analysis(text: str) -> dict:
    sections = {name: [] for name in ('summary', 'heap', 'callsite', 'frame', 'timeline', 'stat')}
    for row in csv.DictReader(io.StringIO(text)):
        if row.get('section') not in sections or 'payload_hex' not in row: raise ValueError('Unexpected trace analysis schema')
        # Perfetto's CSV printer does not consistently escape nested JSON quotes.
        sections[row['section']].append(json.loads(bytes.fromhex(row['payload_hex']).decode('utf-8')))
    summaries = sections['summary']
    if len(summaries) != 1 or summaries[0].get('rows', 0) < 1 or summaries[0].get('allocated_bytes', 0) < 1:
        raise ValueError('No target heap allocation evidence; profiling may not have attached')
    if summaries[0].get('process_count') != 1 or summaries[0].get('net_bytes', -1) < 0:
        raise ValueError('Ambiguous process or invalid net allocation accounting')
    bad = []
    for stat in sections['stat']:
        if stat.get('value', 0) <= 0: continue
        name = stat.get('name', '').lower()
        if stat.get('severity') in ('error', 'data_loss') or any(word in name for word in
                ('corrupt', 'overrun', 'overran', 'overwritten', 'discard', 'invalid', 'malformed', 'missing', 'drop', 'timeout', 'failed', 'rejected')):
            bad.append(stat)
    sections['quality_errors'] = bad
    sections['quality'] = 'complete' if not bad else 'incomplete'
    return sections


def allocation_coverage(report: dict, expected_seconds: int) -> dict:
    if type(expected_seconds) is not int or not 20 <= expected_seconds <= 300:
        raise ValueError('Invalid expected profiling duration')
    timestamps = sorted({row['ts'] for row in report['timeline']})
    span = (timestamps[-1]-timestamps[0])/1e9 if timestamps else 0
    # Continuous dumps are ten seconds apart. Allow at most twenty seconds
    # for cold launch/attachment and one final interval; never certify one dump.
    required = max(10, expected_seconds-30)
    return {'status':'complete' if len(timestamps)>=2 and span>=required else 'incomplete',
            'distinct_dumps':len(timestamps),'observed_span_s':span,'required_span_s':required,
            'requested_s':expected_seconds,'startup_tolerance_s':20,'final_dump_tolerance_s':10}


def analyze(processor: Path, trace: Path, output: Path, pid: int, *, expected_seconds: int | None = None) -> dict:
    sql = output/'analysis.sql'; sql.write_text(analysis_sql(pid), encoding='utf-8')
    result = subprocess.run([str(processor), 'query', '-f', str(sql), str(trace)], capture_output=True, timeout=120)
    (output/'analysis.csv').write_bytes(result.stdout)
    (output/'analysis-tool.log').write_bytes(result.stderr)
    if result.returncode: raise RuntimeError('Trace Processor query failed; retained analysis-tool.log')
    report = parse_analysis(result.stdout.decode('utf-8-sig'))
    if expected_seconds is not None:
        report['coverage']=allocation_coverage(report,expected_seconds)
        if report['coverage']['status']!='complete': report['quality']='incomplete'
    (output/'analysis.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
    if report['quality_errors']: raise RuntimeError('Profiler reported errors/data loss; partial analysis retained')
    if report.get('coverage',{}).get('status')=='incomplete':
        raise RuntimeError('Allocation dumps do not cover the observation interval; partial analysis retained')
    return report


def verify_installed(adb: Adb, receipt_path: Path) -> dict:
    receipt = json.loads(receipt_path.read_text(encoding='utf-8'))
    if (receipt.get('status') != 'verified' or receipt.get('cleanup') != 'removed-and-absence-verified'
            or receipt.get('package_identity', {}).get('package') != PACKAGE):
        raise ValueError('Expected verified exact-baseline profiling APK provenance')
    expected = receipt.get('output_sha256', '')
    if not re.fullmatch('[0-9a-f]{64}', expected): raise ValueError('Profiling APK receipt has no exact hash')
    paths = adb.shell('pm', 'path', PACKAGE).splitlines()
    if len(paths) != 1 or not paths[0].startswith('package:'): raise ValueError('Expected one installed baseline APK')
    path = paths[0][8:]
    if not re.fullmatch(r'/data/app/[A-Za-z0-9_+=.@~/-]+\.apk', path): raise ValueError('Unexpected installed APK path')
    value = adb.shell('sha256sum', path).split()[0]
    if value != expected: raise ValueError('Installed APK differs from the approved profileable baseline')
    return {'sha256': value, 'package_identity': receipt['package_identity'],
            'signer_certificate_sha256': receipt.get('signer_certificate_sha256')}


def capture(args) -> dict:
    output = plain_directory(args.output)
    if output.exists(): raise ValueError('Use a new evidence directory; do not overwrite a capture')
    if not args.trace_processor.is_file() or sha256(args.trace_processor) != PROCESSOR_SHA256:
        raise ValueError('Expected verified official Windows Trace Processor58.2')
    if not re.fullmatch('[A-Za-z0-9_.:-]+', args.serial): raise ValueError('Invalid explicit device serial')
    output.mkdir(parents=True)
    name = 'lisa-heap-'+uuid.uuid4().hex[:16]
    remote_config, remote_trace = remote_paths(name)
    config = output/'config.pbtxt'; config.write_text(configuration(args.duration, name), encoding='utf-8')
    receipt = {'schema_version': 1, 'status': 'active', 'created_utc': datetime.now(timezone.utc).isoformat(),
               'package': PACKAGE, 'serial': args.serial, 'project_id': args.project, 'requested_seconds': args.duration,
               'coverage': 'from-process-start' if args.start_app else 'late-attach-new-allocations-only',
               'session_name': name, 'remote_config': remote_config, 'remote_trace': remote_trace, 'cleanup': 'pending',
               'capture_tool_sha256': sha256(Path(__file__)), 'processor_sha256': PROCESSOR_SHA256}
    def save():
        temporary = output/'receipt.tmp'
        temporary.write_text(json.dumps(receipt, ensure_ascii=False, indent=2), encoding='utf-8')
        temporary.replace(output/'receipt.json')
    save()
    adb = Adb(args.adb, args.serial)
    perfetto_pid = None; pushed = False; start_attempted = False; stopped = False; pulled = False; app_pid = None; failure = None
    stage = 'device-and-apk-preflight'
    try:
        if adb.shell('getprop', 'ro.hardware') in ('ranchu', 'goldfish'): raise ValueError('Physical device required')
        if int(adb.shell('getprop', 'ro.build.version.sdk')) < 29: raise ValueError('Native profiling requires Android10 or newer')
        receipt['installed_apk'] = verify_installed(adb, args.apk_receipt)
        # Only the specifically authorized project's small identity metadata; no feature/account files.
        root = '/sdcard/Android/data/'+PACKAGE+'/files/map/collector_projects'
        selected = [x for x in adb.shell('ls', root).splitlines() if re.fullmatch(r'collector_'+str(args.project)+r'_[A-Za-z0-9_-]+', x)]
        if len(selected) != 1: raise ValueError('Requested project directory is missing or ambiguous')
        metadata = json.loads(adb.shell('cat', root+'/'+selected[0]+'/project.json'))
        if metadata.get('project_remote_id') != args.project: raise ValueError('Project metadata identity mismatch')
        receipt['project_evidence'] = 'Explicit project exists; active selection supplied by operator, not inferred from coordinates'
        old_pid = process_pid(adb, required=not args.start_app); receipt['previous_app_pid'] = old_pid
        if args.start_app:
            assert_no_recorder(adb.shell('dumpsys', 'activity', 'services', PACKAGE))
            stage = 'stop-approved-idle-application'
            adb.shell('am', 'force-stop', PACKAGE)
        else: foreground(adb)
        for path in (remote_config, remote_trace):
            if not remote_absent(adb.attempt('test', '-e', path)): raise ValueError('Fresh remote evidence path is not absent')
        pushed = True  # Own the fresh reserved path even if a transfer fails part-way.
        stage = 'push-owned-config'
        adb.invoke('push', str(config), remote_config)
        began = time.monotonic()
        start_attempted = True
        stage = 'start-acknowledged-profiler'
        result = adb.shell('perfetto', '--background-wait', '--txt', '-c', remote_config, '-o', remote_trace, timeout=45)
        perfetto_pid = parse_perfetto_pid(result); receipt['perfetto_pid'] = perfetto_pid; save()
        stage = 'launch-and-bind-target-process'
        if args.start_app: adb.shell('am', 'start', '-W', '-n', PACKAGE+'/com.nextgis.mobile.activity.MainActivity')
        app_pid = process_pid(adb); receipt['app_pid'] = app_pid
        foreground(adb); save()
        print('CAPTURING '+json.dumps({'pid': app_pid, 'perfetto_pid': perfetto_pid, 'seconds':args.duration, 'output':str(output)}), flush=True)
        stage = 'capture-target-allocations'
        while time.monotonic()-began < args.duration:
            foreground(adb)
            if process_pid(adb) != app_pid: raise RuntimeError('Application PID changed/exited; retain partial allocation evidence')
            text = adb.shell('dumpsys', 'meminfo', '--local', str(app_pid))
            sample = {'elapsed_s': round(time.monotonic()-began, 3), **parse_memory(text)}
            with (output/'memory.jsonl').open('a', encoding='utf-8') as stream: stream.write(json.dumps(sample)+'\n')
            print(json.dumps(sample), flush=True)
            if sample.get('total_rss_kib', 0) >= args.ceiling_mib*1024: raise RuntimeError('Profiling RSS ceiling reached')
            time.sleep(min(5, max(.1, args.duration-(time.monotonic()-began))))
        receipt['capture_elapsed_s'] = round(time.monotonic()-began, 3)
    except BaseException as error:
        failure = error; receipt.update(status='failed', failed_stage=stage, error_type=type(error).__name__, error=str(error))
        if isinstance(error,AdbFailure): receipt['command_failure']=error.details
    finally:
        if perfetto_pid is not None:
            try:
                receipt['perfetto_stop'] = stop_owned(adb, perfetto_pid, remote_config, remote_trace); stopped = True
            except Exception as error: receipt['perfetto_stop_error'] = str(error)
        elif start_attempted:
            # Unknown start failure: never guess a PID or signal somebody else's capture.
            receipt['perfetto_stop_error'] = 'No owned PID returned; bounded config remains a safety limit'
        if stopped:
            try:
                trace = output/'native-heap.pftrace'
                remote_hash = adb.shell('sha256sum', remote_trace).split()[0]
                adb.invoke('pull', remote_trace, str(trace), timeout=60)
                if not trace.is_file() or trace.stat().st_size == 0 or sha256(trace) != remote_hash:
                    raise RuntimeError('Trace pull/hash readback failed')
                receipt['trace_sha256'] = remote_hash; receipt['trace_bytes'] = trace.stat().st_size; pulled = True
            except Exception as error: receipt['trace_pull_error'] = str(error)
        if pushed:
            try:
                if start_attempted and not stopped: raise RuntimeError('Retain remote files until owned profiler termination can be verified')
                if start_attempted and not pulled: raise RuntimeError('Retain remote trace until a complete local copy is verified')
                remove_owned(adb, [remote_config, remote_trace]); receipt['cleanup'] = 'remote-files-removed-and-absence-verified'
            except Exception as error: receipt.update(cleanup='pending', cleanup_error=str(error))
        else: receipt['cleanup'] = 'no-remote-files-created'
        save()
    if pulled and app_pid is not None:
        try:
            if receipt['trace_bytes'] >= 67108864: raise RuntimeError('Trace file size limit reached; capture may be truncated')
            report = analyze(args.trace_processor, output/'native-heap.pftrace', output, app_pid,expected_seconds=args.duration)
            receipt['analysis_summary'] = report['summary'][0]; receipt['analysis_quality'] = report['quality']
        except Exception as error:
            receipt['analysis_error'] = str(error)
            if failure is None: failure = error
    elif failure is None: failure = RuntimeError('No verified trace/target process evidence')
    if receipt['cleanup'] not in ('remote-files-removed-and-absence-verified', 'no-remote-files-created') and failure is None:
        failure = RuntimeError('Remote profiler cleanup is incomplete')
    receipt['status'] = 'verified-capture' if failure is None else 'failed'
    save()
    if failure is not None: raise RuntimeError('Native heap capture incomplete; see '+str(output/'receipt.json')) from failure
    return receipt


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb', type=Path, required=True); parser.add_argument('--serial', required=True)
    parser.add_argument('--project', type=int); parser.add_argument('--output', type=Path)
    parser.add_argument('--apk-receipt', type=Path)
    parser.add_argument('--recover',type=Path,help='Recover only the exact resources recorded by an earlier owned capture')
    parser.add_argument('--trace-processor', type=Path, default=PROCESSOR)
    parser.add_argument('--duration', type=int, default=120); parser.add_argument('--ceiling-mib', type=int, default=3800)
    parser.add_argument('--start-app', action='store_true')
    args = parser.parse_args()
    if args.recover:
        try: result=recover(args)
        except Exception as error:print(str(error),flush=True);return 1
        print(json.dumps(result,ensure_ascii=False),flush=True);return 0
    if args.project is None or args.output is None or args.apk_receipt is None:
        parser.error('Capture requires --project, --output and --apk-receipt')
    if not 20 <= args.duration <= 300 or args.project < 1 or not 256 <= args.ceiling_mib <= 8192:
        parser.error('Invalid bounded duration/project/memory ceiling')
    try: result = capture(args)
    except Exception as error: print(str(error), flush=True); return 1
    print(json.dumps({'status': result['status'], 'cleanup': result['cleanup'], 'analysis': result['analysis_summary']}), flush=True)
    return 0


if __name__ == '__main__': raise SystemExit(main())
