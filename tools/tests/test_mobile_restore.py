import json
from pathlib import Path
import sys
from tempfile import TemporaryDirectory
from types import SimpleNamespace
import unittest
from unittest.mock import Mock,patch

sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from run_mobile_tests import Verification,PACKAGE

class RestoreTest(unittest.TestCase):
    def runner(self,folder,owned='emulator-5556'):
        runner=Verification.__new__(Verification)
        runner.adb='adb';runner.output=Path(folder);runner.devices={}
        runner.owned_emulator=owned;runner.emulator='emulator-5556'
        runner.saved_emulator={};runner.command=Mock();runner.shell=Mock()
        runner.assert_build_pair=Mock()
        return runner

    def test_owned_console_stops_even_when_setting_restore_raises(self):
        with TemporaryDirectory() as folder,patch('run_mobile_tests.socket.socket') as socket:
            socket.return_value.__enter__.return_value.connect_ex.return_value=1
            runner=self.runner(folder);runner.saved_emulator={'window_animation_scale':'1'}
            runner.shell.side_effect=RuntimeError('device still authorizing')
            with self.assertRaisesRegex(RuntimeError,'authorizing'):runner.restore()
            runner.command.assert_called_once_with(['adb','-s','emulator-5556','emu','kill'],allow_failure=True,timeout=10)
            receipt=json.loads((Path(folder)/'cleanup.json').read_text())
            self.assertTrue(receipt['owned_console_closed']);self.assertIn('restore_error',receipt)

    def test_early_owned_start_failure_still_stops_the_console(self):
        with TemporaryDirectory() as folder,patch('run_mobile_tests.socket.socket') as socket:
            socket.return_value.__enter__.return_value.connect_ex.return_value=1
            runner=self.runner(folder);runner.restore()
            runner.command.assert_called_once_with(['adb','-s','emulator-5556','emu','kill'],allow_failure=True,timeout=10)

    def test_borrowed_emulator_is_never_killed(self):
        with TemporaryDirectory() as folder:
            runner=self.runner(folder,owned=None);runner.restore();runner.command.assert_not_called()

    def test_owned_launcher_is_joined_before_cleanup_succeeds(self):
        with TemporaryDirectory() as folder,patch('run_mobile_tests.socket.socket') as socket:
            socket.return_value.__enter__.return_value.connect_ex.return_value=1
            runner=self.runner(folder);runner.emulator_process=Mock()
            runner.restore();runner.emulator_process.wait.assert_called_once_with(timeout=26)
            self.assertTrue(json.loads((Path(folder)/'cleanup.json').read_text())['owned_process_exited'])

    def test_cleanup_failure_does_not_write_a_successful_receipt(self):
        with TemporaryDirectory() as folder:
            runner=self.runner(folder,owned=None)
            runner.args=SimpleNamespace(scope='related',area=['example'])
            runner.plan={'areas':{'example':[]}};runner.fingerprint='abc'
            runner.select_devices=Mock();runner.build=Mock();runner.install=Mock()
            runner.restore=Mock(side_effect=RuntimeError('cleanup fault'))
            runner.receipt=lambda status:{'status':status,'passed_cases':[]}
            with patch('run_mobile_tests.source_fingerprint',return_value='abc'):
                with self.assertRaisesRegex(RuntimeError,'cleanup fault'):runner.run()
            receipt=json.loads((Path(folder)/'result.json').read_text())
            self.assertEqual('failed',receipt['status'])

    def test_cleanup_failure_cannot_publish_the_full_gate(self):
        with TemporaryDirectory() as folder:
            root=Path(folder);gate=root/'build/verification/full-passed.json'
            gate.parent.mkdir(parents=True);gate.write_text('{"status":"passed"}')
            runner=self.runner(folder,owned=None);runner.phone='phone'
            runner.args=SimpleNamespace(scope='full');runner.plan={'phone':[],'emulator':[]}
            runner.fingerprint='abc';runner.required=set();runner.required_phone=frozenset();runner.results=[]
            for name in ('select_devices','build','install','grant_and_deny','diagnostics','ui_matrix','real_window_matrix'):
                setattr(runner,name,Mock())
            runner.restore=Mock(side_effect=RuntimeError('cleanup fault'))
            runner.receipt=lambda status:{'status':status,'scope':'full','passed_cases':[],
                'source_fingerprint':'abc','checks':dict.fromkeys(('units','docs','versions','ui_matrix','test_project'),True),
                'devices':{'phone':{'sdk':30},'emulator':{'sdk':36}}}
            with patch('run_mobile_tests.ROOT',root),patch('run_mobile_tests.source_fingerprint',return_value='abc'),patch('run_mobile_tests.check_publication_receipt'):
                with self.assertRaisesRegex(RuntimeError,'cleanup fault'):runner.run()
            self.assertFalse(gate.exists())
            self.assertEqual('failed',json.loads((root/'result.json').read_text())['status'])


    def test_angle_failure_does_not_skip_other_setting_rollback_or_owned_shutdown(self):
        with TemporaryDirectory() as folder,patch('run_mobile_tests.socket.socket') as socket:
            socket.return_value.__enter__.return_value.connect_ex.return_value=1
            runner=self.runner(folder)
            runner.angle_session=Mock()
            runner.angle_session.close.side_effect=RuntimeError('ANGLE rollback failure')
            runner.saved_emulator=dict.fromkeys(
                ('window_animation_scale','transition_animation_scale','animator_duration_scale'),'1')
            runner.saved_emulator.update(font='1.0',density='Physical density: 480',
                                         wifi='1',data='1',location='true')
            with self.assertRaisesRegex(RuntimeError,'ANGLE rollback failure'):runner.restore()
            runner.shell.assert_any_call('emulator-5556','settings','put','system','font_scale','1.0')
            runner.shell.assert_any_call('emulator-5556','wm','density','reset')
            runner.command.assert_called_once_with(['adb','-s','emulator-5556','emu','kill'],allow_failure=True,timeout=10)
            receipt=json.loads((Path(folder)/'cleanup.json').read_text())
            self.assertTrue(receipt['settings_restored'])
            self.assertTrue(receipt['owned_console_closed'])
            self.assertIn('ANGLE rollback failure',receipt['angle_restore_error'])

    def test_unrelated_disk_stress_receipt_cannot_inject_or_replace_current_run_evidence(self):
        with TemporaryDirectory() as folder,patch('run_mobile_tests.ROOT',Path(folder)):
            runner=self.runner(folder,owned=None)
            runner.args=SimpleNamespace(scope='related');runner.results=[]
            runner.checks={};runner.fingerprint='current'
            saved=Path(folder)/'build/verification/render-stress.json'
            saved.parent.mkdir(parents=True)
            saved.write_text('{"source_fingerprint":"old","status":"passed"}')
            self.assertNotIn('render_stress',runner.receipt('running')['checks'])
            current={'source_fingerprint':'current','status':'passed','run':'this-run'}
            runner.checks['render_stress']=current
            self.assertEqual(current,runner.receipt('running')['checks']['render_stress'])
            self.assertTrue(saved.is_file())

    def test_primary_error_survives_cleanup_error_and_ignores_a_stale_receipt(self):
        with TemporaryDirectory() as folder:
            runner=self.runner(folder,owned=None)
            runner.args=SimpleNamespace(scope='related',area=['example'])
            primary=RuntimeError('current original native failure')
            runner.build=Mock(side_effect=primary)
            runner.restore=Mock(side_effect=RuntimeError('current cleanup failure'))
            runner.receipt=lambda status:{'status':status,'passed_cases':[]}
            result=Path(folder)/'result.json'
            result.write_text('{"status":"failed","error":"stale different failure"}')
            with self.assertRaises(RuntimeError) as raised:runner.run()
            self.assertIs(primary,raised.exception)
            receipt=json.loads(result.read_text())
            self.assertEqual('current original native failure',receipt['error'])
            self.assertEqual('current cleanup failure',receipt['cleanup_error'])
            self.assertEqual('failed',receipt['status'])
            runner.restore.assert_called_once()

    def test_foreign_console_is_preserved_while_only_the_owned_job_is_stopped(self):
        with TemporaryDirectory() as folder:
            runner=self.runner(folder)
            class Owned:
                resource_error=None;resource_stats={};forced=False
                def __init__(self):self.wait=Mock();self.close=Mock();self.terminate=Mock()
                def owns_console(self,port):return False
                def active(self):return 0
            process=Owned();runner.emulator_process=process
            with patch('run_mobile_tests.WindowsEmulatorProcess',Owned),patch('run_mobile_tests.socket.socket') as socket:
                socket.return_value.__enter__.return_value.connect_ex.return_value=0
                runner.restore()
            runner.command.assert_not_called();process.close.assert_called_once()
            result=json.loads((Path(folder)/'cleanup.json').read_text())
            self.assertFalse(result['owned_console_verified'])
            self.assertEqual(0,result['owned_job_remaining'])

    def test_guarded_hung_owned_vm_is_terminated_without_skipping_job_close(self):
        import subprocess
        class Owned:
            resource_error=None;resource_stats={};forced=True
            def __init__(self):
                self.wait=Mock(side_effect=[subprocess.TimeoutExpired('owned',26),0])
                self.close=Mock();self.terminate=Mock()
            def owns_console(self,port):return True
            def active(self):return 0
        with TemporaryDirectory() as folder,patch('run_mobile_tests.WindowsEmulatorProcess',Owned),patch('run_mobile_tests.socket.socket') as socket:
            socket.return_value.__enter__.return_value.connect_ex.return_value=1
            runner=self.runner(folder);runner.emulator_process=Owned()
            with self.assertRaisesRegex(RuntimeError,'failed bounded shutdown'):runner.restore()
            runner.emulator_process.terminate.assert_called_once()
            runner.emulator_process.close.assert_called_once()
            self.assertTrue(json.loads((Path(folder)/'cleanup.json').read_text())['owned_process_exited'])

    def test_saved_screen_timeout_null_value_is_restored_exactly(self):
        with TemporaryDirectory() as folder:
            runner=self.runner(folder,owned=None)
            runner.saved_emulator={'screen_timeout':'null'}
            runner.restore()
            runner.shell.assert_called_once_with('emulator-5556','settings','delete','system','screen_off_timeout')

    def test_restore_shell_commands_have_a_short_timeout_even_without_a_live_transport(self):
        with TemporaryDirectory() as folder:
            runner=self.runner(folder,owned=None);runner.command.return_value=''
            runner.shell=Verification.shell.__get__(runner,Verification)
            runner.saved_emulator={'font':'1.0'};runner.restore()
            runner.command.assert_called_once_with(['adb','-s','emulator-5556','shell','settings','put','system','font_scale','1.0'],timeout=8)
            self.assertFalse(runner.restoring)

if __name__=='__main__':unittest.main()
