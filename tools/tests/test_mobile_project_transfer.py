import hashlib
from pathlib import Path
import sys
from tempfile import TemporaryDirectory
from types import SimpleNamespace
import unittest
from unittest.mock import Mock, patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from run_mobile_tests import Verification, PACKAGE


class ProjectTransferTest(unittest.TestCase):
    def runner(self, folder):
        runner = Verification.__new__(Verification)
        runner.adb = 'adb'
        runner.output = Path(folder)
        runner.project_fixture = Path(folder) / 'policy.json'
        runner.project_fixture.write_text('{"name":"Полевые слои"}', encoding='utf-8')
        digest = hashlib.sha256(runner.project_fixture.read_bytes()).hexdigest()
        runner.shell = Mock(side_effect=['/data/user/0/' + PACKAGE, '', digest + '  files/test-project-layers.json'])
        runner.command = Mock()
        return runner

    def test_only_the_verification_uid_writes_a_hash_checked_private_file(self):
        with TemporaryDirectory() as folder:
            runner = self.runner(folder)
            destination = runner.stage_project_fixture('tablet-wifi')
            self.assertEqual('/data/user/0/' + PACKAGE + '/files/test-project-layers.json', destination)
            runner.command.assert_called_once_with(
                ['adb', '-s', 'tablet-wifi', 'exec-in', 'run-as', PACKAGE, 'tee', 'files/test-project-layers.json'],
                input_text=runner.project_fixture.read_text(encoding='utf-8'))
            self.assertEqual(('tablet-wifi', 'run-as', PACKAGE, 'sha256sum', 'files/test-project-layers.json'),
                             runner.shell.call_args.args)

    def test_another_package_or_unexpected_directory_cannot_receive_the_fixture(self):
        for directory in ('/sdcard/Android/data/' + PACKAGE, '/data/user/0/com.nextgis.mobile.geonical',
                          '/data/user/0/' + PACKAGE + '/../../another'):
            with self.subTest(directory=directory), TemporaryDirectory() as folder:
                runner = self.runner(folder)
                runner.shell = Mock(return_value=directory)
                with self.assertRaisesRegex(RuntimeError, 'Unexpected verification private directory'):
                    runner.stage_project_fixture('tablet-wifi')
                runner.command.assert_not_called()

    def test_truncated_or_changed_payload_is_rejected(self):
        with TemporaryDirectory() as folder:
            runner = self.runner(folder)
            runner.shell.side_effect = ['/data/user/0/' + PACKAGE, '', 'bad-hash  files/test-project-layers.json']
            with self.assertRaisesRegex(RuntimeError, 'SHA-256'):
                runner.stage_project_fixture('tablet-wifi')

    def test_transfer_error_stops_before_readback_or_instrumentation(self):
        with TemporaryDirectory() as folder:
            runner = self.runner(folder)
            runner.command.side_effect = RuntimeError('transfer disconnected')
            with self.assertRaisesRegex(RuntimeError, 'transfer disconnected'):
                runner.stage_project_fixture('tablet-wifi')
            self.assertEqual(2, runner.shell.call_count)

    def test_command_passes_unicode_stdin_without_shell_interpolation(self):
        with TemporaryDirectory() as folder:
            runner = Verification.__new__(Verification)
            runner.output = Path(folder)
            for name in (None, 'with-log'):
                with self.subTest(name=name), patch('run_mobile_tests.subprocess.run') as run:
                    run.return_value = SimpleNamespace(returncode=0, stdout='ok')
                    runner.command(['adb', 'exec-in', 'run-as', PACKAGE, 'tee', 'files/policy.json'],
                                   name=name, input_text='Полевые слои')
                    self.assertEqual('Полевые слои', run.call_args.kwargs['input'])
                    self.assertEqual('utf-8', run.call_args.kwargs['encoding'])
                    self.assertNotIn('shell', run.call_args.kwargs)


if __name__ == '__main__':
    unittest.main()
