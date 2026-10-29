import csv
import io
import json
from pathlib import Path
import sqlite3
import sys
from types import SimpleNamespace
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from profile_native_heap import (Adb,AdbFailure,PACKAGE,allocation_coverage,analysis_sql,assert_no_recorder,configuration,find_owned_profilers,owns_perfetto,
                                 parse_analysis,parse_perfetto_pid,remote_absent,remote_paths,remove_owned,stop_owned,validate_remote_pair)


CONFIG = '/data/misc/perfetto-configs/lisa-heap-0123456789abcdef.pbtxt'
TRACE = '/data/misc/perfetto-traces/lisa-heap-0123456789abcdef.pftrace'


def csv_result(rows):
    stream = io.StringIO(); writer = csv.writer(stream); writer.writerow(['section', 'payload_hex'])
    for section, data in rows: writer.writerow([section, json.dumps(data).encode().hex()])
    return stream.getvalue()


class FakeAdb:
    def __init__(self, commandline=None): self.commandline = commandline; self.calls = []
    def attempt(self, *words, **options):
        self.calls.append(words)
        if words[0] == 'cat':
            return SimpleNamespace(returncode=0 if self.commandline else 1, stdout=(self.commandline or '').encode(),stderr=b'')
        return SimpleNamespace(returncode=1, stdout=b'',stderr=b'')
    def shell(self, *words, **options): self.calls.append(words); return ''


class NativeHeapCaptureTest(unittest.TestCase):
    def summary(self): return dict(pid=1234, rows=4, process_count=1, net_bytes=100, allocated_bytes=160, freed_bytes=60)

    def test_configuration_targets_only_the_explicit_main_process_and_is_bounded(self):
        text = configuration(120, 'lisa-heap-0123456789abcdef')
        self.assertIn('duration_ms: 120000', text); self.assertIn('process_cmdline: "'+PACKAGE+'"', text)
        self.assertEqual(1, text.count('process_cmdline:')); self.assertIn('block_client: false', text)
        self.assertNotIn('all_heaps: true', text); self.assertNotIn('linux.process_stats', text)
        self.assertIn('sampling_interval_bytes: 1048576',text)
        self.assertIn('shmem_size_bytes: 67108864',text)
        self.assertIn('flush_period_ms: 5000',text)
        for duration in (0, 301):
            with self.assertRaises(ValueError): configuration(duration, 'lisa-heap-0123456789abcdef')
        with self.assertRaises(ValueError): configuration(120, 'unowned')

    def test_supported_android_paths_share_one_nonce_and_legacy_is_recovery_only(self):
        name='lisa-heap-0123456789abcdef'
        self.assertEqual((CONFIG,TRACE),remote_paths(name));self.assertEqual(name,validate_remote_pair(CONFIG,TRACE))
        for config,trace in ((CONFIG,TRACE.replace('abcdef','abcdea')),(TRACE,CONFIG),
                             (CONFIG,'/data/local/tmp/'+name+'.pftrace')):
            with self.assertRaises(ValueError):validate_remote_pair(config,trace,allow_legacy=True)
        legacy=('/data/local/tmp/'+name+'.pbtxt','/data/local/tmp/'+name+'.pftrace')
        with self.assertRaises(ValueError):validate_remote_pair(*legacy)
        self.assertEqual(name,validate_remote_pair(*legacy,allow_legacy=True))

    def test_only_the_returned_pid_with_both_exact_owned_paths_can_be_signalled(self):
        self.assertEqual(1234, parse_perfetto_pid('trace starting\n1234\n'))
        for text in ('', '1234\n5678', 'pid=1234', '0'):
            with self.assertRaises(ValueError): parse_perfetto_pid(text)
        commandline='perfetto\0--background-wait\0-c\0'+CONFIG+'\0-o\0'+TRACE+'\0'
        self.assertTrue(owns_perfetto(commandline, CONFIG, TRACE))
        adb=FakeAdb(commandline)
        self.assertEqual('terminated-and-exited', stop_owned(adb,1234,CONFIG,TRACE))
        self.assertIn(('kill','-TERM','1234'), adb.calls)
        adb=FakeAdb('perfetto\0-c\0/somebody-elses-trace\0')
        with self.assertRaisesRegex(RuntimeError, 'unrelated'): stop_owned(adb,1234,CONFIG,TRACE)
        self.assertFalse(any(words[0]=='kill' for words in adb.calls))

    def test_already_exited_profiler_is_not_signalled_and_cleanup_is_exact(self):
        adb=FakeAdb()
        self.assertEqual('already-exited', stop_owned(adb,1234,CONFIG,TRACE))
        self.assertFalse(any(words[0]=='kill' for words in adb.calls))
        remove_owned(adb,[CONFIG,TRACE])
        self.assertIn(('rm','-f','--',CONFIG,TRACE),adb.calls)
        before=list(adb.calls)
        for paths in ([],['/data/local/tmp'],[CONFIG,'/sdcard/user-data']):
            with self.assertRaises(ValueError): remove_owned(adb,paths)
        self.assertEqual(before,adb.calls)

    def test_duration_exit_between_identity_check_and_term_requires_fresh_absence(self):
        commandline='perfetto\0-c\0'+CONFIG+'\0-o\0'+TRACE+'\0'
        class ExitRace(FakeAdb):
            def __init__(self, absent): super().__init__(commandline); self.absent=absent
            def shell(self,*words,**options):
                self.calls.append(words)
                raise AdbFailure(('shell',*words),SimpleNamespace(returncode=1,stdout=b'',stderr=b'kill: No such process'))
            def attempt(self,*words,**options):
                if words[0]=='test':
                    self.calls.append(words)
                    return SimpleNamespace(returncode=1,stdout=b'',stderr=b'' if self.absent else b'error: device offline')
                return super().attempt(*words,**options)
        adb=ExitRace(True)
        self.assertEqual('already-exited-before-signal',stop_owned(adb,1234,CONFIG,TRACE))
        self.assertEqual(('test','-d','/proc/1234'),adb.calls[-1])
        with self.assertRaises(AdbFailure):stop_owned(ExitRace(False),1234,CONFIG,TRACE)

    def test_real_recorders_block_cold_restart_but_background_gps_does_not(self):
        assert_no_recorder('ServiceRecord{abc '+PACKAGE+'/com.nextgis.mobile.location.BackgroundLocationService}')
        for name in ('TrackerService','WalkEditService'):
            with self.assertRaisesRegex(ValueError,'recording'):
                assert_no_recorder('ServiceRecord{abc '+PACKAGE+'/example.'+name+'}')

    def test_transport_failure_never_counts_as_verified_process_or_file_absence(self):
        self.assertTrue(remote_absent(SimpleNamespace(returncode=1,stderr=b'')))
        self.assertFalse(remote_absent(SimpleNamespace(returncode=1,stderr=b'error: device offline')))
        class Disconnected(FakeAdb):
            def attempt(self,*words,**options):
                self.calls.append(words)
                return SimpleNamespace(returncode=1,stdout=b'',stderr=b'error: device offline')
        adb=Disconnected()
        with self.assertRaisesRegex(RuntimeError,'ownership'):stop_owned(adb,1234,CONFIG,TRACE)
        self.assertFalse(any(words[0]=='kill' for words in adb.calls))
        with self.assertRaisesRegex(RuntimeError,'absence'):remove_owned(adb,[CONFIG,TRACE])

    def test_actual_nonzero_adb_result_retains_command_exit_and_bounded_diagnostic_output(self):
        result=SimpleNamespace(returncode=1,stdout=b'x'*9000,stderr=b'Config parse error at line7: unknown field')
        with patch('profile_native_heap.subprocess.run',return_value=result):
            with self.assertRaises(AdbFailure) as failure:Adb(Path('adb.exe'),'device').shell('perfetto','--background-wait')
        self.assertIn('unknown field',str(failure.exception))
        self.assertEqual(['shell','perfetto','--background-wait'],failure.exception.details['command'])
        self.assertEqual(1,failure.exception.details['exit_code'])
        self.assertEqual(4096,len(failure.exception.details['stdout_tail']))

    def test_recovery_discovery_identifies_only_exact_owned_session_not_other_perfetto(self):
        class Discovery(FakeAdb):
            def attempt(self,*words,**options):
                self.calls.append(words)
                if words[0]=='pidof':return SimpleNamespace(returncode=0,stdout=b'100 200',stderr=b'')
                commandline=('perfetto\0-c\0'+CONFIG+'\0-o\0'+TRACE+'\0') if words[1]=='/proc/100/cmdline' else 'perfetto\0-c\0/other-config\0'
                return SimpleNamespace(returncode=0,stdout=commandline.encode(),stderr=b'')
        adb=Discovery()
        self.assertEqual([100],find_owned_profilers(adb,CONFIG,TRACE))
        self.assertFalse(any(words[0]=='kill' for words in adb.calls))
        self.assertEqual([],find_owned_profilers(FakeAdb(),CONFIG,TRACE))

    def test_negative_frees_remain_distinct_from_total_allocations_and_net_retained(self):
        report=parse_analysis(csv_result([('summary',self.summary()),('callsite',dict(callsite_id=7,net_bytes=100,allocated_bytes=160,freed_bytes=60))]))
        self.assertEqual(100,report['summary'][0]['net_bytes']);self.assertEqual(60,report['summary'][0]['freed_bytes'])
        self.assertEqual('complete',report['quality'])
        sql=analysis_sql(1234);self.assertIn('WHERE pid=1234',sql);self.assertIn('WHEN size<0 THEN -size',sql)
        with self.assertRaises(ValueError):analysis_sql('1234 OR 1=1')

    def test_missing_or_ambiguous_allocation_evidence_and_data_loss_never_pass(self):
        for update in (dict(rows=0),dict(allocated_bytes=0),dict(process_count=2),dict(net_bytes=-1)):
            summary={**self.summary(),**update}
            with self.assertRaises(ValueError):parse_analysis(csv_result([('summary',summary)]))
        for stat in (dict(name='heapprofd_buffer_overran',value=1,severity='info'),
                     dict(name='trace_data_lost',value=1,severity='data_loss')):
            report=parse_analysis(csv_result([('summary',self.summary()),('stat',stat)]))
            self.assertEqual('incomplete',report['quality']);self.assertEqual([stat],report['quality_errors'])
        report=parse_analysis(csv_result([('summary',self.summary()),('stat',dict(name='buffer_size',value=32768,severity='info'))]))
        self.assertEqual('complete',report['quality'])

    def test_coverage_requires_multiple_dumps_spanning_the_requested_observation(self):
        def report(seconds):return {'timeline':[{'ts':int(t*1e9)} for t in seconds]}
        for times in ([],[0],[0,0],[0,10],[0,149]):
            self.assertEqual('incomplete',allocation_coverage(report(times),180)['status'])
        result=allocation_coverage(report([10,20,160]),180)
        self.assertEqual('complete',result['status']);self.assertEqual(150,result['observed_span_s'])
        self.assertEqual(150,result['required_span_s']);self.assertEqual(3,result['distinct_dumps'])
        self.assertEqual('complete',allocation_coverage(report([0,10]),20)['status'])
        with self.assertRaises(ValueError):allocation_coverage(report([0,300]),301)

    def test_analysis_query_accounts_frees_excludes_other_processes_and_keeps_stack_offsets(self):
        with sqlite3.connect(':memory:') as database:
            database.executescript('''
              CREATE TABLE process(upid INTEGER,pid INTEGER);
              CREATE TABLE heap_profile_allocation(upid INTEGER,ts INTEGER,heap_name TEXT,callsite_id INTEGER,size INTEGER);
              CREATE TABLE stack_profile_callsite(id INTEGER,frame_id INTEGER,parent_id INTEGER);
              CREATE TABLE stack_profile_frame(id INTEGER,name TEXT,mapping INTEGER,rel_pc INTEGER);
              CREATE TABLE stack_profile_mapping(id INTEGER,name TEXT,build_id TEXT);
              CREATE TABLE stats(name TEXT,idx INTEGER,value INTEGER,severity TEXT,source TEXT);
              INSERT INTO process VALUES(1,1234),(2,9999);
              INSERT INTO heap_profile_allocation VALUES(1,10,'libc.malloc',7,160),(1,20,'libc.malloc',7,-60),(2,20,'libc.malloc',7,1000);
              INSERT INTO stack_profile_callsite VALUES(7,11,8),(8,12,NULL);
              INSERT INTO stack_profile_frame VALUES(11,'new',1,111),(12,'map-prepare',1,222);
              INSERT INTO stack_profile_mapping VALUES(1,'libmaplibre.so','verified-build-id');
            ''')
            cursor=database.execute(analysis_sql(1234));rows=cursor.fetchall()
        stream=io.StringIO();writer=csv.writer(stream);writer.writerow(['section','payload_hex']);writer.writerows(rows)
        report=parse_analysis(stream.getvalue())
        self.assertEqual(100,report['summary'][0]['net_bytes']);self.assertEqual(160,report['summary'][0]['allocated_bytes'])
        self.assertEqual(60,report['summary'][0]['freed_bytes'])
        self.assertEqual([160,100],[row['net_bytes'] for row in report['timeline']])
        self.assertEqual({111,222},{row['rel_pc'] for row in report['frame']})


if __name__=='__main__': unittest.main()
