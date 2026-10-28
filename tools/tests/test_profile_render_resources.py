"""Technical-only ownership, bounds and transfer regressions; no device or map data."""
import hashlib
import json
from pathlib import Path
import sys
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from profile_render_resources import (APP_DIR, MAX_APP_BYTES, MAX_APP_FILE_BYTES, MAX_LIGHT_BYTES, MAX_TRACE_BYTES,
    MAX_WINDOWS, PACKAGE, inspect_process, light_script, load, owns_command, proc_stat,
    app_footer_quality,choose_native_layout,control_lock,cpu_record_command,invoke_native_background,native_paths,native_stdin_command,pull,pull_file, pull_light, recover_light_owner, recover_window_owners, require_active_app_session, save, session_base, start_window, stop_process, validate_app_header, validate_native_pair, wait_cpu_ack, wait_light_ack, window_offset)

NAME='lisa-render-0123456789abcdef'
BASE='/data/local/tmp/'+NAME
TOKEN='fedcba9876543210'
SOURCE='a'*40+'+dirty'
APP_SESSION='b'*32


def stat(pid=1234,start=5678,comm='worker (native) one'):
    fields=['S']+['0']*23
    fields[11]='19';fields[12]='7';fields[19]=str(start)
    return f'{pid} ({comm}) '+ ' '.join(fields)


class ProcessAdb:
    def __init__(self,command,start=5678):self.command=command;self.start=start;self.calls=[];self.alive=True
    def attempt(self,*args,**kw):
        self.calls.append(args)
        if args[0]=='kill':self.alive=False;return SimpleNamespace(returncode=0,stdout=b'',stderr=b'')
        if args[0]=='cat' and args[1].endswith('/stat'):
            return SimpleNamespace(returncode=0 if self.alive else 1,stdout=stat(start=self.start).encode() if self.alive else b'',stderr=b'')
        if args[:2]==('test','-d'):return SimpleNamespace(returncode=0 if self.alive else 1,stdout=b'',stderr=b'')
        raise AssertionError(args)
    def shell(self,*args,**kw):
        self.calls.append(args)
        if args[0]=='cat' and args[1].endswith('/cmdline'):return self.command
        raise AssertionError(args)


class PullAdb:
    def __init__(self,data=b'profile',changed=False):self.data=data;self.changed=changed;self.hashes=0;self.calls=[]
    def shell(self,*args,**kw):
        self.calls.append(args)
        if args[0]=='stat':return str(len(self.data))
        if args[0]=='sha256sum':
            self.hashes+=1
            digest=hashlib.sha256(self.data if not self.changed or self.hashes==1 else b'changed').hexdigest()
            return digest+'  '+args[1]
        raise AssertionError(args)
    def invoke(self,*args,**kw):
        self.calls.append(args)
        if args[0]=='pull':Path(args[2]).write_bytes(self.data);return SimpleNamespace(returncode=0)
        raise AssertionError(args)


class RenderResourceToolTest(unittest.TestCase):
    def cpu(self):return {'pid':1234,'start_ticks':5678,'target_pid':777,'output':BASE+'/cpu-'+TOKEN+'.data'}
    def cpu_cmd(self):return '\0'.join(['/system/bin/simpleperf','record','-p','777','-o',BASE+'/cpu-'+TOKEN+'.data',''])
    def native(self):return {'pid':1234,'start_ticks':5678,'config':'/data/misc/perfetto-configs/lisa-heap-'+TOKEN+'.pbtxt','trace':'/data/misc/perfetto-traces/lisa-heap-'+TOKEN+'.pftrace'}
    def header(self,**changes):
        h={'event':'app_session','schema_version':1,'session':APP_SESSION,'pid':777,
           'package_name':PACKAGE,'source_revision':SOURCE,'private_resource_profiling':True,
           'max_file_bytes':MAX_APP_FILE_BYTES,'app_build':224,'app_version':'3.1.2.30',
           'deadline_elapsed_ns':3600_000_000_000,'process_64_bit':True,'process_start_elapsed_ms':56780,
           'process_start_ticks':5678,'clock_tick_hz':100}
        h.update(changes);return json.dumps(h)

    def test_proc_stat_parses_last_parenthesis_and_start_identity(self):
        r=proc_stat(stat(comm='a ) b ( c'))
        self.assertEqual((1234,5678,19,7),(r['pid'],r['start_ticks'],r['utime_ticks'],r['stime_ticks']))
        for bad in ('', '1 bad', '1 (x) S 0'):
            with self.assertRaises(ValueError):proc_stat(bad)

    def test_owned_root_is_exact_nonce_not_directory_prefix(self):
        self.assertEqual(BASE,session_base(NAME))
        for bad in ('lisa-render-',NAME+'/../other','lisa-render-0123456789abcdeg'):
            with self.assertRaises(ValueError):session_base(bad)

    def test_cpu_ownership_requires_output_target_and_executable(self):
        self.assertTrue(owns_command('cpu',self.cpu_cmd(),self.cpu(),NAME))
        for bad in (self.cpu_cmd().replace('777','778'), self.cpu_cmd().replace(TOKEN,'a'*16),
                    self.cpu_cmd().replace('simpleperf','unrelated'),'/system/bin/simpleperf\0record\0-p\0'):
            self.assertFalse(owns_command('cpu',bad,self.cpu(),NAME))

    def test_native_ownership_requires_both_exact_paths(self):
        n=self.native();cmd='perfetto\0--txt\0-c\0'+n['config']+'\0-o\0'+n['trace']+'\0'
        self.assertTrue(owns_command('native',cmd,n,NAME))
        self.assertFalse(owns_command('native',cmd.replace(n['trace'],'/data/local/tmp/other'),n,NAME))

    def test_native_fallback_is_bound_to_exact_session_and_window_pair(self):
        config,trace=native_paths(NAME,TOKEN,'owned_tmp')
        self.assertEqual((BASE+'/lisa-heap-'+TOKEN+'.pbtxt',BASE+'/lisa-heap-'+TOKEN+'.pftrace'),(config,trace))
        self.assertEqual('owned_tmp',validate_native_pair(config,trace,NAME))
        n={'pid':1234,'start_ticks':5678,'config':config,'trace':trace}
        cmd='perfetto\0--txt\0-c\0'+config+'\0-o\0'+trace+'\0'
        self.assertTrue(owns_command('native',cmd,n,NAME))
        for c,t in ((config.replace(NAME,'lisa-render-'+'1'*16),trace),
                    (config,trace.replace(TOKEN,'a'*16)),(config,trace.replace(BASE,BASE+'/..')),
                    ('/data/local/tmp/lisa-heap-'+TOKEN+'.pbtxt','/data/local/tmp/lisa-heap-'+TOKEN+'.pftrace')):
            with self.assertRaises(ValueError):validate_native_pair(c,t,NAME)

    def test_native_path_capability_uses_stdin_without_reading_system_configs(self):
        class A:
            calls=[]
            missing=True
            def attempt(self,*args):
                self.calls.append(args)
                missing=self.missing and args[-1]=='/data/misc/perfetto-configs'
                return SimpleNamespace(returncode=1 if missing else 0,stdout=b'',stderr=b'')
        a=A();self.assertEqual('stdin_standard',choose_native_layout(a,NAME)[0])
        self.assertTrue(all(c[0]=='test' for c in a.calls))
        self.assertIn(('test','-w',BASE),a.calls)
        self.assertFalse(any(c[-1]=='/data/misc/perfetto-configs' for c in a.calls))
        a.missing=False;self.assertEqual('stdin_standard',choose_native_layout(a,NAME)[0])

    def test_stdin_native_parent_requires_exact_dash_config_and_trace(self):
        config,trace=native_paths(NAME,TOKEN,'stdin_standard')
        self.assertEqual('stdin_standard',validate_native_pair(config,trace,NAME))
        owner={'config':config,'trace':trace,'pid':1234,'start_ticks':5678}
        cmd='\0'.join(['perfetto','--txt','-c','-','-o',trace,''])
        self.assertTrue(owns_command('native',cmd,owner,NAME))
        self.assertFalse(owns_command('native',cmd.replace('\0-\0','\0'+config+'\0'),owner,NAME))
        self.assertFalse(owns_command('native',cmd.replace(TOKEN,'a'*16),owner,NAME))
        launch=native_stdin_command(NAME,config,trace)
        self.assertEqual(['perfetto','--background','--txt','-c','-','-o',trace],launch)
        self.assertNotIn(config,launch)

    def test_native_background_uses_host_stdin_and_actual_cli_pid(self):
        config,trace=native_paths(NAME,TOKEN,'stdin_standard')
        adb=SimpleNamespace(binary=Path('adb.exe'),serial='192.168.0.17:33999')
        content=b'duration_ms: 20000\n'
        result=SimpleNamespace(returncode=0,stdout=b'1005\n',stderr=b'')
        with patch('profile_render_resources.subprocess.run',return_value=result) as run:
            self.assertIs(result,invoke_native_background(adb,NAME,config,trace,content))
        args,kw=run.call_args
        self.assertEqual(['adb.exe','-s',adb.serial,'shell','perfetto','--background','--txt','-c','-','-o',trace],args[0])
        self.assertEqual(content,kw['input']);self.assertEqual(15,kw['timeout'])
        self.assertNotIn('shell',kw)

    def test_supported_cpu_app_parent_is_owned_but_its_child_is_not_signalled(self):
        owner={**self.cpu(),'selector':'app'}
        command=cpu_record_command(NAME,TOKEN,180,'fp')
        self.assertEqual('99',command[command.index('-f')+1]);self.assertEqual('32M',command[command.index('--size-limit')+1])
        self.assertEqual('180',command[command.index('--duration')+1]);self.assertEqual(PACKAGE,command[command.index('--app')+1])
        self.assertNotIn('-p',command)
        raw='\0'.join(command+[''])
        self.assertTrue(owns_command('cpu',raw,owner,NAME))
        self.assertFalse(owns_command('cpu',raw.replace(PACKAGE,'unrelated.app'),owner,NAME))
        child='\0'.join(['/system/bin/simpleperf','record','--in-app','--out-fd','7','--stop-signal-fd','8',''])
        self.assertFalse(owns_command('cpu',child,owner,NAME))
        a=ProcessAdb(raw)
        self.assertEqual('stopped-and-absent',stop_process(a,'cpu',owner,NAME))
        self.assertEqual([('kill','-INT','1234')],[c for c in a.calls if c[0]=='kill'])
        for seconds in (0,19,301):
            with self.assertRaises(ValueError):cpu_record_command(NAME,TOKEN,seconds,'fp')

    def test_cpu_ack_discovers_forked_parent_not_setsid_launcher_or_in_app_child(self):
        owner={**self.cpu(),'selector':'app','launcher_pid':1995}
        owner.pop('pid');owner.pop('start_ticks')
        parent='\0'.join(cpu_record_command(NAME,TOKEN,180,'fp')+[''])
        child='\0'.join(['simpleperf','record','--in-app','--out-fd','7',''])
        class A:
            def attempt(self,*args,**kw):
                if args==('pidof','simpleperf'):return SimpleNamespace(returncode=0,stdout=b'1996 1997',stderr=b'')
                if args==('cat','/proc/1996/cmdline'):return SimpleNamespace(returncode=0,stdout=parent.encode(),stderr=b'')
                if args==('cat','/proc/1997/cmdline'):return SimpleNamespace(returncode=0,stdout=child.encode(),stderr=b'')
                if args==('cat','/proc/1996/stat'):return SimpleNamespace(returncode=0,stdout=stat(pid=1996,start=9876).encode(),stderr=b'')
                raise AssertionError(args)
            def shell(self,*args,**kw):
                if args==('cat','/proc/1996/cmdline'):return parent
                raise AssertionError(args)
        a=A();ack=wait_cpu_ack(a,owner,NAME)
        self.assertEqual(1996,ack['pid']);self.assertEqual(9876,ack['start_ticks'])
        self.assertEqual(1995,owner['launcher_pid'])
        window={'native':{**self.native()},'cpu':{**owner,'pid':1995}}
        recover_window_owners(a,window,NAME)
        self.assertEqual(1996,window['cpu']['pid']);self.assertEqual(9876,window['cpu']['start_ticks'])
        self.assertEqual(1995,window['cpu']['unverified_returned_pid'])

    def test_light_ownership_does_not_match_another_shell_or_nonce(self):
        cmd='\0'.join(['sh',BASE+'/light.sh','1800',NAME,''])
        self.assertTrue(owns_command('light',cmd,{},NAME))
        self.assertFalse(owns_command('light',cmd.replace(NAME,'lisa-render-'+'1'*16),{},NAME))

    def test_reused_pid_is_never_signalled(self):
        adb=ProcessAdb(self.cpu_cmd(),start=9999)
        with self.assertRaisesRegex(RuntimeError,'reused'):stop_process(adb,'cpu',self.cpu(),NAME)
        self.assertFalse(any(c[0]=='kill' for c in adb.calls))

    def test_other_command_is_never_signalled(self):
        adb=ProcessAdb(self.cpu_cmd().replace(TOKEN,'a'*16))
        with self.assertRaisesRegex(RuntimeError,'another command'):stop_process(adb,'cpu',self.cpu(),NAME)
        self.assertFalse(any(c[0]=='kill' for c in adb.calls))

    def test_owned_cpu_stops_by_its_single_pid_and_int(self):
        adb=ProcessAdb(self.cpu_cmd())
        self.assertEqual('stopped-and-absent',stop_process(adb,'cpu',self.cpu(),NAME))
        self.assertEqual([('kill','-INT','1234')],[x for x in adb.calls if x[0]=='kill'])

    def test_already_exited_collector_is_not_signalled(self):
        adb=ProcessAdb(self.cpu_cmd());adb.alive=False
        self.assertIsNone(inspect_process(adb,1234,'cpu',self.cpu(),NAME))
        self.assertEqual('already-exited',stop_process(adb,'cpu',self.cpu(),NAME))
        self.assertFalse(any(c[0]=='kill' for c in adb.calls))

    def test_app_export_requires_filename_session_package_source_private_gate(self):
        name='lisa-resources-'+APP_SESSION+'.jsonl'
        self.assertEqual(777,validate_app_header(name,self.header(),SOURCE)['pid'])
        for changes in ({'session':'c'*32},{'source_revision':'d'*40},{'package_name':'another.app'},
                        {'private_resource_profiling':False},{'max_file_bytes':999999999},{'event':'tile'},
                        {'process_64_bit':'true'}):
            with self.assertRaises(ValueError):validate_app_header(name,self.header(**changes),SOURCE)

    def test_app_export_rejects_path_injection_and_oversize_header(self):
        for name in ('../lisa-resources-'+APP_SESSION+'.jsonl','lisa-resources-'+APP_SESSION.upper()+'.jsonl'):
            with self.assertRaises(ValueError):validate_app_header(name,self.header(),SOURCE)
        with self.assertRaises(ValueError):validate_app_header('lisa-resources-'+APP_SESSION+'.jsonl',' '*9000,SOURCE)

    def test_authoritative_footer_is_bound_to_header_not_presence_of_an_end_name(self):
        header=json.loads(self.header(app_version='3.1.2.30',app_build=224))
        summary={'session':APP_SESSION,'revision':SOURCE,'version':'3.1.2.30','build':224,
                 'stopped':True,'detailed_complete':False,'cpu_fully_attributed':False}
        end={'event':'app_end','session':APP_SESSION,'pid':777,'summary':summary,'complete_footer':True,
             'summary_available':True,'resource_end_persisted_in_footer':True,'raw_app_stream_complete':False,
             'queue_dropped_records':2,'file_rejected_records':0,'observer_errors':0}
        q=app_footer_quality(header,[header,end],False)
        self.assertTrue(q['complete_summary_footer']);self.assertFalse(q['detailed_complete'])
        self.assertFalse(q['raw_app_stream_complete']);self.assertEqual(2,q['queue_dropped_records'])
        self.assertFalse(app_footer_quality(header,[header,end],True)['complete_summary_footer'])
        bad={**end,'summary':{**summary,'revision':'c'*40}}
        self.assertFalse(app_footer_quality(header,[header,bad],False)['complete_summary_footer'])
        absent={**end,'summary_available':False,'summary':None}
        self.assertFalse(app_footer_quality(header,[header,absent],False)['complete_summary_footer'])
        with self.assertRaises(ValueError):app_footer_quality(header,[header,end,end],False)

    def test_missing_footer_is_explicit_incomplete_prefix(self):
        h=json.loads(self.header())
        q=app_footer_quality(h,[h],False)
        self.assertFalse(q['has_app_end']);self.assertFalse(q['complete_summary_footer'])

    def test_session_budget_and_schedules_allow_five_spread_windows(self):
        self.assertLessEqual(MAX_WINDOWS*MAX_TRACE_BYTES*2+MAX_LIGHT_BYTES+MAX_APP_BYTES,512*1024**2)
        self.assertEqual(2*MAX_APP_FILE_BYTES,MAX_APP_BYTES)
        offsets=[window_offset(1800,180,5,i,'spread') for i in range(5)]
        self.assertEqual(0,offsets[0]);self.assertEqual(1780,offsets[-1]+180)
        self.assertTrue(all(b-a>=180 for a,b in zip(offsets,offsets[1:])))
        self.assertEqual(720,window_offset(1800,180,5,4,'continuous'))
        with self.assertRaises(ValueError):window_offset(1800,180,6,0,'spread')

    def test_sixth_window_is_rejected_before_device_calls(self):
        with self.assertRaisesRegex(ValueError,'Five-window'):start_window(object(),{'windows':[{}]*5},Path('.'),180)

    def test_stopped_app_journal_in_same_pid_is_not_active_capture(self):
        test=self
        class A:
            def attempt(self,*args,**kw):
                if args==('pidof',PACKAGE):return SimpleNamespace(stdout=b'777',returncode=0)
                if args==('ls','-1',APP_DIR):return SimpleNamespace(stdout=('lisa-resources-'+APP_SESSION+'.jsonl\n').encode(),returncode=0)
                raise AssertionError(args)
            def shell(self,*args,**kw):
                if args[0]=='stat':return '2000'
                if args[0]=='head':return test.header()
                if args[:2]==('cat','/proc/uptime'):return '100.0 10.0'
                if args==('getconf','CLK_TCK'):return '100'
                if args==('cat','/proc/777/stat'):return stat(pid=777)
                if args[0]=='tail':return json.dumps({'event':'app_end','session':APP_SESSION})
                raise AssertionError(args)
        r={'baseline_app_files':{},'initial_app_pid':777,'source_revision':SOURCE}
        with self.assertRaisesRegex(RuntimeError,'fresh app process'):require_active_app_session(A(),r)

    def test_active_app_journal_needs_matching_pid_and_future_deadline(self):
        test=self
        class A:
            deadline=3600_000_000_000
            pid=777
            process_start_ticks=5678
            def attempt(self,*args,**kw):
                if args==('pidof',PACKAGE):return SimpleNamespace(stdout=b'777',returncode=0)
                if args==('ls','-1',APP_DIR):return SimpleNamespace(stdout=('lisa-resources-'+APP_SESSION+'.jsonl\n').encode(),returncode=0)
                raise AssertionError(args)
            def shell(self,*args,**kw):
                if args[0]=='stat':return '2000'
                if args[0]=='head':return test.header(pid=self.pid,deadline_elapsed_ns=self.deadline,
                                                    process_start_ticks=self.process_start_ticks)
                if args[:2]==('cat','/proc/uptime'):return '100.0 10.0'
                if args==('getconf','CLK_TCK'):return '100'
                if args==('cat','/proc/777/stat'):return stat(pid=777)
                if args[0]=='tail':return json.dumps({'event':'app_checkpoint','session':APP_SESSION})
                raise AssertionError(args)
        r={'baseline_app_files':{},'initial_app_pid':777,'source_revision':SOURCE}
        a=A();self.assertEqual(APP_SESSION,require_active_app_session(a,r)['header']['session'])
        a.deadline=99_000_000_000
        with self.assertRaisesRegex(RuntimeError,'active private app journal'):require_active_app_session(a,r)
        a.deadline=3600_000_000_000;a.pid=778
        with self.assertRaisesRegex(RuntimeError,'active private app journal'):require_active_app_session(a,r)
        a.pid=777;a.process_start_ticks=123
        with self.assertRaisesRegex(RuntimeError,'active private app journal'):require_active_app_session(a,r)

    def test_unacknowledged_light_launch_is_not_reported_stopped(self):
        class A:
            def attempt(self,*args):return SimpleNamespace(returncode=1,stdout=b'')
        r={'remote_base':BASE,'session':NAME,'light_launch_attempted':True}
        with self.assertRaisesRegex(RuntimeError,'not acknowledged'):recover_light_owner(A(),r)
        self.assertNotIn('light',r)

    def test_light_owner_recovery_checks_persisted_process_start(self):
        cmd='\0'.join(['sh',BASE+'/light.sh','1800',NAME,''])
        class A(ProcessAdb):
            def attempt(self,*args,**kw):
                if args==('cat',BASE+'/light/owner.pid'):return SimpleNamespace(returncode=0,stdout=b'1234\n')
                return super().attempt(*args,**kw)
            def shell(self,*args,**kw):
                if args==('cat',BASE+'/light/owner-stat.raw'):return stat()
                return super().shell(*args,**kw)
        r={'remote_base':BASE,'session':NAME,'light_launch_attempted':True}
        recover_light_owner(A(cmd),r)
        self.assertEqual(5678,r['light']['start_ticks'])
        r={'remote_base':BASE,'session':NAME,'light_launch_attempted':True}
        with self.assertRaisesRegex(RuntimeError,'reused'):recover_light_owner(A(cmd,start=9999),r)

    def test_light_ack_waits_for_empty_created_pid_stat_and_header(self):
        cmd='\0'.join(['sh',BASE+'/light.sh','1800',NAME,''])
        header={'event':'light_start','session':NAME,'owner_pid':1234,'max_bytes':MAX_LIGHT_BYTES}
        class A(ProcessAdb):
            polls=0
            def attempt(self,*args,**kw):
                if args==('cat',BASE+'/light/owner.pid'):
                    self.polls+=1
                    return SimpleNamespace(returncode=0,stdout=b'' if self.polls==1 else b'1234\n')
                if args==('cat',BASE+'/light/owner-stat.raw'):
                    return SimpleNamespace(returncode=0,stdout=b'' if self.polls==2 else stat().encode())
                if args==('cat',BASE+'/light/start.json'):
                    return SimpleNamespace(returncode=0,stdout=b'' if self.polls==3 else json.dumps(header).encode())
                return super().attempt(*args,**kw)
        a=A(cmd)
        with patch('profile_render_resources.time.sleep'):
            owner,ack=wait_light_ack(a,{'remote_base':BASE,'session':NAME})
        self.assertEqual(4,a.polls);self.assertEqual(5678,owner['start_ticks'])
        self.assertEqual(header,ack)

    def test_light_ack_never_accepts_only_created_empty_pid(self):
        class A:
            def attempt(self,*args,**kw):return SimpleNamespace(returncode=0,stdout=b'')
        with patch('profile_render_resources.time.monotonic',side_effect=[0,0,13]), \
             patch('profile_render_resources.time.sleep'):
            with self.assertRaisesRegex(RuntimeError,'complete PID/start-stat'):
                wait_light_ack(A(),{'remote_base':BASE,'session':NAME})

    def test_control_transactions_exclude_another_command(self):
        with tempfile.TemporaryDirectory() as d:
            path=Path(d)/'receipt.json'
            with control_lock(path):
                with self.assertRaisesRegex(RuntimeError,'Another session'):
                    with control_lock(path,wait_seconds=0):pass

    def test_aborted_pull_keeps_progress_and_bounded_retry_receipts(self):
        with tempfile.TemporaryDirectory() as d:
            path=Path(d)/'receipt.json'
            r={'state':'OWNED_EXTERNAL_COLLECTORS_STOPPED','session':NAME}
            save(path,r)
            def fail(adb,receipt,output,report):
                report['artifacts'].append({'remote':BASE+'/trace.data','bytes':7,'cap_reached':False})
                raise RuntimeError('inaccessible app inventory')
            with patch('profile_render_resources.load',side_effect=lambda args:(path,json.loads(path.read_text()),object())), \
                 patch('profile_render_resources.shutil.disk_usage',return_value=SimpleNamespace(free=100*1024**3)), \
                 patch('profile_render_resources.pull_contents',side_effect=fail) as capture:
                for attempt in range(2):
                    with self.assertRaisesRegex(RuntimeError,'inaccessible'):pull(SimpleNamespace())
                    saved=json.loads(path.read_text())
                    self.assertEqual(attempt+1,len(saved['pulls']))
                    self.assertEqual('PULL_INCOMPLETE_RETAIN_REMOTE',saved['state'])
                    progress=json.loads(Path(saved['pulls'][-1]).read_text())
                    self.assertEqual(7,progress['artifacts'][0]['bytes'])
                    self.assertFalse(progress['complete_transfer'])
                with self.assertRaisesRegex(RuntimeError,'Two immutable'):pull(SimpleNamespace())
                self.assertEqual(2,capture.call_count)

    def test_cold_start_requires_confirmation_before_app_stop(self):
        class A:
            def __init__(self):self.calls=[]
            def attempt(self,*args,**kw):self.calls.append(args);return SimpleNamespace(stdout=b'777',returncode=0)
        adb=A()
        with self.assertRaisesRegex(ValueError,'confirmation'):
            start_window(adb,{'windows':[]},Path('.'),180,cold_start=True,confirm_idle=False)
        self.assertFalse(any(c[0]=='am' for c in adb.calls))

    def test_cold_start_preparation_failure_never_stops_app(self):
        class A:
            def __init__(self):self.calls=[]
            def attempt(self,*args,**kw):
                self.calls.append(args)
                if args==('pidof',PACKAGE):return SimpleNamespace(stdout=b'777',stderr=b'',returncode=0)
                if args[:3]==('/system/bin/simpleperf','record','--help'):
                    return SimpleNamespace(stdout=b'--size-limit',stderr=b'',returncode=0)
                if args[0]=='test':
                    absent=args[1]=='-e' or args[-1]=='/data/misc/perfetto-configs'
                    return SimpleNamespace(stdout=b'',stderr=b'',returncode=1 if absent else 0)
                raise AssertionError(args)
            def shell(self,*args,**kw):
                self.calls.append(args)
                if args==('pm','path',PACKAGE):return 'package:/data/app/base.apk'
                if args[0]=='sha256sum':return 'a'*64+'  '+args[1]
                if args==('dumpsys','package',PACKAGE):return 'primaryCpuAbi=arm64-v8a'
                raise AssertionError(args)
            def invoke(self,*args,**kw):
                self.calls.append(args)
                raise RuntimeError('config push denied')
        with tempfile.TemporaryDirectory() as d:
            a=A();r={'windows':[],'session':NAME,'remote_base':BASE,'installed_apk':{'sha256':'a'*64}}
            with self.assertRaisesRegex(RuntimeError,'config push denied'):
                start_window(a,r,Path(d),20,cold_start=True,confirm_idle=True)
            self.assertFalse(any(c[:2]==('am','force-stop') for c in a.calls))
            self.assertEqual('stdin_standard',r['windows'][0]['native_path_layout'])

    def test_generated_light_script_is_owned_bounded_and_read_only_to_app(self):
        s=light_script(NAME,'a'*64)
        self.assertIn('DEADLINE=$((START+DURATION))',s);self.assertIn('30720',s)
        self.assertIn('dumpsys -t 5 meminfo',s);self.assertIn('apk_changed',s)
        self.assertIn('no_wakelock',s);self.assertIn(BASE,s)
        for forbidden in ('pm clear','am force-stop','pkill','killall','setenforce','rm -rf'):
            self.assertNotIn(forbidden,s)

    def test_file_pull_verifies_hash_without_overwriting_prior_evidence(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)/'trace';adb=PullAdb()
            r=pull_file(adb,BASE+'/file',p,64)
            self.assertEqual(7,r['bytes']);self.assertEqual(hashlib.sha256(b'profile').hexdigest(),r['sha256'])
            with self.assertRaises(FileExistsError):pull_file(adb,BASE+'/file',p,64)

    def test_changed_source_after_pull_preserves_partial_and_reports_failure(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)/'trace';adb=PullAdb(changed=True)
            with self.assertRaisesRegex(RuntimeError,'mismatch'):pull_file(adb,BASE+'/file',p,64)
            self.assertEqual(b'profile',p.read_bytes())

    def test_oversize_artifact_is_not_downloaded(self):
        with tempfile.TemporaryDirectory() as d:
            adb=PullAdb(data=b'profile')
            with self.assertRaisesRegex(RuntimeError,'bound'):pull_file(adb,BASE+'/file',Path(d)/'trace',2)
            self.assertFalse(any(x[0]=='pull' for x in adb.calls))

    def test_unknown_light_file_prevents_blanket_download(self):
        class A:
            def __init__(self):self.calls=[]
            def shell(self,*args):self.calls.append(args);return 'cpu-0001.txt\nuser.db'
            def invoke(self,*args,**kw):self.calls.append(args);raise AssertionError('Unknown files must not be pulled')
        with tempfile.TemporaryDirectory() as d:
            adb=A()
            with self.assertRaisesRegex(RuntimeError,'Unknown light'):pull_light(adb,BASE,Path(d))
            self.assertFalse(any(x[0]=='pull' for x in adb.calls))


if __name__=='__main__':unittest.main()
