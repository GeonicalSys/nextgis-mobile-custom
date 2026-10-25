import sys
from pathlib import Path
from tempfile import TemporaryDirectory
from types import SimpleNamespace
import unittest
from unittest.mock import Mock

sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from run_mobile_tests import Verification

CASE='example.Test#works'

def output(code):
    return ('INSTRUMENTATION_STATUS: class=example.Test\nINSTRUMENTATION_STATUS: test=works\n'
            'INSTRUMENTATION_STATUS_CODE: 1\nINSTRUMENTATION_STATUS: class=example.Test\n'
            f'INSTRUMENTATION_STATUS: test=works\nINSTRUMENTATION_STATUS_CODE: {code}\n'
            + ('OK (1 test)\n' if code==0 else 'FAILURES!!!\n')+'INSTRUMENTATION_CODE: -1\n')

class NativeFailureCollectionTest(unittest.TestCase):
    def runner(self,folder,scope='full'):
        runner=Verification.__new__(Verification)
        runner.args=SimpleNamespace(scope=scope)
        runner.plan={};runner.catalog={'example.Test':{CASE}}
        runner.adb='adb';runner.phone='phone';runner.output=Path(folder)
        runner.results=[];runner.checks={};runner.devices={};runner.fingerprint='abc'
        runner.prepare=Mock();runner.command=Mock(side_effect=[output(-2),output(0)])
        return runner

    def test_full_preserves_failure_and_runs_the_next_independent_check(self):
        with TemporaryDirectory() as folder:
            runner=self.runner(folder)
            self.assertTrue(runner.native('phone',[CASE],'first')['failed'])
            self.assertEqual([CASE],runner.native('phone',[CASE],'second')['passed'])
            self.assertEqual(2,runner.prepare.call_count)
            self.assertEqual(2,len(runner.results))
            with self.assertRaisesRegex(RuntimeError,'first'):runner.require_native_success()

    def test_related_stays_fail_fast(self):
        with TemporaryDirectory() as folder:
            runner=self.runner(folder,'related')
            with self.assertRaises(RuntimeError):runner.native('phone',[CASE],'first')
            self.assertFalse(runner.results)

    def test_transport_or_setup_failures_still_abort_full(self):
        with TemporaryDirectory() as folder:
            runner=self.runner(folder);runner.command=Mock(side_effect=RuntimeError('Device unavailable'))
            with self.assertRaisesRegex(RuntimeError,'Device unavailable'):runner.native('phone',[CASE],'first')
            self.assertFalse(runner.results)

    def test_successful_roster_is_not_rejected(self):
        with TemporaryDirectory() as folder:
            runner=self.runner(folder);runner.command=Mock(return_value=output(0))
            runner.native('phone',[CASE],'first');runner.require_native_success()

if __name__=='__main__':unittest.main()
