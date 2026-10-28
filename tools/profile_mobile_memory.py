"""Bounded ADB profiling of an explicitly selected, already open production project.

Never installs, clears, edits feature data or starts an emulator. An explicitly
requested mock GPS is temporary and its restoration requires readback evidence.
Pan/zoom controls must be chosen from the observed map screen by the operator.
"""
from __future__ import annotations
import argparse
from datetime import datetime, timezone
import json
import math
from pathlib import Path
import re
import subprocess
import time

PACKAGE = 'com.nextgis.mobile.geonical'


def parse_memory(text: str) -> dict:
    """Read reported columns, never infer RSS from a fixed PSS-column offset.

    --local commonly prints zero placeholders for all three allocator columns.
    Those are unavailable measurements, even when resident memory is nonzero.
    Unknown layouts/columns stay absent rather than becoming invented zeroes.
    """
    text = text.replace('\r', '')
    out = {}
    lines = [line for line in text.splitlines() if line.strip()]
    columns = None
    for index, line in enumerate(lines[:-1]):
        upper, lower = line.split(), lines[index+1].split()
        if ('Pss' in upper and 'Heap' in upper and 'Size' in lower
                and len(upper) == len(lower)):
            columns = list(zip(upper, lower))
            break
    for label, key in [('Native Heap', 'native'), ('Dalvik Heap', 'java')]:
        row = re.search(r'^\s*' + label + r'[ \t]+([0-9]+(?:[ \t]+[0-9]+)*)[ \t]*$', text, re.M)
        if row:
            numbers = [int(x) for x in row.group(1).split()]
            if columns is not None and len(numbers) == len(columns):
                values = dict(zip(columns, numbers))
                for column, suffix in ((('Pss', 'Total'), '_pss_kib'), (('Rss', 'Total'), '_rss_kib')):
                    if column in values: out[key+suffix] = values[column]
                heap = [values.get(('Heap', name)) for name in ('Size', 'Alloc', 'Free')]
                available = all(value is not None for value in heap) and any(heap)
                out[key+'_heap_counters_available'] = bool(available)
                if available:
                    for suffix, value in zip(('_heap_size_kib', '_allocated_kib', '_heap_free_kib'), heap):
                        out[key+suffix] = value
    total = re.search(r'^\s*TOTAL[ \t]+([0-9]+(?:[ \t]+[0-9]+)*)[ \t]*$', text, re.M)
    if total and columns is not None:
        numbers = [int(value) for value in total.group(1).split()]
        if len(numbers) == len(columns):
            values = dict(zip(columns, numbers))
            for column, key in ((('Pss', 'Total'), 'total_pss_kib'), (('Rss', 'Total'), 'total_rss_kib')):
                if column in values: out[key] = values[column]
    for label, key in [('TOTAL PSS', 'total_pss_kib'), ('TOTAL RSS', 'total_rss_kib'),
                       ('Graphics', 'graphics_pss_kib'), ('MEMORY_USED', 'sqlite_kib')]:
        value = re.search(r'\b'+label+r':\s*(\d+)', text)
        if value: out[key] = int(value.group(1))
    value = re.search(r'^\s*Bitmap \(malloced\):\s+(\d+)\s+(\d+)', text, re.M)
    if value: out['bitmap_count'], out['bitmap_malloc_kib'] = map(int, value.groups())
    return out


def mock_appop_mode(text: str) -> str:
    modes = re.findall(r'(?i)\b(?:MOCK_LOCATION|mock_location):\s*(allow|deny|ignore|default|foreground)\b', text)
    if modes and len(set(value.lower() for value in modes)) == 1: return modes[0].lower()
    if re.search(r'(?i)\bNo operations\b', text): return 'default'
    raise ValueError('Mock-location appop mode is missing or ambiguous')


def gps_is_mocked(text: str) -> bool:
    match = re.search(r'(?im)^\s*gps provider:', text)
    if not match: raise ValueError('GPS provider state is unavailable')
    block = text[match.end():]
    next_provider = re.search(r'(?im)^\s*[a-z][a-z0-9_-]* provider:', block)
    if next_provider: block = block[:next_provider.start()]
    if re.search(r'(?i)\b(?:mock|ismock)\s*=\s*true\b', block): return True
    provider = re.search(r'(?i)\bmock provider\s*=\s*(\S+)', block)
    return bool(provider and provider.group(1).lower() not in ('null', 'none', 'false'))


def restore_mock(shell, state: dict, created: bool, original_mode: str) -> None:
    """Try both restorations independently and require their actual readback."""
    errors = {}
    if created:
        try:
            shell('cmd','location','providers','remove-test-provider','gps')
            if gps_is_mocked(shell('dumpsys','location')):
                raise RuntimeError('GPS still reports a mock provider after removal')
            state['provider_cleanup'] = 'removed-and-absence-verified'
        except Exception as error: errors['provider'] = str(error)
    elif state.get('provider_add_attempted'):
        errors['provider'] = 'Provider creation was attempted but not confirmed; ownership/state require inspection'
    else: state['provider_cleanup'] = 'not-created'
    try:
        shell('cmd','appops','set','com.android.shell','android:mock_location',original_mode)
        readback = shell('cmd','appops','get','com.android.shell','android:mock_location')
        state['appop_readback'] = readback
        if mock_appop_mode(readback) != original_mode:
            raise RuntimeError('Mock-location appop readback differs from the original mode')
        state['appop_cleanup'] = 'original-mode-verified'
    except Exception as error: errors['appop'] = str(error)
    state['cleanup'] = 'failed' if errors else 'restored-and-readback-verified'
    if errors:
        state['cleanup_errors'] = errors
        raise RuntimeError('Mock GPS restoration requires attention: '+', '.join(errors))


def arguments(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb', required=True); parser.add_argument('--serial', required=True)
    parser.add_argument('--project', type=int, required=True); parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--stage', choices=['idle', 'pan', 'zoom'], default='idle')
    parser.add_argument('--seconds', type=int, default=90); parser.add_argument('--ceiling-mib', type=int, default=2800)
    parser.add_argument('--pan-rect', help='Observed safe map rectangle x1,y1,x2,y2')
    parser.add_argument('--zoom-controls', help='Observed + then - button centres x1,y1,x2,y2')
    parser.add_argument('--zoom-out-first', action='store_true')
    parser.add_argument('--mock-centre', help='Explicitly authorized temporary GPS centre latitude,longitude; always restored')
    parser.add_argument('--centre-on-mock', help='Observed map location button x,y, tapped once after test fixes arrive')
    args = parser.parse_args(argv)
    if not 10 <= args.seconds <= 600: parser.error('Duration must be 10..600 seconds')
    if args.project < 1 or not 256 <= args.ceiling_mib <= 8192: parser.error('Invalid project or memory ceiling')
    if not re.fullmatch(r'[A-Za-z0-9_.:-]+', args.serial): parser.error('Invalid explicit device serial')
    coordinates = args.pan_rect if args.stage == 'pan' else args.zoom_controls
    try: args.points = [int(value) for value in coordinates.split(',')] if coordinates else []
    except ValueError: parser.error('Observed map coordinates must be integers')
    if args.stage != 'idle' and (len(args.points) != 4 or min(args.points) < 0
                               or args.points[:2] == args.points[2:]):
        parser.error('Four distinct observed map/control coordinates required')
    if args.centre_on_mock and not args.mock_centre: parser.error('--centre-on-mock requires --mock-centre')
    try:
        args.centre_points = [int(value) for value in args.centre_on_mock.split(',')] if args.centre_on_mock else []
        args.mock_point = [float(value) for value in args.mock_centre.split(',')] if args.mock_centre else []
    except ValueError: parser.error('Invalid explicit mock/map coordinates')
    if args.centre_on_mock and (len(args.centre_points) != 2 or min(args.centre_points) < 0):
        parser.error('Two observed location-button coordinates required')
    if args.mock_centre and (len(args.mock_point) != 2 or not -85 <= args.mock_point[0] <= 85
                            or not -180 <= args.mock_point[1] <= 180):
        parser.error('Invalid mock GPS centre')
    if args.output.exists(): parser.error('Use a new evidence directory; existing evidence is immutable')
    return args


def main(argv=None) -> int:
    args = arguments(argv)
    args.output.mkdir(parents=True)
    def run(*words, binary=False):
        result = subprocess.run([args.adb, '-s', args.serial, *words], capture_output=True, timeout=30)
        if result.returncode: raise RuntimeError(result.stderr.decode('utf-8', 'replace')[:1000])
        return result.stdout if binary else result.stdout.decode('utf-8', 'replace').strip()
    def shell(*words): return run('shell', *words)
    def foreground():
        focused = [line for line in shell('dumpsys', 'activity', 'activities').splitlines()
                   if 'topResumedActivity=' in line or 'mResumedActivity=' in line]
        if not any(PACKAGE+'/com.nextgis.mobile.activity.MainActivity' in line for line in focused):
            raise RuntimeError('The selected application/map is no longer foreground; stopping input')
    points = args.points
    hardware = shell('getprop', 'ro.hardware')
    if hardware in ('ranchu', 'goldfish'): raise RuntimeError('This profiler requires a physical device')
    foreground()
    root = '/sdcard/Android/data/'+PACKAGE+'/files/map/collector_projects'
    selected = []
    for directory in shell('ls', root).splitlines():
        if not re.fullmatch(r'collector_'+str(args.project)+r'_[A-Za-z0-9_-]+', directory): continue
        data = json.loads(shell('cat', root+'/'+directory+'/project.json'))
        if data.get('project_remote_id') == args.project: selected.append(directory)
    if len(selected) != 1: raise RuntimeError('The explicitly selected project is missing or ambiguous')
    original_pid = shell('pidof', PACKAGE)
    if not original_pid.isdecimal(): raise RuntimeError('Expected one running application process')
    state = {'project_id': args.project, 'project_directory': selected[0], 'stage': args.stage,
             'serial': args.serial, 'pid': int(original_pid), 'hardware': hardware,
             'started_utc': datetime.now(timezone.utc).isoformat(), 'status': 'running', 'actions': 0}
    observations = []
    mock_created = False; mock_operation = None
    began = time.monotonic(); next_sample = began
    try:
        if args.mock_centre:
            latitude, longitude = args.mock_point
            location = shell('dumpsys', 'location')
            # Do not replace somebody else's active mock provider.
            if gps_is_mocked(location):
                raise RuntimeError('GPS already has a mock provider; not replacing another owner')
            operation = shell('cmd','appops','get','com.android.shell','android:mock_location')
            mock_operation = mock_appop_mode(operation)
            state['mock_gps'] = {'original_shell_appop': mock_operation, 'provider': 'gps', 'updates': 0,
                                 'cleanup': 'pending', 'centre': [latitude, longitude]}
            (args.output/'receipt.json').write_text(json.dumps(state,ensure_ascii=False,indent=2),encoding='utf-8')
            shell('cmd','appops','set','com.android.shell','android:mock_location','allow')
            state['mock_gps']['provider_add_attempted'] = True
            shell('cmd','location','providers','add-test-provider','gps','--requiresSatellite','--supportsAltitude','--supportsSpeed','--supportsBearing')
            mock_created = True
            state['mock_gps']['provider_added'] = True
            (args.output/'receipt.json').write_text(json.dumps(state,ensure_ascii=False,indent=2),encoding='utf-8')
            shell('cmd','location','providers','set-test-provider-enabled','gps','true')
        while time.monotonic() - began < args.seconds:
            foreground()
            if shell('pidof', PACKAGE) != original_pid: raise RuntimeError('Application exited/restarted; original PID evidence retained')
            if time.monotonic() >= next_sample:
                text = shell('dumpsys', 'meminfo', original_pid)
                # Verify the expected project is open, without reading any feature/account data.
                if args.stage != 'idle' and not observations and '/'+selected[0]+'/layers.db' not in text:
                    raise RuntimeError('Selected project database is not open in the current process')
                record = {'elapsed_s': round(time.monotonic()-began, 2), 'utc': datetime.now(timezone.utc).isoformat(), **parse_memory(text)}
                if 'total_rss_kib' not in record or not {'native_rss_kib','native_pss_kib'} & record.keys():
                    raise RuntimeError('Incomplete Android resident/proportional memory counters')
                observations.append(record)
                (args.output/('meminfo-%03d.txt'%len(observations))).write_text(text, encoding='utf-8')
                with (args.output/'samples.jsonl').open('a', encoding='utf-8') as stream: stream.write(json.dumps(record)+'\n')
                print(json.dumps(record), flush=True)
                if record['total_rss_kib'] >= args.ceiling_mib*1024: raise RuntimeError('Profiling memory ceiling reached; no further gestures')
                next_sample = time.monotonic()+5
            if mock_created:
                angle = (time.monotonic()-began)/12
                point = '%.7f,%.7f'%(latitude+math.sin(angle)*.00005,longitude+math.cos(angle)*.00005)
                shell('cmd','location','providers','set-test-provider-location','gps','--location',point,'--accuracy','5')
                state['mock_gps']['updates'] += 1
                if state['mock_gps']['updates'] == 3 and args.centre_on_mock:
                    x, y = args.centre_points
                    shell('input','tap',str(x),str(y))
                if state['mock_gps']['updates'] in (8, 30):
                    (args.output/('mock-map-%02d.png'%state['mock_gps']['updates'])).write_bytes(run('exec-out','screencap','-p',binary=True))
            if args.stage == 'pan':
                x1,y1,x2,y2 = points
                if state['actions'] % 2: x1,y1,x2,y2 = x2,y2,x1,y1
                shell('input','swipe',str(x1),str(y1),str(x2),str(y2),'350'); state['actions']+=1
                time.sleep(.7)
            elif args.stage == 'zoom':
                # Alternating real +/- controls returns to the same scale, avoiding remote extent drift.
                index = 0 if (state['actions']//3) % 2 == 0 else 2
                if args.zoom_out_first: index = 2-index
                shell('input','tap',str(points[index]),str(points[index+1])); state['actions']+=1
                time.sleep(1.3)
            else: time.sleep(1)
        state['status'] = 'completed'
    except (Exception, KeyboardInterrupt) as failure:
        state['status'] = 'stopped'; state['error'] = str(failure) or type(failure).__name__
        print('STOP '+state['error'], flush=True)
    finally:
        if mock_operation is not None:
            try:
                restore_mock(shell,state['mock_gps'],mock_created,mock_operation)
            except Exception as failure:
                state['mock_gps']['cleanup'] = 'failed'; state['mock_gps']['cleanup_error'] = str(failure)
                state['status'] = 'stopped'; state['error'] = 'Mock GPS restoration requires attention'
        state['elapsed_s'] = round(time.monotonic()-began, 2)
        state['sample_count'] = len(observations)
        if observations: state['first'], state['last'] = observations[0], observations[-1]
        (args.output/'receipt.json').write_text(json.dumps(state, ensure_ascii=False, indent=2), encoding='utf-8')
    return 0 if state['status'] == 'completed' else 1


if __name__ == '__main__': raise SystemExit(main())
