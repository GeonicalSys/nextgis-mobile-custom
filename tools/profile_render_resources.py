"""Bounded manual render-resource sessions for an explicitly selected physical device.

Only start/stop of exact owned collectors is performed. No map input, application
install, data clearing, default force-stop or publication. App JSONL exports are
selected by header/source/PID and session inventory, never by a blanket download.
"""
from __future__ import annotations

import argparse
from contextlib import contextmanager
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import shlex
import shutil
import subprocess
import time
import uuid
from types import SimpleNamespace

from prepare_native_heap_apk import plain_directory, sha256
from profile_native_heap import (Adb, PACKAGE, assert_no_recorder, configuration,
                                 find_owned_profilers, parse_perfetto_pid, remote_absent, remote_paths,
                                 verify_installed)

SCHEMA = 1
SESSION_RE = re.compile(r'lisa-render-[0-9a-f]{16}')
APP_FILE_RE = re.compile(r'lisa-resources-([0-9a-f]{32})\.jsonl')
SOURCE_RE = re.compile(r'[0-9a-f]{40}(?:\+dirty)?')
APP_DIR = '/sdcard/Download/LISA-Diagnostics'
MAX_LIGHT_BYTES = 32 * 1024 * 1024
MAX_APP_FILE_BYTES = 64 * 1024 * 1024
MAX_APP_BYTES = 128 * 1024 * 1024
MAX_TRACE_BYTES = 32 * 1024 * 1024
MAX_WINDOWS = 5
MAX_PULLS = 2


def utc() -> str:
    return datetime.now(timezone.utc).isoformat()


def window_offset(duration: int, seconds: int, planned: int, index: int, schedule: str) -> float:
    if not 1<=planned<=MAX_WINDOWS or not 0<=index<planned or schedule not in ('continuous','spread'):
        raise ValueError('Invalid bounded window schedule')
    spacing=seconds if schedule=='continuous' else max(seconds,(duration-seconds-20)/max(1,planned-1))
    return index*spacing


def save(path: Path, value: dict) -> None:
    temporary = path.with_suffix(path.suffix + '.tmp')
    temporary.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    temporary.replace(path)


@contextmanager
def control_lock(receipt_path: Path, *, wait_seconds: float=35):
    """OS-owned lock releases even if a supervisor dies; no stale lock deletion."""
    parent=plain_directory(receipt_path.parent)
    if parent.drive.upper()!='Q:':raise ValueError('Capture control evidence must stay on Q')
    f=(receipt_path.parent/'controller.lock').open('a+b')
    if f.tell()==0:f.write(b'0');f.flush()
    until=time.monotonic()+wait_seconds
    acquired=False
    try:
        while not acquired:
            f.seek(0)
            try:
                if __import__('os').name=='nt':
                    import msvcrt
                    msvcrt.locking(f.fileno(),msvcrt.LK_NBLCK,1)
                else:
                    import fcntl
                    fcntl.flock(f.fileno(),fcntl.LOCK_EX|fcntl.LOCK_NB)
                acquired=True
            except OSError:
                if time.monotonic()>=until:raise RuntimeError('Another session control command is still running')
                time.sleep(.1)
        yield
    finally:
        if acquired:
            f.seek(0)
            if __import__('os').name=='nt':
                import msvcrt
                msvcrt.locking(f.fileno(),msvcrt.LK_UNLCK,1)
            else:
                import fcntl
                fcntl.flock(f.fileno(),fcntl.LOCK_UN)
        f.close()


def session_base(name: str) -> str:
    if not SESSION_RE.fullmatch(name): raise ValueError('Invalid owned session name')
    return '/data/local/tmp/' + name


def native_paths(session: str, token: str, layout: str) -> tuple[str,str]:
    """Two exact formats, each bound to a window nonce; tmp also binds session."""
    base=session_base(session)
    if not re.fullmatch('[0-9a-f]{16}',token):raise ValueError('Invalid native window nonce')
    name='lisa-heap-'+token
    if layout=='standard':return remote_paths(name)
    if layout=='owned_tmp':return base+'/'+name+'.pbtxt',base+'/'+name+'.pftrace'
    if layout=='stdin_standard':return base+'/'+name+'.pbtxt',remote_paths(name)[1]
    raise ValueError('Unknown native path layout')


def validate_native_pair(config: str, trace: str, session: str) -> str:
    token=re.search(r'lisa-heap-([0-9a-f]{16})\.pbtxt$',config)
    if token:
        for layout in ('standard','owned_tmp','stdin_standard'):
            if (config,trace)==native_paths(session,token.group(1),layout):return layout
    raise ValueError('Native pair does not belong to the exact session/window')


def choose_native_layout(adb: Adb, session: str) -> tuple[str,dict]:
    """Use supported stdin; OEM perfetto SELinux cannot read shell tmp config."""
    probes={}
    for directory in ('/data/misc/perfetto-traces',session_base(session)):
        for flag in ('-d','-w'):
            result=adb.attempt('test',flag,directory)
            if result.returncode==0:probes[directory+':'+flag]=True
            elif remote_absent(result):
                raise RuntimeError('Supported native stdin/output directory is unavailable; retain light capture without permission changes')
            else:raise RuntimeError('Native output directory capability is indeterminate; do not retry via permission changes')
    return 'stdin_standard',probes


def native_stdin_command(session: str, config: str, trace: str) -> list[str]:
    if validate_native_pair(config,trace,session)!='stdin_standard':raise ValueError('New native capture requires supported stdin/standard trace layout')
    return ['perfetto','--background','--txt','-c','-','-o',trace]


def invoke_native_background(adb: Adb, session: str, config: str, trace: str, config_bytes: bytes):
    """Native daemonization preserves PID; host stdin avoids vendor tmp SELinux."""
    if not config_bytes or len(config_bytes)>8192:raise ValueError('Unexpected bounded native config bytes')
    command=native_stdin_command(session,config,trace)
    return subprocess.run([str(adb.binary),'-s',adb.serial,'shell',*command],input=config_bytes,
                          stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=15)


def cpu_record_command(session: str, token: str, seconds: int, graph: str) -> list[str]:
    base=session_base(session)
    if not re.fullmatch('[0-9a-f]{16}',token) or not 20<=seconds<=300 or graph not in ('fp','dwarf,8192'):
        raise ValueError('Unknown bounded CPU record options')
    return ['/system/bin/simpleperf','record','--app',PACKAGE,'-e','task-clock:u','-f','99',
            '--call-graph',graph,'--duration',str(seconds),'--size-limit','32M','-o',base+'/cpu-'+token+'.data']


def proc_stat(text: str) -> dict:
    """Parse comm containing spaces/parentheses; start ticks protects PID reuse."""
    opening, closing = text.find('('), text.rfind(')')
    if opening < 1 or closing <= opening: raise ValueError('Invalid proc stat')
    pid = int(text[:opening].strip()); fields = text[closing + 1:].split()
    if pid < 1 or len(fields) < 22: raise ValueError('Incomplete proc stat')
    return {'pid': pid, 'comm': text[opening+1:closing], 'start_ticks': int(fields[19]),
            'utime_ticks': int(fields[11]), 'stime_ticks': int(fields[12])}


def command_fields(raw: str) -> list[str]:
    fields = raw.rstrip('\x00\r\n').split('\x00')
    if not fields or not fields[0]: raise ValueError('Empty command line')
    return fields


def owns_command(kind: str, raw: str, expected: dict, name: str) -> bool:
    fields = command_fields(raw)
    base = session_base(name)
    program = fields[0].rsplit('/', 1)[-1]
    if kind == 'light':
        return (program == 'sh' and len(fields) >= 4 and fields[1] == base + '/light.sh'
                and fields[3] == name)
    if kind == 'native':
        config, trace = expected['config'], expected['trace']
        layout=validate_native_pair(config, trace, name)
        ci=fields.index('-c') if '-c' in fields else -1
        oi=fields.index('-o') if '-o' in fields else -1
        return (program=='perfetto' and 0<=ci<len(fields)-1 and 0<=oi<len(fields)-1
                and fields[ci+1]==('-' if layout=='stdin_standard' else config) and fields[oi+1]==trace)
    if kind == 'cpu':
        output = expected['output']
        target = fields.index('-p') if '-p' in fields else -1
        app=fields.index('--app') if '--app' in fields else -1
        selector=(0<=app<len(fields)-1 and fields[app+1]==PACKAGE if expected.get('selector')=='app'
                  else 0<=target<len(fields)-1 and fields[target+1]==str(expected['target_pid']))
        oi=fields.index('-o') if '-o' in fields else -1
        return (program == 'simpleperf' and re.fullmatch(re.escape(base)+r'/cpu-[0-9a-f]{16}\.data',output) is not None
                and 'record' in fields and 0<=oi<len(fields)-1 and fields[oi+1]==output and selector)
    raise ValueError('Unknown collector kind')


def inspect_process(adb: Adb, pid: int, kind: str, expected: dict, name: str) -> dict | None:
    if type(pid) is not int or pid < 1: raise ValueError('Invalid owned PID')
    result = adb.attempt('cat', '/proc/' + str(pid) + '/stat')
    if result.returncode:
        if remote_absent(adb.attempt('test', '-d', '/proc/' + str(pid))): return None
        raise RuntimeError('Collector process state is unreadable; not signalling it')
    parsed = proc_stat(result.stdout.decode('utf-8', 'replace'))
    if parsed['pid'] != pid: raise RuntimeError('Collector PID changed')
    raw = adb.shell('cat', '/proc/' + str(pid) + '/cmdline')
    if not owns_command(kind, raw, expected, name):
        raise RuntimeError('PID belongs to another command; not signalling it')
    if expected.get('start_ticks') is not None and parsed['start_ticks'] != expected['start_ticks']:
        raise RuntimeError('PID was reused; not signalling it')
    return {**parsed, 'cmdline': raw}


def find_owned_cpu_parents(adb: Adb, owner: dict, session: str) -> list[int]:
    """setsid may fork: select the exact output-bound parent, never its launcher."""
    result=adb.attempt('pidof','simpleperf')
    if remote_absent(result):return []
    if result.returncode:raise RuntimeError('Cannot discover owned CPU parent identity')
    candidates=[]
    for raw in result.stdout.decode().split():
        if not re.fullmatch('[1-9][0-9]*',raw):raise ValueError('Invalid candidate CPU PID')
        cmd=adb.attempt('cat','/proc/'+raw+'/cmdline')
        if cmd.returncode:
            if remote_absent(adb.attempt('test','-d','/proc/'+raw)):continue
            raise RuntimeError('Candidate CPU process identity is unreadable')
        if owns_command('cpu',cmd.stdout.decode('utf-8','replace'),owner,session):candidates.append(int(raw))
    if len(candidates)>1:raise RuntimeError('More than one exact output-bound CPU parent exists')
    return candidates


def wait_cpu_ack(adb: Adb, owner: dict, session: str, *, timeout: float=5) -> dict:
    until=time.monotonic()+timeout
    expected={k:v for k,v in owner.items() if k not in ('pid','start_ticks')}
    while time.monotonic()<until:
        candidates=find_owned_cpu_parents(adb,expected,session)
        if candidates:
            pid=candidates[0]
            state=inspect_process(adb,pid,'cpu',expected,session)
            if state is not None:
                return {'pid':pid,'start_ticks':state['start_ticks'],
                        'startup_ack':'EXACT_OUTPUT_BOUND_SIMPLEPERF_PARENT_LIVE'}
        time.sleep(.1)
    raise RuntimeError('No live exact output-bound CPU parent acknowledged; inspect owned log, do not infer recording from setsid launcher PID')


def recover_window_owners(adb: Adb, window: dict, session: str) -> None:
    """Discover only exact owned paths after a transport interrupted startup."""
    for kind in ('native','cpu'):
        owner=window[kind]
        if 'pid' in owner and 'start_ticks' in owner:continue
        if 'pid' in owner:
            # Old receipts may have recorded setsid's short-lived returned PID.
            # It was never acknowledged with start ticks, so cannot be signalled.
            owner.setdefault('unverified_returned_pid',owner.pop('pid'))
        if kind=='native':
            if validate_native_pair(owner['config'],owner['trace'],session)=='stdin_standard':
                result=adb.attempt('pidof','perfetto')
                if remote_absent(result):candidates=[]
                elif result.returncode:raise RuntimeError('Cannot recover owned stdin native collector identity')
                else:
                    candidates=[]
                    for raw in result.stdout.decode().split():
                        if not re.fullmatch('[1-9][0-9]*',raw):raise ValueError('Invalid candidate native PID')
                        cmd=adb.attempt('cat','/proc/'+raw+'/cmdline')
                        if cmd.returncode:
                            if remote_absent(adb.attempt('test','-d','/proc/'+raw)):continue
                            raise RuntimeError('Candidate native process identity is unreadable')
                        if owns_command('native',cmd.stdout.decode('utf-8','replace'),owner,session):candidates.append(int(raw))
            else:candidates=find_owned_profilers(adb,owner['config'],owner['trace'])
        else:
            candidates=find_owned_cpu_parents(adb,owner,session)
        if len(candidates)>1:raise RuntimeError('More than one process matches one owned collector')
        if candidates:
            owner['pid']=candidates[0]
            observed=inspect_process(adb,owner['pid'],kind,owner,session)
            if observed:owner['start_ticks']=observed['start_ticks']
        elif owner.get('launch_attempted'):
            raise RuntimeError('Detached '+kind+' launch was not acknowledged; retain incomplete stop until identity/exit is verified')


def recover_light_owner(adb: Adb, receipt: dict) -> None:
    """A transport failure after detached launch must not hide a live sampler."""
    if receipt.get('light') or not receipt.get('light_launch_attempted'):return
    owner_file=receipt['remote_base']+'/light/owner.pid'
    raw=adb.attempt('cat',owner_file)
    if raw.returncode:
        raise RuntimeError('Light launch was attempted but its owner was not acknowledged; keep external stop incomplete')
    value=raw.stdout.decode().strip()
    if not re.fullmatch('[1-9][0-9]*',value):raise ValueError('Invalid recovered light owner PID')
    parsed=proc_stat(adb.shell('cat',receipt['remote_base']+'/light/owner-stat.raw'))
    if parsed['pid']!=int(value):raise RuntimeError('Recovered light owner identities differ')
    expected={'pid':int(value),'start_ticks':parsed['start_ticks'],'recovered_after_start_failure':True}
    inspect_process(adb,expected['pid'],'light',expected,receipt['session'])
    receipt['light']=expected


def wait_light_ack(adb: Adb, receipt: dict, *, timeout: float=12) -> tuple[dict,dict]:
    """File creation precedes shell printf/cat: rc0 alone is never startup ACK."""
    base=receipt['remote_base'];until=time.monotonic()+timeout
    while time.monotonic()<until:
        pid_result=adb.attempt('cat',base+'/light/owner.pid')
        value=pid_result.stdout.decode().strip() if pid_result.returncode==0 else ''
        if re.fullmatch('[1-9][0-9]*',value):
            raw=adb.attempt('cat',base+'/light/owner-stat.raw')
            started=adb.attempt('cat',base+'/light/start.json')
            if raw.returncode==0 and started.returncode==0:
                try:
                    parsed=proc_stat(raw.stdout.decode())
                    header=json.loads(started.stdout.decode())
                except (ValueError,UnicodeDecodeError):
                    parsed=None
                if parsed is not None:
                    if parsed['pid']!=int(value):raise RuntimeError('Light ACK owner identities differ')
                    if (header.get('event')!='light_start' or header.get('session')!=receipt['session']
                            or header.get('owner_pid')!=parsed['pid'] or header.get('max_bytes')!=MAX_LIGHT_BYTES):
                        raise RuntimeError('Light ACK session identity/bound differs')
                    owner={'pid':parsed['pid'],'start_ticks':parsed['start_ticks']}
                    if inspect_process(adb,owner['pid'],'light',owner,receipt['session']) is None:
                        raise RuntimeError('Light sampler exited during startup')
                    return owner,header
        time.sleep(.25)
    raise RuntimeError('Light sampler did not acknowledge a complete PID/start-stat/session header')


def stop_process(adb: Adb, kind: str, expected: dict, name: str) -> str:
    pid = expected['pid']
    if inspect_process(adb, pid, kind, expected, name) is None: return 'already-exited'
    # SIGINT asks simpleperf to finalize; TERM finalizes the owned Perfetto client.
    signal = '-INT' if kind == 'cpu' else '-TERM'
    result = adb.attempt('kill', signal, str(pid))
    if result.returncode and not remote_absent(adb.attempt('test', '-d', '/proc/' + str(pid))):
        raise RuntimeError('Owned collector signal failed')
    until = time.monotonic() + 12
    while time.monotonic() < until:
        if inspect_process(adb, pid, kind, expected, name) is None: return 'stopped-and-absent'
        time.sleep(.25)
    raise RuntimeError('Owned collector remains active; retain files and inspect receipt')


def require_fresh(adb: Adb, path: str) -> None:
    if not remote_absent(adb.attempt('test', '-e', path)):
        raise RuntimeError('Fresh owned output path is not proven absent: ' + path)


def file_sha(adb: Adb, path: str) -> str:
    value = adb.shell('sha256sum', path).split()[0]
    if not re.fullmatch('[0-9a-f]{64}', value): raise RuntimeError('Invalid remote SHA256')
    return value


def validate_app_header(filename: str, text: str, source: str) -> dict:
    match = APP_FILE_RE.fullmatch(filename)
    if not match: raise ValueError('Unknown app export filename')
    if len(text.encode('utf-8')) > 8192: raise ValueError('Oversize app header')
    row = json.loads(text.splitlines()[0])
    if (row.get('event') != 'app_session' or row.get('schema_version') != SCHEMA
            or row.get('session') != match.group(1)
            or row.get('private_resource_profiling') is not True
            or row.get('package_name') != PACKAGE
            or row.get('source_revision') != source
            or type(row.get('pid')) is not int or row['pid'] < 1
            or type(row.get('app_build')) is not int or row['app_build'] < 1
            or not isinstance(row.get('app_version'),str) or not row['app_version']
            or type(row.get('process_64_bit')) is not bool):
        raise ValueError('App export identity does not match this private capture')
    if row.get('max_file_bytes') != MAX_APP_FILE_BYTES:
        raise ValueError('App journal bound differs from known format')
    return row


def app_footer_quality(header: dict, events: list[dict], partial: bool) -> dict:
    ends=[x for x in events if x.get('event')=='app_end']
    if len(ends)>1:raise ValueError('More than one terminal app footer')
    footer=ends[0] if ends else {}
    if footer and events[-1] is not footer:raise ValueError('Records after terminal app footer')
    summary=footer.get('summary')
    bound=(isinstance(summary,dict) and summary.get('session')==header['session']
           and summary.get('revision')==header['source_revision']
           and summary.get('version')==header.get('app_version')
           and summary.get('build')==header.get('app_build') and summary.get('stopped') is True)
    complete=(bool(footer) and footer.get('session')==header['session'] and footer.get('pid')==header['pid']
              and footer.get('complete_footer') is True and footer.get('summary_available') is True
              and footer.get('resource_end_persisted_in_footer') is True and bound and not partial)
    return {'has_app_end':bool(footer),'summary_bound_to_header':bound,'complete_summary_footer':complete,
            'raw_app_stream_complete':complete and footer.get('raw_app_stream_complete') is True,
            'detailed_complete':complete and summary.get('detailed_complete') is True,
            'cpu_fully_attributed':complete and summary.get('cpu_fully_attributed') is True,
            'queue_dropped_records':footer.get('queue_dropped_records'),
            'file_rejected_records':footer.get('file_rejected_records'),
            'observer_errors':footer.get('observer_errors')}


def app_inventory(adb: Adb) -> dict[str, dict]:
    result = adb.attempt('ls', '-1', APP_DIR)
    if result.returncode:
        if remote_absent(adb.attempt('test', '-d', APP_DIR)): return {}
        raise RuntimeError('App Downloads directory is inaccessible; no scoped-storage bypass')
    inventory = {}
    for name in result.stdout.decode('utf-8', 'replace').splitlines():
        if not APP_FILE_RE.fullmatch(name): continue
        path = APP_DIR + '/' + name
        size = int(adb.shell('stat', '-c', '%s', path))
        inventory[name] = {'path': path, 'size': size}
    return inventory


def current_pid(adb: Adb) -> int | None:
    value = adb.attempt('pidof', PACKAGE).stdout.decode().strip()
    if not value: return None
    if not re.fullmatch('[1-9][0-9]*', value): raise RuntimeError('Expected one exact main app PID')
    return int(value)


def read_app_sessions(adb: Adb, receipt: dict) -> tuple[list[dict], list[dict]]:
    owned, rejected = [], []
    pid = current_pid(adb)
    for name, info in app_inventory(adb).items():
        # Pre-existing unrelated exports remain untouched, even with a matching prefix.
        old = receipt['baseline_app_files'].get(name)
        try:
            header = validate_app_header(name, adb.shell('head', '-n', '1', info['path']), receipt['source_revision'])
            if old is not None and header['pid'] != receipt.get('initial_app_pid'):
                continue
            if old is not None and info['size'] == old['size'] and header['pid'] != pid:
                continue
            owned.append({**info, 'filename': name, 'header': header,
                          'preexisting_bytes': old['size'] if old else 0})
        except (ValueError, KeyError, IndexError, json.JSONDecodeError) as error:
            rejected.append({'filename': name, 'reason': type(error).__name__})
    return owned, rejected


def require_active_app_session(adb: Adb, receipt: dict) -> dict:
    """A completed journal in the same PID is not a running app collector."""
    pid=current_pid(adb)
    if pid is None:raise RuntimeError('Diagnostic app process is absent')
    clock_ticks=int(adb.shell('getconf','CLK_TCK'))
    if not 1<=clock_ticks<=1_000_000:raise RuntimeError('Unknown device process clock tick rate')
    process=proc_stat(adb.shell('cat','/proc/'+str(pid)+'/stat'))
    owned,_=read_app_sessions(adb,receipt)
    now_ns=int(float(adb.shell('cat','/proc/uptime').split()[0])*1_000_000_000)
    candidates=[]
    for item in owned:
        h=item['header']
        if h['pid']!=pid:continue
        if (type(h.get('process_start_ticks')) is not int or h['process_start_ticks']!=process['start_ticks']
                or type(h.get('clock_tick_hz')) is not int or h['clock_tick_hz']!=clock_ticks):continue
        if receipt.get('heavy_seconds',0) and h['process_64_bit'] is not True:continue
        if type(h.get('deadline_elapsed_ns')) is not int or h['deadline_elapsed_ns']<=now_ns:continue
        if item['size']>=MAX_APP_FILE_BYTES:continue
        # The app reserves256KiB for its final footer. A bounded512KiB tail
        # contains that complete final line even if the preceding line is split.
        tail=adb.shell('tail','-c','524288',item['path'])
        ended=False
        for line in tail.splitlines():
            try:row=json.loads(line)
            except json.JSONDecodeError:continue
            if row.get('event')=='app_end' and row.get('session')==h['session']:ended=True
        if not ended:candidates.append(item)
    if len(candidates)!=1:
        raise RuntimeError('Expected one active private app journal for the current PID; a stopped collector needs a fresh app process')
    return candidates[0]


def light_script(name: str, expected_apk: str) -> str:
    base = session_base(name)
    if not re.fullmatch('[0-9a-f]{64}', expected_apk): raise ValueError('Invalid APK hash')
    return r'''#!/system/bin/sh
BASE=@@BASE@@
PKG=com.nextgis.mobile.geonical
EXPECTED_APK=@@APK@@
DURATION=$1
TAG=$2
[ "$TAG" = "@@NAME@@" ] || exit 64
case "$DURATION" in ''|*[!0-9]*) exit 64;; esac
[ "$DURATION" -ge 60 ] && [ "$DURATION" -le 3600 ] || exit 64
RUN=$BASE/light
mkdir "$RUN" || exit 65
umask 077
trap '' HUP
getup() { read -r UP ignored < /proc/uptime; NOW=${UP%%.*}; }
getup
START=$NOW; DEADLINE=$((START+DURATION)); LAST_MEM=$((START-15)); N=0; M=0; LAST_PID=0; REASON=deadline
finish() {
 trap - EXIT INT TERM
 getup
 printf '{"event":"light_end","session":"%s","owner_pid":%s,"reason":"%s","elapsed_uptime_s":%s,"cpu_samples":%s,"memory_samples":%s,"data_cleanup":false}\n' "$TAG" "$$" "$REASON" "$((NOW-START))" "$N" "$M" > "$RUN/exit.json"
}
trap finish EXIT
trap 'REASON=owned_signal; exit 0' INT TERM
printf '%s\n' "$$" > "$RUN/owner.pid"
cat /proc/$$/stat > "$RUN/owner-stat.raw"
printf '{"event":"light_start","session":"%s","owner_pid":%s,"start_uptime_s":%s,"deadline_uptime_s":%s,"cpu_interval_s":5,"memory_interval_s":15,"max_bytes":33554432,"no_wakelock":true}\n' "$TAG" "$$" "$START" "$DEADLINE" > "$RUN/start.json"
while :; do
 getup
 [ "$NOW" -lt "$DEADLINE" ] || break
 if [ -f "$BASE/stop" ]; then REASON=owned_stop_marker; break; fi
 KIB=$(du -sk "$RUN"); KIB=${KIB%%[!0-9]*}
 case "$KIB" in ''|*[!0-9]*) REASON=budget_unknown; break;; esac
 [ "$KIB" -lt 30720 ] || { REASON=budget_limit; break; }
 PID=$(pidof "$PKG" 2>/dev/null)
 case "$PID" in ''|*[!0-9]*) PID=0;; esac
 if [ "$PID" != "$LAST_PID" ] && [ "$PID" != 0 ]; then
  APATH=$(pm path "$PKG" 2>/dev/null); APATH=${APATH#package:}
  AHASH=$(sha256sum "$APATH" 2>/dev/null); AHASH=${AHASH%% *}
  [ "$AHASH" = "$EXPECTED_APK" ] || { REASON=apk_changed; break; }
 fi
 N=$((N+1)); C=$(printf '%04d' "$N")
 {
  printf '[uptime]\n'; cat /proc/uptime
  printf '[system_proc_stat]\n'; cat /proc/stat
  printf '[cpu_online]\n'; cat /sys/devices/system/cpu/online
  printf '[main_pid]\n%s\n' "$PID"
  if [ "$PID" != 0 ]; then
   printf '[process_stat]\n'; cat "/proc/$PID/stat"
   printf '[process_status]\n'; cat "/proc/$PID/status"
  fi
 } 2>&1 | head -c 65536 > "$RUN/cpu-$C.txt"
 [ "$(wc -c < "$RUN/cpu-$C.txt")" -lt 65536 ] || { REASON=raw_cap; break; }
 if [ "$PID" != 0 ] && [ "$((NOW-LAST_MEM))" -ge 15 ]; then
  M=$((M+1)); MC=$(printf '%04d' "$M")
  dumpsys -t 5 meminfo "$PID" 2>&1 | head -c 131072 > "$RUN/memory-$MC.txt"
  [ "$(wc -c < "$RUN/memory-$MC.txt")" -lt 131072 ] || { REASON=raw_cap; break; }
  LAST_MEM=$NOW
 fi
 LAST_PID=$PID
 printf '{"event":"light_heartbeat","session":"%s","owner_pid":%s,"main_pid":%s,"observed_uptime_s":%s,"cpu_samples":%s,"memory_samples":%s}\n' "$TAG" "$$" "$PID" "$NOW" "$N" "$M" > "$RUN/heartbeat.json"
 sleep 5
done
'''.replace('@@BASE@@', shlex.quote(base)).replace('@@APK@@', expected_apk).replace('@@NAME@@', name)


def load(args) -> tuple[Path, dict, Adb]:
    path = plain_directory(args.receipt.parent) / args.receipt.name
    if path.drive.upper()!='Q:':raise ValueError('Capture evidence must stay on Q')
    receipt = json.loads(path.read_text(encoding='utf-8-sig'))
    if receipt.get('schema_version') != SCHEMA or receipt.get('package') != PACKAGE:
        raise ValueError('Unknown capture receipt')
    if session_base(receipt['session']) != receipt['remote_base']:
        raise ValueError('Capture root differs from owned session')
    if not re.fullmatch('[A-Za-z0-9_.:-]+', receipt['serial']): raise ValueError('Invalid explicit device serial')
    if not SOURCE_RE.fullmatch(receipt['source_revision']): raise ValueError('Unknown source identity')
    if len(receipt.get('windows',[]))>MAX_WINDOWS:raise ValueError('Too many recorded windows')
    for window in receipt.get('windows',[]):
        token=window.get('id','')
        if not re.fullmatch('[0-9a-f]{16}',token):raise ValueError('Invalid heavy window ID')
        layout=validate_native_pair(window['native']['config'],window['native']['trace'],receipt['session'])
        if ((window['native']['config'],window['native']['trace'])!=native_paths(receipt['session'],token,layout)
                or window.get('native_path_layout',layout)!=layout):
            raise ValueError('Native paths do not belong to window ID')
        if window['cpu']['output']!=receipt['remote_base']+'/cpu-'+token+'.data':
            raise ValueError('CPU path does not belong to session/window')
    return path, receipt, Adb(args.adb, receipt['serial'])


def start_window(adb: Adb, receipt: dict, output: Path, seconds: int, *, cold_start: bool=False,
                 confirm_idle: bool=False) -> dict:
    if not 20 <= seconds <= 300: raise ValueError('Heavy window must be20..300seconds')
    if len(receipt['windows']) >= MAX_WINDOWS: raise ValueError('Five-window session bound reached')
    for prior in receipt['windows']:
        recover_window_owners(adb,prior,receipt['session'])
        for kind in ('native', 'cpu'):
            owned = prior.get(kind)
            if owned and inspect_process(adb, owned['pid'], kind, owned, receipt['session']) is not None:
                raise RuntimeError('Previous owned heavy window is still active')
    pid = current_pid(adb)
    if cold_start:
        if not confirm_idle:raise ValueError('Cold start requires explicit idle/no-editor confirmation')
    if pid is None and not cold_start:
        raise RuntimeError('Open the diagnostic application before starting a heavy window')
    installed=adb.shell('pm','path',PACKAGE).splitlines()
    if len(installed)!=1 or not installed[0].startswith('package:'):raise RuntimeError('Unknown APK layout')
    if file_sha(adb,installed[0][8:])!=receipt['installed_apk']['sha256']:
        raise RuntimeError('Installed APK changed; no new profiling window started')
    package = adb.shell('dumpsys', 'package', PACKAGE)
    if not re.search(r'primaryCpuAbi=(?:arm64-v8a|x86_64)\b', package):
        raise RuntimeError('Heavy capture requires a verified64bit app; retain light capture')
    help_result=adb.attempt('/system/bin/simpleperf','record','--help')
    help_text=(help_result.stdout+help_result.stderr).decode('utf-8','replace')
    if '--size-limit' not in help_text:
        raise RuntimeError('System simpleperf lacks mandatory size bound; no unbounded fallback')
    token = uuid.uuid4().hex[:16]
    name = 'lisa-heap-' + token
    layout,path_capability=choose_native_layout(adb,receipt['session'])
    config, trace = native_paths(receipt['session'],token,layout)
    cpu_path = receipt['remote_base'] + '/cpu-' + token + '.data'
    native_path = output / (name + '.pbtxt')
    config_text=configuration(seconds,name)
    assert config_text.count('max_file_size_bytes: 67108864')==1
    config_text=config_text.replace('max_file_size_bytes: 67108864','max_file_size_bytes: 33554432')
    native_path.write_text(config_text, encoding='ascii')
    for p in (config, trace, cpu_path): require_fresh(adb, p)
    window = {'id': token, 'requested_seconds': seconds, 'target_pid': None if cold_start else pid,
              'native_path_layout':layout,'native_path_capability':path_capability,
              'target_start_ticks': proc_stat(adb.shell('cat', '/proc/' + str(pid) + '/stat'))['start_ticks'] if pid and not cold_start else None,
              'started_utc': utc(), 'coverage': 'late_attach_new_allocations_only',
              'native_config_sha256': sha256(native_path), 'state': 'PREPARING',
              'native': {'config': config, 'trace': trace, 'config_transport':'host_stdin_native_background'},
              'cpu': {'output': cpu_path, 'target_pid': None if cold_start else pid,'selector':'app'}}
    # Save before starts: a transport interruption never licenses a blind retry.
    receipt['windows'].append(window); save(output/'receipt.json', receipt)
    adb.invoke('push', str(native_path), config)
    if file_sha(adb, config) != window['native_config_sha256']: raise RuntimeError('Native config transfer mismatch')
    perfetto_help=adb.attempt('perfetto','--help')
    help_text=(perfetto_help.stdout+perfetto_help.stderr).decode('utf-8','replace')
    if cold_start:
        # Preparation can fail independently. Only now, immediately before the
        # native client, recheck current UI/services and perform the explicit stop.
        assert_no_recorder(adb.shell('dumpsys','activity','services',PACKAGE))
        pid=current_pid(adb)
        if pid is not None:
            activity=adb.shell('dumpsys','activity','activities')
            lines=[x for x in activity.splitlines() if 'ResumedActivity' in x or 'topResumedActivity=' in x]
            if not any(PACKAGE+'/com.nextgis.mobile.activity.MainActivity' in x for x in lines):
                raise RuntimeError('Cold restart requires the normal idle main screen')
            adb.shell('am','force-stop',PACKAGE)
            window['cold_stop_performed']=True;save(output/'receipt.json',receipt)
            pid=None
    window['native']['launch_attempted']=True;save(output/'receipt.json',receipt)
    config_bytes=native_path.read_bytes()
    if hashlib.sha256(config_bytes).hexdigest()!=window['native_config_sha256']:
        raise RuntimeError('Native config bytes changed before stdin launch')
    launched=invoke_native_background(adb,receipt['session'],config,trace,config_bytes)
    window['native'].update(startup_ack='NATIVE_BACKGROUND_PID_ACK_NOT_DATASOURCE_READY',
                             launch_returncode=launched.returncode,
                             launch_stderr_tail=launched.stderr.decode('utf-8','replace')[-4096:])
    save(output/'receipt.json',receipt)
    if launched.returncode:raise RuntimeError('Native background launch failed; inspect owned launch stderr in receipt')
    native_pid = parse_perfetto_pid(launched.stdout.decode('utf-8','replace'))
    window['native']['pid'] = native_pid
    state = inspect_process(adb, native_pid, 'native', window['native'], receipt['session'])
    if state is None: raise RuntimeError('Native profiler exited before acknowledgement')
    window['native']['start_ticks'] = state['start_ticks']; save(output/'receipt.json', receipt)
    if cold_start:
        adb.shell('am','start','-W','-n',PACKAGE+'/com.nextgis.mobile.activity.MainActivity')
        pid=current_pid(adb)
        if pid is None:raise RuntimeError('Cold launch did not produce one main process')
        window.update(target_pid=pid,target_start_ticks=proc_stat(adb.shell('cat','/proc/'+str(pid)+'/stat'))['start_ticks'],
                      coverage='STARTUP_REQUESTED_VERIFY_FROM_STARTUP_IN_TRACE')
        window['cpu']['target_pid']=pid
    cpu_log = receipt['remote_base'] + '/cpu-' + token + '.log'
    graph=receipt.get('cpu_callgraph','fp')
    if graph not in ('fp','dwarf,8192'):raise ValueError('Unknown bounded CPU callgraph option')
    command=cpu_record_command(receipt['session'],token,seconds,graph)
    window['cpu'].update(callgraph=graph,sampling_hz=99,
                         coverage='PACKAGE_PROCESSES_DISCOVERED_AT_START_VERIFY_PROFILE_PID_SEGMENTS',
                         lifecycle='SYSTEM_APP_RUNNER_PARENT_FORWARDS_STOP_PIPE_AND_WAITS_CHILD')
    launch = ('/system/bin/setsid nohup ' + shlex.join(command) + ' </dev/null >' + shlex.quote(cpu_log)
              + ' 2>&1 & echo $!')
    window['cpu']['launch_attempted']=True;save(output/'receipt.json',receipt)
    launcher_pid=parse_perfetto_pid(adb.shell(launch))
    window['cpu'].update(launcher_pid=launcher_pid,log=cpu_log)
    save(output/'receipt.json',receipt)
    window['cpu'].update(wait_cpu_ack(adb,window['cpu'],receipt['session']))
    window['state'] = 'CAPTURING_BOUNDED_WINDOW'; save(output/'receipt.json', receipt)
    return window


def start(args) -> dict:
    output = plain_directory(args.output)
    if output.drive.upper() != 'Q:': raise ValueError('Capture evidence must stay on Q')
    if output.exists(): raise FileExistsError('Use a fresh capture directory')
    if shutil.disk_usage(output.anchor).free < 30*1024**3:
        raise RuntimeError('Less than30GiB free on Q; reuse/cleanup planning required before trace capture')
    if not 60 <= args.duration <= 3600: raise ValueError('Session duration must be60..3600seconds')
    if not SOURCE_RE.fullmatch(args.source_revision): raise ValueError('Expected exact source revision')
    if not re.fullmatch('[A-Za-z0-9_.:-]+', args.serial): raise ValueError('Invalid explicit device serial')
    if not 0 <= args.heavy_seconds <= 300 or 0 < args.heavy_seconds < 20:
        raise ValueError('Heavy duration must be0 or20..300seconds')
    if not 1<=args.windows<=MAX_WINDOWS:raise ValueError('Invalid heavy window bound')
    if args.cold_start and not args.heavy_seconds:raise ValueError('Cold startup mode requires a heavy window')
    output.mkdir(parents=True)
    with control_lock(output/'receipt.json'):
        return start_locked(args,output)


def start_locked(args, output: Path) -> dict:
    """The start transaction and failure finalization hold the same OS lock."""
    name = 'lisa-render-' + uuid.uuid4().hex[:16]
    base = session_base(name)
    adb = Adb(args.adb, args.serial)
    installed = verify_installed(adb, args.apk_receipt)
    sdk = int(adb.shell('getprop', 'ro.build.version.sdk'))
    if sdk < 29 or adb.shell('getprop', 'ro.hardware') in ('ranchu', 'goldfish'):
        raise ValueError('An explicit physical Android10+ device is required')
    receipt = {'schema_version': SCHEMA, 'state': 'PREPARING', 'created_utc': utc(),
               'session': name, 'serial': args.serial, 'package': PACKAGE, 'sdk': sdk,
               'source_revision': args.source_revision, 'installed_apk': installed,
               'duration_seconds': args.duration, 'remote_base': base,
               'initial_app_pid': current_pid(adb), 'baseline_app_files': app_inventory(adb),
               'windows': [], 'app_sessions': [], 'cleanup': 'RETAIN_UNTIL_VERIFIED_PULL',
               'cpu_callgraph':args.cpu_callgraph,'scheduled_windows':args.windows,
               'heavy_seconds':args.heavy_seconds,'schedule':args.schedule,
               'tool_sha256': sha256(Path(__file__)),
               'limits': {'light_bytes': MAX_LIGHT_BYTES, 'app_export_bytes': MAX_APP_BYTES,
                          'each_trace_bytes': MAX_TRACE_BYTES, 'heavy_windows': MAX_WINDOWS},
               'qualification': 'Manual app workload. No gestures/install/clear/defaultforce-stop. Native allocations only after attach; memorycategories are not per-layer retained sizes.'}
    save(output/'receipt.json', receipt)
    try:
        require_fresh(adb, base)
        adb.shell('mkdir', base)
        source = output/'light.sh'; source.write_text(light_script(name, installed['sha256']), encoding='ascii', newline='\n')
        adb.invoke('push', str(source), base+'/light.sh')
        if file_sha(adb, base+'/light.sh') != sha256(source): raise RuntimeError('Light script transfer mismatch')
        command = ('/system/bin/setsid nohup sh ' + shlex.quote(base+'/light.sh') + ' '
                   + str(args.duration) + ' ' + name + ' </dev/null >' + shlex.quote(base+'/runner.log') + ' 2>&1 &')
        receipt['light_launch_attempted']=True;save(output/'receipt.json',receipt)
        adb.shell(command)
        receipt['light'],receipt['light_start']=wait_light_ack(adb,receipt)
        receipt['light']['script_sha256']=sha256(source)
        save(output/'receipt.json', receipt)
        # Startup export creation/flush may trail MainApplication construction.
        def wait_active_app():
            until=time.monotonic()+12
            while True:
                try:
                    receipt['active_app_at_start']=require_active_app_session(adb,receipt)
                    return
                except RuntimeError:
                    if time.monotonic()>=until:raise
                    time.sleep(.5)
        if not args.cold_start:wait_active_app()
        if args.heavy_seconds:start_window(adb,receipt,output,args.heavy_seconds,cold_start=args.cold_start,
                                           confirm_idle=args.confirm_idle_no_editor)
        if args.cold_start:wait_active_app()
        receipt['state'] = 'CAPTURING_MANUAL_SESSION'; save(output/'receipt.json', receipt)
        return receipt
    except BaseException as error:
        receipt.update(state='START_INCOMPLETE_RESOURCES_REQUIRE_STATUS_OR_STOP', error_type=type(error).__name__, error=str(error))
        save(output/'receipt.json', receipt)
        try:
            recovery=stop(SimpleNamespace(receipt=output/'receipt.json',adb=args.adb,stop_app=False))
            current=json.loads((output/'receipt.json').read_text(encoding='utf-8-sig'))
            current['startup_failure']={'error_type':type(error).__name__,'error':str(error),
                                        'external_finalization':recovery,'app_journaling_stopped':False}
            save(output/'receipt.json',current)
        except Exception as cleanup_error:
            current=json.loads((output/'receipt.json').read_text(encoding='utf-8-sig'))
            current['startup_failure_finalization_error']=str(cleanup_error)
            save(output/'receipt.json',current)
        raise


def status(args) -> dict:
    path, receipt, adb = load(args)
    report = {'utc': utc(), 'session': receipt['session'], 'main_pid_now': current_pid(adb), 'collectors': []}
    recover_light_owner(adb,receipt)
    if receipt.get('light'):
        alive = inspect_process(adb, receipt['light']['pid'], 'light', receipt['light'], receipt['session'])
        report['collectors'].append({'kind': 'light', 'alive': alive is not None})
    for w in receipt['windows']:
        recover_window_owners(adb,w,receipt['session'])
        for kind in ('native', 'cpu'):
            owned=w.get(kind, {})
            if 'pid' in owned:
                alive=inspect_process(adb, owned['pid'], kind, owned, receipt['session'])
                report['collectors'].append({'kind': kind, 'window': w['id'], 'alive': alive is not None})
    report['app_sessions'], report['rejected_app_exports'] = read_app_sessions(adb, receipt)
    receipt['app_sessions'] = report['app_sessions']; receipt['last_status'] = report; save(path, receipt)
    return report


def watch(args) -> dict:
    """Host supervisor; every on-device component remains duration/size bounded.

    Disconnection is explicit evidence, not permission to start duplicate windows.
    A command failure ends supervision; already-running device work remains bounded.
    """
    path,receipt,adb=load(args)
    if receipt['state']!='CAPTURING_MANUAL_SESSION':raise ValueError('Session is not active')
    deadline=receipt['light_start']['deadline_uptime_s']
    start_up=receipt['light_start']['start_uptime_s']
    planned=receipt['scheduled_windows']
    seconds=receipt['heavy_seconds']
    host_fence=time.monotonic()+receipt['duration_seconds']+30
    report={'started_utc':utc(),'session':receipt['session'],'schedule':receipt['schedule'],
            'planned_windows':planned,'windows_started_before':len(receipt['windows']),
            'limits':'At most5x(native32MiB+CPU32MiB)+light32MiB+selectedapp128MiB =480MiB plus compact receipts',
            'gaps':[],'completed':False}
    try:
        while time.monotonic()<host_fence:
            with control_lock(path):
                receipt=json.loads(path.read_text(encoding='utf-8-sig'))
                if receipt['state']!='CAPTURING_MANUAL_SESSION':break
            now=float(adb.shell('cat','/proc/uptime').split()[0])
            if now>=deadline:break
            if remote_absent(adb.attempt('test','-d','/proc/'+str(receipt['light']['pid']))):break
            if len(receipt['windows'])<planned and seconds:
                prior=receipt['windows'][-1] if receipt['windows'] else None
                active=False
                if prior:
                    for kind in ('native','cpu'):
                        owner=prior.get(kind,{})
                        if 'pid' in owner and inspect_process(adb,owner['pid'],kind,owner,receipt['session']) is not None:
                            active=True
                index=len(receipt['windows'])
                offset=window_offset(receipt['duration_seconds'],seconds,planned,index,receipt['schedule'])
                if not active and now>=start_up+offset and deadline-now>=seconds+5:
                    if current_pid(adb) is None:
                        if not report['gaps'] or report['gaps'][-1].get('reason')!='app_process_absent':
                            report['gaps'].append({'utc':utc(),'reason':'app_process_absent','next_window_index':index})
                        time.sleep(min(5,max(.1,deadline-now)))
                        continue
                    with control_lock(path):
                        receipt=json.loads(path.read_text(encoding='utf-8-sig'))
                        if receipt['state']!='CAPTURING_MANUAL_SESSION':break
                        if len(receipt['windows'])!=index:continue
                        start_window(adb,receipt,path.parent,seconds)
                        print(json.dumps({'event':'heavy_window_started','number':len(receipt['windows']),'target_pid':receipt['windows'][-1]['target_pid']},ensure_ascii=False),flush=True)
            time.sleep(min(5,max(.1,deadline-now)))
        report['completed']=True
    except Exception as error:
        report['gaps'].append({'utc':utc(),'error_type':type(error).__name__,'error':str(error)})
        raise
    finally:
        report['finished_utc']=utc();report['windows_started_after']=len(receipt['windows'])
        # Do not overwrite a stop command's newer receipt/state.
        with control_lock(path):
            current=json.loads(path.read_text(encoding='utf-8-sig'))
            current['watch_report']=report;save(path,current)
        save(path.parent/('watch-'+uuid.uuid4().hex[:12]+'.json'),report)
    return report


def stop(args) -> dict:
    path, receipt, adb = load(args)
    receipt['state']='STOPPING_OWNED_COLLECTORS';save(path,receipt)
    report = {'utc': utc(), 'session': receipt['session'], 'stopped': [], 'errors': []}
    try:
        if receipt.get('light_launch_attempted'):adb.shell('touch',receipt['remote_base']+'/stop')
        recover_light_owner(adb,receipt)
    except Exception as error:report['errors'].append({'kind':'light_start_recovery','error':str(error)})
    try:owned_apps,rejected=read_app_sessions(adb,receipt)
    except Exception as error:
        owned_apps=[];rejected=[{'reason':'ExportDirectoryUnavailable','detail':str(error)}]
    receipt['app_sessions'] = owned_apps
    report['rejected_app_exports'] = rejected
    for item in owned_apps:
        if not getattr(args,'stop_app',True):break
        session=item['header']['session']
        result=adb.attempt('am', 'broadcast', '-a', PACKAGE+'.resourceprofiler.STOP', '--es', 'session', session)
        report.setdefault('app_stop_broadcasts', []).append({'session': session, 'returncode': result.returncode,
                                                           'acknowledgement': 'app_end must still be verified during pull'})
    # Each independent owned stop is attempted; never kill/pkill by a program name.
    for w in receipt['windows']:
        try:recover_window_owners(adb,w,receipt['session'])
        except Exception as error:report['errors'].append({'window':w['id'],'error':str(error)})
        for kind in ('cpu', 'native'):
            owned=w.get(kind, {})
            if 'pid' not in owned: continue
            try:
                result=stop_process(adb, kind, owned, receipt['session'])
                report['stopped'].append({'kind': kind, 'window': w['id'], 'result': result})
            except Exception as error:
                report['errors'].append({'kind': kind, 'window': w['id'], 'error': str(error)})
    if receipt.get('light'):
        try:
            owned=receipt['light']
            if inspect_process(adb, owned['pid'], 'light', owned, receipt['session']) is not None:
                adb.shell('touch', receipt['remote_base']+'/stop')
                until=time.monotonic()+7
                while time.monotonic()<until:
                    if inspect_process(adb, owned['pid'], 'light', owned, receipt['session']) is None: break
                    time.sleep(.25)
                else: stop_process(adb, 'light', owned, receipt['session'])
            report['stopped'].append({'kind':'light','result':'owned-stop-and-absence-checked'})
        except Exception as error: report['errors'].append({'kind':'light','error':str(error)})
    receipt['stop_report']=report
    receipt['state']='STOP_INCOMPLETE_RETAIN_REMOTE' if report['errors'] else 'OWNED_EXTERNAL_COLLECTORS_STOPPED'
    save(path, receipt)
    if report['errors']: raise RuntimeError('Some owned collectors could not be stopped; see receipt')
    return report


def pull_file(adb: Adb, remote: str, local: Path, cap: int) -> dict:
    if local.exists(): raise FileExistsError('Never overwrite an earlier pulled artifact')
    first=int(adb.shell('stat','-c','%s',remote)); time.sleep(.1)
    second=int(adb.shell('stat','-c','%s',remote))
    if first!=second or first<0 or first>cap: raise RuntimeError('Source is changing or exceeds known bound')
    digest=file_sha(adb,remote)
    adb.invoke('pull',remote,str(local),timeout=90)
    if local.stat().st_size!=first or sha256(local)!=digest or file_sha(adb,remote)!=digest:
        raise RuntimeError('Artifact transfer/hash mismatch; retain original')
    return {'remote':remote,'local':str(local),'bytes':first,'sha256':digest,'cap_reached':first==cap}


def pull_light(adb: Adb, base: str, output: Path) -> list[dict]:
    """One bounded directory pull, with exact before/after hashes, no450 roundtrips."""
    names=adb.shell('ls','-1',base+'/light').splitlines()
    if len(names)>1000:raise RuntimeError('Unexpected number of light sampler artifacts')
    valid=re.compile(r'(?:cpu-\d{4}\.txt|memory-\d{4}\.txt|owner\.pid|owner-stat\.raw|start\.json|heartbeat\.json|exit\.json)')
    if not names or any(not valid.fullmatch(n) for n in names):
        raise RuntimeError('Unknown light directory contents retained; no blanket pull')
    paths=[base+'/light/'+n for n in names]
    def hashes():
        found={}
        for i in range(0,len(paths),24):
            text=adb.shell('sha256sum',*paths[i:i+24])
            for line in text.splitlines():
                match=re.fullmatch(r'([0-9a-f]{64})\s+(.+)',line)
                if not match or match.group(2) not in paths:raise RuntimeError('Unknown light hash record')
                if match.group(2) in found:raise RuntimeError('Duplicate light hash record')
                found[match.group(2)]=match.group(1)
        if set(found)!=set(paths):raise RuntimeError('Light manifest is incomplete')
        return found
    before=hashes()
    local=output/'light'
    if local.exists():raise FileExistsError('Light destination exists')
    adb.invoke('pull',base+'/light',str(local),timeout=120)
    total=0;artifacts=[]
    if sorted(p.name for p in local.iterdir())!=sorted(names):raise RuntimeError('Light transfer file roster differs')
    for name,remote in zip(names,paths):
        p=local/name
        if not p.is_file() or p.is_symlink():raise RuntimeError('Unexpected local light file type')
        size=p.stat().st_size;total+=size
        if size>131072 or total>MAX_LIGHT_BYTES:raise RuntimeError('Light data exceeds its known bound')
        if sha256(p)!=before[remote]:raise RuntimeError('Light file transfer mismatch')
        artifacts.append({'remote':remote,'local':str(p),'bytes':size,'sha256':before[remote],'cap_reached':False})
    if hashes()!=before:raise RuntimeError('Light source changed during transfer')
    return artifacts


def pull(args) -> dict:
    path,receipt,adb=load(args)
    if receipt.get('state') not in ('OWNED_EXTERNAL_COLLECTORS_STOPPED','PULLED_RETAINED_REMOTE','PULL_INCOMPLETE_RETAIN_REMOTE'):
        raise RuntimeError('Stop exact owned external collectors before final pull')
    if len(receipt.get('pulls',[]))>=MAX_PULLS:
        raise RuntimeError('Two immutable pull attempts already retained; review them before creating more bulk copies')
    if shutil.disk_usage(path.anchor).free < 30*1024**3:
        raise RuntimeError('Less than30GiB free on Q; retain remote evidence and review storage before pull')
    # A pull creates a new immutable snapshot directory; prior partial pulls survive.
    pull_id=uuid.uuid4().hex[:12]
    output=path.parent/('pull-'+pull_id); output.mkdir()
    report={'utc':utc(),'session':receipt['session'],'output':str(output),'artifacts':[],
            'errors':[],'app_snapshots':[],'remote_cleanup':'NOT_PERFORMED_RETAINED_UNTIL_REVIEW'}
    save(output/'pull-receipt.json',report)
    receipt['pulls']=receipt.get('pulls',[])+[str(output/'pull-receipt.json')]
    receipt['state']='PULL_INCOMPLETE_RETAIN_REMOTE';save(path,receipt)
    try:
        pull_contents(adb,receipt,output,report)
    except BaseException as error:
        report['errors'].append({'stage':'pull_aborted','error_type':type(error).__name__,'error':str(error)})
        raise
    finally:
        report['complete_transfer']=not report['errors']
        report['data_quality_errors']=[{'remote':a['remote'],'reason':'empty_or_size_cap_reached'}
                                       for a in report['artifacts']
                                       if a['remote'].endswith(('.pftrace','.data')) and (a['bytes']==0 or a['cap_reached'])]
        report['native_and_CPU_quality']='NOT_ANALYZED: retained traces need data-loss/coverage/PID/symbol checks; transfer alone is not profile acceptance'
        save(output/'pull-receipt.json',report)
        receipt['state']='PULLED_RETAINED_REMOTE' if report['complete_transfer'] else 'PULL_INCOMPLETE_RETAIN_REMOTE'
        save(path,receipt)
    return report


def pull_contents(adb: Adb, receipt: dict, output: Path, report: dict) -> None:
    def collect(remote,name,cap):
        try: report['artifacts'].append(pull_file(adb,remote,output/name,cap))
        except Exception as error: report['errors'].append({'remote':remote,'error':str(error)})
        finally:save(output/'pull-receipt.json',report)
    for w in receipt['windows']:
        collect(w['native']['config'],w['id']+'-config.pbtxt',8192)
        collect(w['native']['trace'],w['id']+'-native.pftrace',MAX_TRACE_BYTES)
        collect(w['cpu']['output'],w['id']+'-cpu.data',MAX_TRACE_BYTES)
        if w['cpu'].get('log'): collect(w['cpu']['log'],w['id']+'-cpu.log',65536)
        if w['native'].get('log'):collect(w['native']['log'],w['id']+'-native.log',65536)
    base=receipt['remote_base']
    try:report['artifacts'].extend(pull_light(adb,base,output))
    except Exception as error:report['errors'].append({'remote':base+'/light','error':str(error)})
    owned_apps,rejected=read_app_sessions(adb,receipt)
    report['rejected_app_exports']=rejected
    total=0
    for item in owned_apps:
        total+=item['size']
        if total>MAX_APP_BYTES: raise RuntimeError('Selected app exports exceed session aggregate bound')
        # No remote app duplicates: a completed export transfers directly. An
        # active export is accepted only if its size/hash remains stable through
        # transfer; otherwise its partial local evidence is retained as failure.
        local=output/item['filename']
        try:
            artifact=pull_file(adb,item['path'],local,MAX_APP_FILE_BYTES)
            report['artifacts'].append(artifact)
            lines=local.read_text(encoding='utf-8').splitlines(); events=[];partial=False
            for i,line in enumerate(lines):
                try: events.append(json.loads(line))
                except json.JSONDecodeError:
                    if i!=len(lines)-1: raise ValueError('Malformed interior app JSONL record')
                    partial=True
            header=validate_app_header(item['filename'],lines[0],receipt['source_revision'])
            if any(row.get('session')!=header['session'] for row in events): raise ValueError('Mixed app sessions')
            quality=app_footer_quality(header,events,partial)
            report['app_snapshots'].append({'session':header['session'],'pid':header['pid'],
                                           'records':len(events),**quality,
                                           'truncated_last_line':partial,'source':item['path'],
                                           'qualification':'Snapshot of selected private app session; app_end required for a completed app journal.'})
        except Exception as error: report['errors'].append({'remote':item['path'],'error':str(error)})
        finally:save(output/'pull-receipt.json',report)


def main() -> int:
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb',type=Path,required=True)
    sub=parser.add_subparsers(dest='command',required=True)
    p=sub.add_parser('start');p.add_argument('--serial',required=True);p.add_argument('--output',type=Path,required=True)
    p.add_argument('--apk-receipt',type=Path,required=True);p.add_argument('--source-revision',required=True)
    p.add_argument('--duration',type=int,default=1800);p.add_argument('--heavy-seconds',type=int,default=180)
    p.add_argument('--windows',type=int,choices=range(1,MAX_WINDOWS+1),default=MAX_WINDOWS)
    p.add_argument('--schedule',choices=('continuous','spread'),default='spread')
    p.add_argument('--cpu-callgraph',choices=('fp','dwarf,8192'),default='fp')
    p.add_argument('--cold-start',action='store_true');p.add_argument('--confirm-idle-no-editor',action='store_true')
    for name in ('status','stop','pull','window','watch'):
        p=sub.add_parser(name);p.add_argument('--receipt',type=Path,required=True)
        if name=='window':p.add_argument('--seconds',type=int,default=180)
    args=parser.parse_args()
    if args.command in ('start','watch'):result=globals()[args.command](args)
    else:
        with control_lock(args.receipt.absolute()):
            if args.command=='window':
                path,receipt,adb=load(args)
                if receipt['state']!='CAPTURING_MANUAL_SESSION':raise ValueError('Session is not active')
                result=start_window(adb,receipt,path.parent,args.seconds)
            else:result=globals()[args.command](args)
    print(json.dumps(result,ensure_ascii=False,indent=2));return 0


if __name__=='__main__':raise SystemExit(main())
