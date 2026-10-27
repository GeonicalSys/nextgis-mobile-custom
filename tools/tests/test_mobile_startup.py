"""Exercise native-process ownership, startup deadlines and fatal transport handling."""
import json
from pathlib import Path
import subprocess
import sys
from tempfile import TemporaryDirectory
from types import SimpleNamespace
import unittest
from unittest.mock import Mock, patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from run_mobile_tests import NativeCheckFailure, NativeStartupTimeout, PACKAGE, Verification


class NativeStartupTest(unittest.TestCase):
    def command_runner(self, folder):
        runner = Verification.__new__(Verification)
        runner.output = Path(folder)
        return runner

    def test_started_test_can_outlive_its_startup_budget(self):
        with TemporaryDirectory() as folder:
            runner = self.command_runner(folder)
            text = runner.command([sys.executable, '-u', '-c',
                'import time; print("INSTRUMENTATION_STATUS_CODE: 1", flush=True); '
                'time.sleep(.8); print("completed", flush=True)'], name='started',
                native_startup_timeout=.5, timeout=5)
            self.assertIn('completed', text)
            self.assertFalse((Path(folder) / 'started.startup-timeout.json').exists())

    def test_missing_start_aborts_only_the_owned_client_and_preserves_receipt(self):
        original_popen = subprocess.Popen
        children = []
        def launch(*args, **kwargs):
            child = original_popen(*args, **kwargs)
            children.append(child)
            return child
        with TemporaryDirectory() as folder, patch('run_mobile_tests.subprocess.Popen', side_effect=launch):
            runner = self.command_runner(folder)
            with self.assertRaisesRegex(NativeStartupTimeout, 'report a test start'):
                runner.command([sys.executable, '-u', '-c', 'import time; time.sleep(5)'],
                               name='not-started', native_startup_timeout=.1, timeout=4)
            self.assertEqual(1, len(children))
            self.assertIsNotNone(children[0].poll())
            receipt = json.loads((Path(folder) / 'not-started.startup-timeout.json').read_text())
            self.assertEqual(children[0].pid, receipt['client_pid'])
            self.assertTrue(receipt['client_exited'])
            self.assertEqual('before-test-start', receipt['phase'])
            self.assertTrue((Path(folder) / 'not-started.log').exists())

    def test_started_test_still_has_an_overall_command_timeout(self):
        with TemporaryDirectory() as folder:
            runner = self.command_runner(folder)
            with self.assertRaises(subprocess.TimeoutExpired):
                runner.command([sys.executable, '-u', '-c',
                    'import time; print("INSTRUMENTATION_STATUS_CODE: 1", flush=True); time.sleep(5)'],
                    name='runtime-timeout', native_startup_timeout=3, timeout=1.5)
            self.assertIn('INSTRUMENTATION_STATUS_CODE: 1',
                          (Path(folder) / 'runtime-timeout.log').read_text())
            self.assertFalse((Path(folder) / 'runtime-timeout.startup-timeout.json').exists())

    def test_prepare_stops_both_verification_packages_before_reset_without_global_settings(self):
        runner = Verification.__new__(Verification)
        runner.adb = 'adb'; runner.phone = 'explicit-phone'
        runner.shell = Mock(side_effect=lambda serial, *args:
                            'Success' if args == ('pm', 'clear', PACKAGE) else '')
        runner.command = Mock(return_value='')
        with patch('run_mobile_tests.phone_unlocked', return_value=True):
            runner.prepare('explicit-phone')
        commands = [call.args for call in runner.shell.call_args_list]
        stops = [args for args in commands if args[1:3] == ('am', 'force-stop')]
        self.assertEqual([('explicit-phone', 'am', 'force-stop', PACKAGE),
                          ('explicit-phone', 'am', 'force-stop', PACKAGE + '.test')], stops)
        reset = commands.index(('explicit-phone', 'pm', 'clear', PACKAGE))
        self.assertTrue(all(commands.index(stop) < reset for stop in stops))
        self.assertFalse(any(args[1] == 'settings' for args in commands))

    def phone_run(self, folder):
        runner = Verification.__new__(Verification)
        runner.args = SimpleNamespace(scope='related', area=['stationary'])
        runner.plan = {'areas': {'stationary': []}}
        runner.catalog = {}; runner.required = set(); runner.phone = 'explicit-phone'
        runner.related_phone_cases = ['example.First#works', 'example.Second#works']
        runner.results = []; runner.checks = {}; runner.devices = {}; runner.fingerprint = 'fixed'
        runner.output = Path(folder)
        runner.select_devices = Mock(); runner.build = Mock(); runner.install = Mock()
        runner.restore = Mock()
        return runner

    def test_phone_timeout_stops_before_the_next_class_and_runs_cleanup(self):
        with TemporaryDirectory() as folder:
            runner = self.phone_run(folder)
            runner.native = Mock(side_effect=subprocess.TimeoutExpired(['owned-adb-client'], 1))
            with self.assertRaises(subprocess.TimeoutExpired):
                runner.run()
            self.assertEqual(1, runner.native.call_count)
            runner.restore.assert_called_once()
            self.assertEqual('failed', json.loads((Path(folder) / 'result.json').read_text())['status'])

    def test_phone_native_assertion_can_collect_the_next_independent_case_but_cannot_pass(self):
        with TemporaryDirectory() as folder:
            runner = self.phone_run(folder)
            runner.native = Mock(side_effect=[NativeCheckFailure('native assertion'), None])
            with self.assertRaisesRegex(RuntimeError, 'native assertion'):
                runner.run()
            self.assertEqual(2, runner.native.call_count)
            runner.restore.assert_called_once()
            self.assertEqual('failed', json.loads((Path(folder) / 'result.json').read_text())['status'])

    def test_intentional_cross_process_phase_preserves_its_workspace(self):
        with TemporaryDirectory() as folder:
            runner = Verification.__new__(Verification)
            runner.args = SimpleNamespace(scope='related'); runner.plan = {}
            case = 'example.Test#works'; runner.catalog = {'example.Test': {case}}
            runner.adb = 'adb'; runner.phone = 'explicit-phone'; runner.output = Path(folder)
            runner.results = []; runner.checks = {}; runner.devices = {}; runner.fingerprint = 'fixed'
            runner.prepare = Mock()
            runner.command = Mock(return_value=(
                'INSTRUMENTATION_STATUS: class=example.Test\nINSTRUMENTATION_STATUS: test=works\n'
                'INSTRUMENTATION_STATUS_CODE: 1\nINSTRUMENTATION_STATUS: class=example.Test\n'
                'INSTRUMENTATION_STATUS: test=works\nINSTRUMENTATION_STATUS_CODE: 0\n'
                'OK (1 test)\nINSTRUMENTATION_CODE: -1\n'))
            runner.native('explicit-phone', [case], 'controlled-restart', reset=False)
            runner.prepare.assert_not_called()
            self.assertEqual(60, runner.command.call_args.kwargs['native_startup_timeout'])


if __name__ == '__main__':
    unittest.main()
