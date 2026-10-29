import sys
from pathlib import Path
from tempfile import TemporaryDirectory
from types import SimpleNamespace
import unittest
from unittest.mock import Mock, patch

sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from run_mobile_tests import Verification, NativeCheckFailure
from mobile_sqlite_evidence import SQLITE_CLASS

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
        runner.emulator='emulator-5554'
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

    def test_related_leaf_raises_but_preserves_completed_failure(self):
        with TemporaryDirectory() as folder:
            runner=self.runner(folder,'related')
            with self.assertRaises(RuntimeError):runner.native('phone',[CASE],'first')
            self.assertEqual(1,len(runner.results))
            self.assertTrue(runner.results[0]['failed'])

    def test_related_isolated_class_finishes_independent_methods_and_retains_failure(self):
        with TemporaryDirectory() as folder:
            runner=self.runner(folder,'related')
            second='example.Test#second'
            runner.catalog={'example.Test':{CASE,second}}
            runner.plan={'isolated_methods':['example.Test']}
            # Sorted second first: one completed assertion failure must not skip the next method.
            runner.command=Mock(side_effect=[output(-2).replace('works','second'),output(0)])
            with self.assertRaises(NativeCheckFailure): runner.native('phone',['example.Test'],'class')
            self.assertEqual(2,runner.prepare.call_count)
            self.assertEqual(2,len(runner.results))
            self.assertTrue(runner.results[0]['failed'])
            self.assertEqual([CASE],runner.results[1]['passed'])
            with self.assertRaisesRegex(RuntimeError,'second'):runner.require_native_success()

    def test_related_isolated_class_stops_on_transport_error(self):
        with TemporaryDirectory() as folder:
            runner=self.runner(folder,'related')
            runner.catalog={'example.Test':{CASE,'example.Test#second'}}
            runner.plan={'isolated_methods':['example.Test']}
            runner.command=Mock(side_effect=RuntimeError('Device unavailable'))
            with self.assertRaisesRegex(RuntimeError,'Device unavailable'):
                runner.native('phone',['example.Test'],'class')
            self.assertEqual(1,runner.prepare.call_count)
            self.assertFalse(runner.results)

    def test_sqlite_log_is_finalized_before_artifact_collection_and_original_failure_survives(self):
        with TemporaryDirectory() as folder:
            runner=self.runner(folder,'related');case=SQLITE_CLASS+'#works'
            runner.catalog={SQLITE_CLASS:{case}};order=[]
            capture=Mock();capture.stop.side_effect=lambda:order.append('stop-log') or {'status':'captured'}
            runner.native_log_capture=Mock(return_value=capture)
            runner.command=Mock(return_value=output(-2).replace('example.Test',SQLITE_CLASS))
            def collect(*args,**kwargs):
                order.append('collect');return {'status':'absent_verified'}
            with patch('run_mobile_tests.collect_sqlite_evidence',side_effect=collect),patch('run_mobile_tests.collect_style_images'):
                with self.assertRaises(NativeCheckFailure):runner.native('phone',[case],'case')
            self.assertEqual(['stop-log','collect'],order)
            capture.stop.assert_called_once()
            self.assertEqual('captured',runner.checks['native_uid_logs'][0]['status'])
            self.assertTrue(runner.results[0]['failed'])

    def test_sqlite_log_cleanup_error_does_not_replace_original_transport_failure(self):
        with TemporaryDirectory() as folder:
            runner=self.runner(folder,'related');case=SQLITE_CLASS+'#works'
            runner.catalog={SQLITE_CLASS:{case}}
            capture=Mock();capture.stop.side_effect=OSError('stop failed')
            runner.native_log_capture=Mock(return_value=capture)
            runner.command=Mock(side_effect=RuntimeError('original transport failure'))
            with patch('run_mobile_tests.collect_sqlite_evidence',return_value={'status':'absent_verified'}):
                with self.assertRaisesRegex(RuntimeError,'original transport failure'):
                    runner.native('phone',[case],'case')
            capture.stop.assert_called_once()
            self.assertEqual('diagnostic_failure',runner.checks['native_uid_logs'][0]['status'])

    def test_sqlite_log_start_failure_keeps_fixed_code_without_masking_test_result(self):
        from mobile_native_log import NativeLogStartError
        with TemporaryDirectory() as folder:
            runner=self.runner(folder,'related');case=SQLITE_CLASS+'#works'
            runner.catalog={SQLITE_CLASS:{case}}
            runner.native_log_capture=Mock(side_effect=NativeLogStartError('uid_missing'))
            runner.command=Mock(return_value=output(0).replace('example.Test',SQLITE_CLASS))
            with patch('run_mobile_tests.collect_sqlite_evidence',return_value={'status':'absent_verified'}),patch('run_mobile_tests.collect_style_images'):
                self.assertEqual([case],runner.native('phone',[case],'case')['passed'])
            state=runner.checks['native_uid_logs'][0]
            self.assertEqual('diagnostic_failure',state['status'])
            self.assertEqual('uid_missing',state['error_code'])
            self.assertNotIn('error',state)

    def test_transport_or_setup_failures_still_abort_full(self):
        with TemporaryDirectory() as folder:
            runner=self.runner(folder);runner.command=Mock(side_effect=RuntimeError('Device unavailable'))
            with self.assertRaisesRegex(RuntimeError,'Device unavailable'):runner.native('phone',[CASE],'first')
            self.assertFalse(runner.results)

    def test_successful_roster_is_not_rejected(self):
        with TemporaryDirectory() as folder:
            runner=self.runner(folder);runner.command=Mock(return_value=output(0))
            runner.native('phone',[CASE],'first');runner.require_native_success()

    def test_full_keeps_missing_malformed_or_incomplete_receiver_as_failure_and_continues(self):
        import gzip
        for bodies in ([],[b'bad',b'bad'],[gzip.compress(b'other'),gzip.compress(b'other')]):
            with self.subTest(bodies=len(bodies)),TemporaryDirectory() as folder:
                runner=self.runner(folder);runner.check_diagnostic_receiver(bodies)
                self.assertFalse(runner.checks['process_exit_delivery'])
                self.assertTrue((Path(folder)/'diagnostic-receiver.json').is_file())
                runner.command=Mock(return_value=output(0))
                self.assertEqual([CASE],runner.native('phone',[CASE],'independent')['passed'])
                with self.assertRaisesRegex(RuntimeError,'diagnostic-receiver'):runner.require_native_success()

    def test_receiver_requires_every_original_identity_and_native_backtrace_marker(self):
        import gzip
        with TemporaryDirectory() as folder:
            runner=self.runner(folder)
            runner.check_diagnostic_receiver([gzip.compress(b'process_exit_fallback verification-before-kill'),
                                              gzip.compress(b'verification-before-native-abort native_frames')])
            self.assertTrue(runner.checks['process_exit_delivery']);runner.require_native_success()
        with TemporaryDirectory() as folder:
            runner=self.runner(folder,'related')
            with self.assertRaises(NativeCheckFailure):runner.check_diagnostic_receiver([])

if __name__=='__main__':unittest.main()
