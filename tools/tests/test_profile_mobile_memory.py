import contextlib
import io
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from profile_mobile_memory import PACKAGE, arguments, gps_is_mocked, main, mock_appop_mode, parse_memory, restore_mock


# Independent literal Android layouts. Deliberately different PSS, private dirty,
# swap and RSS numbers make accidental fixed-column shifts observable.
FULL = '''Applications Memory Usage (in Kilobytes):
** MEMINFO in pid 14577 [com.nextgis.mobile.geonical] **
                   Pss  Private  Private  SwapPss      Rss     Heap     Heap     Heap
                 Total    Dirty    Clean    Dirty    Total     Size    Alloc     Free
                ------   ------   ------   ------   ------   ------   ------   ------
  Native Heap  1793166  1793164        0   124711  1795104  2221456  1800464   416149
  Dalvik Heap   105673   102236     3356     1033   108544   145571    96419    49152
        TOTAL  2250749  2084704    33228   126799  2246968  2367027  1896883   465301
 App Summary
                       Pss(KB)                        Rss(KB)
         Native Heap:  1793164                        1795104
            Graphics:   176624                         176624
           TOTAL PSS:  2250749            TOTAL RSS:  2246968       TOTAL SWAP PSS: 126799
 MEMORY_USED: 32123
 Bitmap (malloced): 12 54321
'''

LOCAL = '''Applications Memory Usage (in Kilobytes):
                   Pss  Private  Private  SwapPss      Rss     Heap     Heap     Heap
                 Total    Dirty    Clean    Dirty    Total     Size    Alloc     Free
                ------   ------   ------   ------   ------   ------   ------   ------
  Native Heap    57828    57784       24       55    59800        0        0        0
  Dalvik Heap    81731    81716        0     1599    84456        0        0        0
        TOTAL   282776   246560    30112     1687   400324        0        0        0
 App Summary
                       Pss(KB)                        Rss(KB)
         Native Heap:    57784                          59800
           TOTAL PSS:   282776            TOTAL RSS:   400324       TOTAL SWAP PSS: 1687
'''

OLD_NO_RSS = '''
                   Pss   Shared   Shared  Private  Private  Swapped     Heap     Heap     Heap
                 Total    Dirty    Clean    Dirty    Clean    Dirty     Size    Alloc     Free
                ------   ------   ------   ------   ------   ------   ------   ------   ------
  Native Heap      100        1        2       10       20        3     1200      900      300
  Dalvik Heap      200        4        5       30       40        6     2400     1800      600
        TOTAL      400        7        8       70       80        9     3600     2700      900
'''

SEVEN_COLUMNS = '''
                   Pss  Private  Private  SwapPss     Heap     Heap     Heap
                 Total    Dirty    Clean    Dirty     Size    Alloc     Free
  Native Heap     1000      950       40       10     4096     3000     1096
'''

GPS_REAL = '''Location Manager State:
  gps provider:
    mock provider=null
    real provider=GnssLocationProvider
  network provider:
    mock provider=another-owner
'''


class Clock:
    def __init__(self): self.value = 100.0
    def now(self): return self.value
    def sleep(self, seconds): self.value += seconds


class FakeAdb:
    def __init__(self, clock, memory=FULL, restart_on_input=False, leave_on_input=False, project_visible=True):
        self.clock=clock; self.memory=memory; self.calls=[]; self.pid='14577'; self.restart_on_input=restart_on_input
        self.leave_on_input=leave_on_input; self.project_visible=project_visible; self.in_foreground=True

    def __call__(self, command, **kwargs):
        words=command[3:]; self.calls.append(words)
        if words[:3]==['shell','getprop','ro.hardware']: text='s5e8845'
        elif words[:4]==['shell','dumpsys','activity','activities']:
            text='topResumedActivity=ActivityRecord{ '+(PACKAGE+'/com.nextgis.mobile.activity.MainActivity' if self.in_foreground else 'other.app/.Screen')+'}'
        elif words[:2]==['shell','ls']: text='collector_787_fixture'
        elif words[:2]==['shell','cat']: text='{"project_remote_id":787}'
        elif words[:2]==['shell','pidof']: text=self.pid
        elif words[:3]==['shell','dumpsys','meminfo']:
            text=self.memory
            if self.project_visible:text+='\n/sdcard/Android/data/'+PACKAGE+'/files/map/collector_projects/collector_787_fixture/layers.db\n'
        elif words[:2]==['shell','input']:
            text=''; self.clock.sleep(.35 if words[2]=='swipe' else .1)
            if self.restart_on_input:self.pid='9999'
            if self.leave_on_input:self.in_foreground=False
        else:raise AssertionError('Unexpected device operation: '+repr(words))
        return subprocess.CompletedProcess(command,0,text.encode(),b'')


class MemoryParserTest(unittest.TestCase):
    def test_full_pss_rss_and_allocator_columns_are_distinct(self):
        result=parse_memory(FULL)
        self.assertEqual(1793166,result['native_pss_kib'])
        self.assertEqual(1795104,result['native_rss_kib'])
        self.assertEqual(1800464,result['native_allocated_kib'])
        self.assertEqual(2221456,result['native_heap_size_kib'])
        self.assertEqual(416149,result['native_heap_free_kib'])
        self.assertEqual(108544,result['java_rss_kib']);self.assertEqual(96419,result['java_allocated_kib'])
        self.assertEqual(2250749,result['total_pss_kib']);self.assertEqual(2246968,result['total_rss_kib'])
        self.assertEqual(176624,result['graphics_pss_kib']);self.assertEqual(32123,result['sqlite_kib'])
        self.assertEqual(12,result['bitmap_count']);self.assertEqual(54321,result['bitmap_malloc_kib'])

    def test_local_zero_allocator_placeholders_are_unknown_not_zero_heap(self):
        result=parse_memory(LOCAL)
        self.assertEqual(57828,result['native_pss_kib']);self.assertEqual(59800,result['native_rss_kib'])
        self.assertEqual(84456,result['java_rss_kib']);self.assertEqual(400324,result['total_rss_kib'])
        for kind in ('native','java'):
            self.assertFalse(result[kind+'_heap_counters_available'])
            for name in ('allocated_kib','heap_size_kib','heap_free_kib'):self.assertNotIn(kind+'_'+name,result)

    def test_legacy_layout_has_allocator_measurements_but_does_not_invent_rss(self):
        result=parse_memory(OLD_NO_RSS)
        self.assertEqual(100,result['native_pss_kib']);self.assertEqual(900,result['native_allocated_kib'])
        self.assertEqual(400,result['total_pss_kib'])
        self.assertNotIn('native_rss_kib',result);self.assertNotIn('total_rss_kib',result)
        seven=parse_memory(SEVEN_COLUMNS)
        self.assertEqual(1000,seven['native_pss_kib']);self.assertEqual(3000,seven['native_allocated_kib'])
        self.assertNotIn('native_rss_kib',seven)

    def test_blank_lines_adb_carriage_returns_and_total_row_without_summary(self):
        self.assertEqual(parse_memory(FULL),parse_memory(FULL.replace('\n','\r\r\n\r\r\n')))
        result=parse_memory(LOCAL.split(' App Summary')[0])
        self.assertEqual(282776,result['total_pss_kib']);self.assertEqual(400324,result['total_rss_kib'])

    def test_unknown_or_malformed_columns_stay_unknown(self):
        for text in ('Native Heap 1 2 3 4 5 6 7 8\nTOTAL RSS:100',FULL.replace('1793166  1793164','1793166  unknown')):
            result=parse_memory(text)
            self.assertNotIn('native_rss_kib',result);self.assertNotIn('native_allocated_kib',result)


class BoundedStageTest(unittest.TestCase):
    def run_stage(self, root, stage, memory=FULL, restart=False, extra=(), leave=False, project_visible=True):
        output=Path(root)/stage;clock=Clock();adb=FakeAdb(clock,memory,restart,leave,project_visible)
        argv=['--adb','adb.exe','--serial','phone','--project','787','--output',str(output),
              '--stage',stage,'--seconds','10',*extra]
        if stage=='pan':argv+=['--pan-rect','100,200,300,200']
        if stage=='zoom':argv+=['--zoom-controls','200,100,200,300']
        with patch('profile_mobile_memory.subprocess.run',side_effect=adb),patch('profile_mobile_memory.time.monotonic',clock.now),patch('profile_mobile_memory.time.sleep',clock.sleep),contextlib.redirect_stdout(io.StringIO()):
            code=main(argv)
        return code,json.loads((output/'receipt.json').read_text()),adb.calls

    def test_idle_pan_and_zoom_are_bounded_observed_map_actions_only(self):
        with tempfile.TemporaryDirectory() as root:
            for stage in ('idle','pan','zoom'):
                with self.subTest(stage=stage):
                    code,receipt,calls=self.run_stage(root,stage)
                    self.assertEqual(0,code);self.assertEqual('completed',receipt['status'])
                    inputs=[c for c in calls if c[:2]==['shell','input']]
                    self.assertEqual(len(inputs),receipt['actions']);self.assertGreaterEqual(receipt['sample_count'],2)
                    self.assertLessEqual(receipt['elapsed_s'],12)
                    if stage=='idle':self.assertEqual([],inputs)
                    elif stage=='pan':
                        self.assertLessEqual(len(inputs),15)
                        self.assertEqual(['shell','input','swipe','100','200','300','200','350'],inputs[0])
                        self.assertEqual(['shell','input','swipe','300','200','100','200','350'],inputs[1])
                    else:
                        self.assertLessEqual(len(inputs),8)
                        self.assertEqual(['shell','input','tap','200','100'],inputs[0])
                        self.assertEqual(['shell','input','tap','200','300'],inputs[3])

    def test_pid_change_and_memory_ceiling_stop_before_further_input(self):
        with tempfile.TemporaryDirectory() as root:
            code,receipt,calls=self.run_stage(root,'pan',restart=True)
            self.assertEqual(1,code);self.assertEqual(1,receipt['actions'])
            self.assertIn('restarted',receipt['error'])
        with tempfile.TemporaryDirectory() as root:
            code,receipt,calls=self.run_stage(root,'zoom',extra=['--ceiling-mib','1000'])
            self.assertEqual(1,code);self.assertEqual(0,receipt['actions'])
            self.assertFalse(any(c[:2]==['shell','input'] for c in calls))

    def test_local_resident_counters_are_usable_without_claiming_allocator_zeroes(self):
        with tempfile.TemporaryDirectory() as root:
            code,receipt,calls=self.run_stage(root,'idle',memory=LOCAL)
            self.assertEqual(0,code);self.assertEqual(400324,receipt['last']['total_rss_kib'])
            self.assertFalse(receipt['last']['native_heap_counters_available'])
            self.assertNotIn('native_allocated_kib',receipt['last'])

    def test_foreground_loss_stops_after_the_first_observed_gesture(self):
        with tempfile.TemporaryDirectory() as root:
            code,receipt,calls=self.run_stage(root,'pan',leave=True)
            self.assertEqual(1,code);self.assertEqual(1,receipt['actions'])
            self.assertIn('foreground',receipt['error'])

    def test_project_or_resident_evidence_missing_prevents_gestures(self):
        for options in ({'project_visible':False},{'memory':'TOTAL PSS:100\nTOTAL RSS:200\n'}):
            with self.subTest(options=options),tempfile.TemporaryDirectory() as root:
                code,receipt,calls=self.run_stage(root,'pan',**options)
                self.assertEqual(1,code);self.assertEqual(0,receipt['actions'])
                self.assertFalse(any(c[:2]==['shell','input'] for c in calls))

    def test_invalid_bounds_or_mock_options_are_rejected_before_device_calls(self):
        with tempfile.TemporaryDirectory() as root:
            base=['--adb','adb.exe','--serial','phone','--project','787','--output',str(Path(root)/'new')]
            cases=[['--seconds','601'],['--ceiling-mib','255'],['--stage','pan'],
                   ['--stage','pan','--pan-rect','1,2,1,2'],['--mock-centre','nan,2'],
                   ['--centre-on-mock','1,2'],['--stage','zoom','--zoom-controls','a,2,3,4']]
            for extra in cases:
                with self.subTest(extra=extra),patch('profile_mobile_memory.subprocess.run') as adb,contextlib.redirect_stderr(io.StringIO()):
                    with self.assertRaises(SystemExit):main(base+extra)
                    adb.assert_not_called();self.assertFalse((Path(root)/'new').exists())


class MockRestorationTest(unittest.TestCase):
    def test_original_mode_and_only_gps_provider_are_identified_conservatively(self):
        self.assertEqual('ignore',mock_appop_mode('MOCK_LOCATION: ignore; time=123'))
        self.assertEqual('default',mock_appop_mode('No operations.'))
        for text in ('', 'permission denied', 'MOCK_LOCATION: allow\nMOCK_LOCATION: deny'):
            with self.assertRaises(ValueError):mock_appop_mode(text)
        self.assertFalse(gps_is_mocked(GPS_REAL))
        self.assertTrue(gps_is_mocked('gps provider: mock=true\nnetwork provider:\nmock provider=null'))
        with self.assertRaises(ValueError):gps_is_mocked('Location service unavailable')

    def shell(self, calls, readback='MOCK_LOCATION: deny', gps=GPS_REAL, removal_error=False):
        def run(*words):
            calls.append(words)
            if 'remove-test-provider' in words and removal_error:raise RuntimeError('device transport failed')
            if words[:2]==('dumpsys','location'):return gps
            if words[:3]==('cmd','appops','get'):return readback
            return ''
        return run

    def test_cleanup_requires_matching_appop_and_verified_provider_absence(self):
        calls=[];state={};restore_mock(self.shell(calls),state,True,'deny')
        self.assertEqual('restored-and-readback-verified',state['cleanup'])
        self.assertEqual('removed-and-absence-verified',state['provider_cleanup'])
        for readback,gps in [('MOCK_LOCATION: allow',GPS_REAL),('MOCK_LOCATION: deny','gps provider: mock=true')]:
            state={}
            with self.assertRaises(RuntimeError):restore_mock(self.shell([],readback,gps),state,True,'deny')
            self.assertEqual('failed',state['cleanup'])

    def test_provider_failure_does_not_skip_restoring_original_appop(self):
        calls=[];state={}
        with self.assertRaises(RuntimeError):restore_mock(self.shell(calls,removal_error=True),state,True,'deny')
        self.assertIn(('cmd','appops','set','com.android.shell','android:mock_location','deny'),calls)
        self.assertEqual('original-mode-verified',state['appop_cleanup'])
        self.assertIn('provider',state['cleanup_errors'])

    def test_unconfirmed_provider_creation_never_claims_successful_cleanup(self):
        state={'provider_add_attempted':True};calls=[]
        with self.assertRaises(RuntimeError):restore_mock(self.shell(calls),state,False,'deny')
        self.assertEqual('failed',state['cleanup'])
        self.assertFalse(any('remove-test-provider' in words for words in calls))


if __name__=='__main__':unittest.main()
