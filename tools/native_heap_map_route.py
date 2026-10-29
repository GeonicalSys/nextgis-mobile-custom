"""Observed, bounded map-only gesture route during an already owned native heap capture.

No settings, feature edits, installations or process termination. Stops on loss of
the original map process/foreground/capture. Coordinates must be supplied after
inspection of the current device screen. This is diagnostic workload, not Full QA.
"""
from __future__ import annotations
import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import time
from profile_native_heap import Adb, PACKAGE, foreground, process_pid, owns_perfetto


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb', type=Path, required=True); parser.add_argument('--serial', required=True)
    parser.add_argument('--capture', type=Path, required=True)
    parser.add_argument('--map-rect', required=True); parser.add_argument('--zoom-controls', required=True)
    args = parser.parse_args()
    receipt = json.loads(args.capture.read_text(encoding='utf-8'))
    if receipt.get('status') != 'active' or receipt.get('serial') != args.serial or receipt.get('project_id') != 787:
        raise ValueError('An active owned project787 capture is required')
    rectangle = [int(v) for v in args.map_rect.split(',')]
    zoom = [int(v) for v in args.zoom_controls.split(',')]
    if len(rectangle) != 4 or len(zoom) != 4 or min(rectangle+zoom) < 0:
        raise ValueError('Four observed coordinates per rectangle/control pair are required')
    adb = Adb(args.adb, args.serial); actions = []
    result = {'created_utc':datetime.now(timezone.utc).isoformat(), 'status':'active',
              'project_id':787,'package':PACKAGE,'pid':receipt['app_pid'],'actions':actions,
              'scope':'Diagnostic map route; actual camera/pixel acceptance remains separate'}
    output = args.capture.parent/'map-route.json'
    def guard():
        foreground(adb)
        if process_pid(adb) != receipt['app_pid']: raise RuntimeError('Original application PID changed')
        command = adb.shell('cat','/proc/'+str(receipt['perfetto_pid'])+'/cmdline')
        if not owns_perfetto(command, receipt['remote_config'],receipt['remote_trace']):
            raise RuntimeError('Owned profile capture has ended')
    def act(*words):
        guard(); adb.shell('input',*words)
        actions.append({'utc':datetime.now(timezone.utc).isoformat(),'input':list(words)})
        output.write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
        time.sleep(1.2)
    try:
        # Wider view, then new areas around a square, then return to original scale.
        for _ in range(3): act('tap',str(zoom[2]),str(zoom[3]))
        left,top,right,bottom=rectangle; cx=(left+right)//2; cy=(top+bottom)//2
        for segment in [(right,cy,left,cy),(cx,bottom,cx,top),(left,cy,right,cy),(cx,top,cx,bottom)]:
            for _ in range(8): act('swipe',*map(str,segment),'350')
        for _ in range(3): act('tap',str(zoom[0]),str(zoom[1]))
        result['status']='completed'
    except Exception as error:
        result['status']='stopped'; result['error']=str(error)
    finally:
        result['finished_utc']=datetime.now(timezone.utc).isoformat()
        output.write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps({'status':result['status'],'actions':len(actions),'output':str(output)}))
    return 0 if result['status']=='completed' else 1


if __name__=='__main__': raise SystemExit(main())
