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
        return runner

    def test_owned_console_stops_even_when_setting_restore_raises(self):
        with TemporaryDirectory() as folder,patch('run_mobile_tests.socket.socket') as socket:
            socket.return_value.__enter__.return_value.connect_ex.return_value=1
            runner=self.runner(folder);runner.saved_emulator={'window_animation_scale':'1'}
            runner.shell.side_effect=RuntimeError('device still authorizing')
            with self.assertRaisesRegex(RuntimeError,'authorizing'):runner.restore()
            runner.command.assert_called_once_with(['adb','-s','emulator-5556','emu','kill'],allow_failure=True)
            receipt=json.loads((Path(folder)/'cleanup.json').read_text())
            self.assertTrue(receipt['owned_console_closed']);self.assertIn('restore_error',receipt)

    def test_early_owned_start_failure_still_stops_the_console(self):
        with TemporaryDirectory() as folder,patch('run_mobile_tests.socket.socket') as socket:
            socket.return_value.__enter__.return_value.connect_ex.return_value=1
            runner=self.runner(folder);runner.restore()
            runner.command.assert_called_once_with(['adb','-s','emulator-5556','emu','kill'],allow_failure=True)

    def test_borrowed_emulator_is_never_killed(self):
        with TemporaryDirectory() as folder:
            runner=self.runner(folder,owned=None);runner.restore();runner.command.assert_not_called()

    def test_owned_launcher_is_joined_before_cleanup_succeeds(self):
        with TemporaryDirectory() as folder,patch('run_mobile_tests.socket.socket') as socket:
            socket.return_value.__enter__.return_value.connect_ex.return_value=1
            runner=self.runner(folder);runner.emulator_process=Mock()
            runner.restore();runner.emulator_process.wait.assert_called_once_with(timeout=10)
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
            runner.fingerprint='abc';runner.required=set();runner.results=[]
            for name in ('select_devices','build','install','grant_and_deny','diagnostics','ui_matrix'):
                setattr(runner,name,Mock())
            runner.restore=Mock(side_effect=RuntimeError('cleanup fault'))
            runner.receipt=lambda status:{'status':status,'scope':'full','passed_cases':[],
                'source_fingerprint':'abc','checks':dict.fromkeys(('units','docs','versions','ui_matrix','test_project'),True),
                'devices':{'phone':{'sdk':30},'emulator':{'sdk':36}}}
            with patch('run_mobile_tests.ROOT',root),patch('run_mobile_tests.source_fingerprint',return_value='abc'):
                with self.assertRaisesRegex(RuntimeError,'cleanup fault'):runner.run()
            self.assertFalse(gate.exists())
            self.assertEqual('failed',json.loads((root/'result.json').read_text())['status'])

if __name__=='__main__':unittest.main()
