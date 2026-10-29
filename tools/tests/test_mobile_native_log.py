import io
import json
from pathlib import Path
import subprocess
import sys
from tempfile import TemporaryDirectory
import threading
import unittest
from unittest.mock import Mock, patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import mobile_native_log as log


def current_user(user=0):
    return subprocess.CompletedProcess([], 0, (str(user) + '\n').encode(), b'')


class Process:
    def __init__(self, data=b'', *, stream=None, returncode=None, timeout=False):
        self.stdout = stream if stream is not None else io.BytesIO(data)
        self.returncode = returncode
        self.timeout = timeout
        self.terminated = 0
        self.killed = 0

    def poll(self):
        return self.returncode

    def terminate(self):
        self.terminated += 1
        if hasattr(self.stdout, 'release'):
            self.stdout.release.set()
        if not self.timeout:
            self.returncode = -15

    def kill(self):
        self.killed += 1
        self.returncode = -9
        if hasattr(self.stdout, 'release'):
            self.stdout.release.set()

    def wait(self, timeout):
        if self.returncode is None:
            raise subprocess.TimeoutExpired('owned-adb-client', timeout)
        return self.returncode


class NativeLogTest(unittest.TestCase):
    def start(self, path, process, *, query=None, user=0):
        query = query or subprocess.CompletedProcess([], 0,
                ('package:' + log.VERIFICATION_PACKAGE + ' uid:10345\n').encode(), b'')
        with patch.object(log.subprocess, 'run', side_effect=[current_user(user), query]) as run, \
                patch.object(log.subprocess, 'Popen', return_value=process) as popen:
            capture = log.start_native_log_capture('adb-path', 'phone:5555', path)
        return capture, run, popen

    def test_fixed_uid_command_and_actual_four_mib_head_tail_bound(self):
        data = b'H' * log.HEAD_BYTES + b'DROP' * 200000 + bytes(range(256)) * (log.TAIL_BYTES // 256 + 1)
        with TemporaryDirectory() as root:
            path = Path(root) / 'log.txt'
            process = Process(data)
            capture, run, popen = self.start(path, process)
            metadata = capture.stop()
            self.assertEqual(data[:log.HEAD_BYTES] + data[-log.TAIL_BYTES:], path.read_bytes())
            self.assertEqual(log.CAP_BYTES, path.stat().st_size)
            self.assertEqual(len(data), metadata['bytes_seen'])
            self.assertEqual(log.CAP_BYTES, metadata['bytes_retained'])
            self.assertEqual(len(data) - log.CAP_BYTES, metadata['bytes_dropped'])
            self.assertTrue(metadata['truncated'])
            self.assertEqual('head_then_tail', metadata['layout'])
            self.assertEqual(0, metadata['tail_cursor'])
            self.assertEqual('captured', metadata['status'])
            self.assertTrue(metadata['reader_stopped'] and metadata['file_closed'] and metadata['reader_stream_closed'])
            self.assertEqual(['adb-path', '-s', 'phone:5555', 'shell', 'cmd', 'package', 'list',
                              'packages', '--user', '0', '-U', log.VERIFICATION_PACKAGE], run.call_args.args[0])
            self.assertEqual(['adb-path', '-s', 'phone:5555', 'shell', 'am', 'get-current-user'],
                             run.call_args_list[0].args[0])
            self.assertEqual(2, run.call_count)
            self.assertEqual(10, run.call_args.kwargs['timeout'])
            self.assertEqual(['adb-path', '-s', 'phone:5555', 'logcat', '--uid=10345', '-T1', '-vthreadtime'], popen.call_args.args[0])
            self.assertEqual(subprocess.DEVNULL, popen.call_args.kwargs['stdin'])
            self.assertEqual(subprocess.STDOUT, popen.call_args.kwargs['stderr'])
            self.assertEqual(0, popen.call_args.kwargs['bufsize'])
            self.assertEqual(getattr(subprocess, 'CREATE_NO_WINDOW', 0), popen.call_args.kwargs['creationflags'])
            self.assertEqual(1, process.terminated)
            self.assertEqual(0, process.killed)

    def test_small_partial_file_and_idempotent_independent_stop_metadata(self):
        with TemporaryDirectory() as root:
            path = Path(root) / 'log.txt'
            process = Process('частичный журнал\n'.encode())
            capture, _, _ = self.start(path, process)
            first = capture.stop()
            self.assertEqual('частичный журнал\n'.encode(), path.read_bytes())
            self.assertEqual(0, first['bytes_dropped'])
            self.assertFalse(first['truncated'])
            self.assertEqual(10345, first['uid'])
            self.assertEqual(log.VERIFICATION_PACKAGE, first['package'])
            self.assertTrue(first['started_at'] and first['stopped_at'])
            first['errors'].append({'stage': 'caller_mutation'})
            self.assertEqual([], capture.stop()['errors'])
            self.assertEqual(1, process.terminated)

    def test_uid_requires_one_exact_package_line_and_app_uid(self):
        good = ('package:' + log.VERIFICATION_PACKAGE + ' uid:10345').encode()
        invalid = [b'', good + b' trailing', good + b'\n' + good,
                   good + b'\npackage:com.other uid:10001\n',
                   good.replace(b'verification.debug', b'verificationXdebug'),
                   good.replace(log.VERIFICATION_PACKAGE.encode(), b'com.other'),
                   good.replace(b'10345', b'0'), good.replace(b'10345', b'9999'),
                   good.replace(b'10345', b'2147483648'), good.replace(b'10345', b'-1'),
                   good.replace(b'10345', b'unknown')]
        for data in invalid:
            with self.subTest(data=data), TemporaryDirectory() as root:
                path = Path(root) / 'log.txt'
                with patch.object(log.subprocess, 'run', side_effect=[current_user(), subprocess.CompletedProcess([], 0, data, b'')]), \
                        patch.object(log.subprocess, 'Popen') as popen:
                    with self.assertRaises(log.NativeLogStartError) as error:
                        log.start_native_log_capture('adb', 'phone', path)
                self.assertEqual('uid_query_missing' if data == b'' else 'uid_query_malformed', error.exception.code)
                popen.assert_not_called()
                self.assertFalse(path.exists())

    def test_query_exit_stderr_and_timeout_are_fixed_diagnostic_failures(self):
        cases = [subprocess.CompletedProcess([], 1, b'', b'SECRET device message'),
                 subprocess.CompletedProcess([], 0, b'ignored', b'SECRET'),
                 subprocess.CompletedProcess([], 0, b'x' * 4097, b'')]
        with TemporaryDirectory() as root:
            path = Path(root) / 'log.txt'
            for result in cases:
                with patch.object(log.subprocess, 'run', side_effect=[current_user(), result]), patch.object(log.subprocess, 'Popen') as popen:
                    with self.assertRaises(log.NativeLogStartError) as error:
                        log.start_native_log_capture('adb', 'phone', path)
                    self.assertEqual('uid_query_failed', error.exception.code)
                    self.assertNotIn('SECRET', str(error.exception))
                    popen.assert_not_called()
            with patch.object(log.subprocess, 'run', side_effect=[current_user(), subprocess.TimeoutExpired('SECRET', 10)]):
                with self.assertRaises(log.NativeLogStartError) as error:
                    log.start_native_log_capture('adb', 'phone', path)
                self.assertEqual('uid_query_timeout', error.exception.code)
                self.assertNotIn('SECRET', str(error.exception))

    def test_invalid_target_or_existing_file_never_runs_adb(self):
        with TemporaryDirectory() as root, patch.object(log.subprocess, 'run') as run:
            path = Path(root) / 'log.txt'
            path.write_bytes(b'original evidence')
            with self.assertRaises(FileExistsError):
                log.start_native_log_capture('adb', 'phone', path)
            self.assertEqual(b'original evidence', path.read_bytes())
            for serial in ['', '-e', 'phone extra', None]:
                with self.assertRaises(ValueError):
                    log.start_native_log_capture('adb', serial, Path(root) / 'new.txt')
            with self.assertRaises(ValueError):
                log.start_native_log_capture('adb', 'phone', Path('relative.txt'))
            run.assert_not_called()

    def test_reader_failure_preserves_partial_bytes_and_never_raises_from_stop(self):
        class FailingStream:
            closed = False
            first = True
            def read(self, size):
                if self.first:
                    self.first = False
                    return b'partial log\n'
                raise OSError('SECRET reader/path')
            def close(self):
                self.closed = True
        with TemporaryDirectory() as root:
            path = Path(root) / 'log.txt'
            capture, _, _ = self.start(path, Process(stream=FailingStream()))
            metadata = capture.stop()
            self.assertEqual(b'partial log\n', path.read_bytes())
            self.assertEqual('diagnostic_failure', metadata['status'])
            self.assertIn({'stage': 'stream', 'error_type': 'OSError'}, metadata['errors'])
            self.assertNotIn('SECRET', json.dumps(metadata))
            self.assertTrue(metadata['reader_stopped'] and metadata['file_closed'])

    def test_wait_timeout_kills_only_owned_client_and_joins_blocked_reader(self):
        class BlockingStream:
            closed = False
            def __init__(self):
                self.release = threading.Event()
            def read(self, size):
                if not self.release.wait(2):
                    raise TimeoutError('fake reader did not receive owned stop')
                return b''
            def close(self):
                self.closed = True
        with TemporaryDirectory() as root:
            process = Process(stream=BlockingStream(), timeout=True)
            capture, run, popen = self.start(Path(root) / 'log.txt', process)
            metadata = capture.stop()
            self.assertEqual(1, process.terminated)
            self.assertEqual(1, process.killed)
            self.assertEqual(-9, metadata['client_returncode'])
            self.assertTrue(metadata['reader_stopped'] and metadata['reader_stream_closed'] and metadata['file_closed'])
            self.assertEqual(2, run.call_count)
            self.assertEqual(1, popen.call_count)

    def test_unexpected_client_exit_is_not_a_successful_continuous_capture(self):
        with TemporaryDirectory() as root:
            process = Process(b'uid-filter error\n', returncode=1)
            capture, _, _ = self.start(Path(root) / 'log.txt', process)
            metadata = capture.stop()
            self.assertEqual('diagnostic_failure', metadata['status'])
            self.assertEqual(1, metadata['client_returncode'])
            self.assertIn({'stage': 'client_exit', 'error_type': 'NonzeroExit'}, metadata['errors'])
            self.assertEqual(0, process.terminated + process.killed)

    def test_finalize_error_keeps_partial_capture_and_closes_handles(self):
        with TemporaryDirectory() as root:
            path = Path(root) / 'log.txt'
            capture, _, _ = self.start(path, Process(b'partial'))
            with patch.object(capture._window, 'finish', side_effect=OSError('SECRET sink')):
                metadata = capture.stop()
            self.assertEqual(b'partial', path.read_bytes())
            self.assertEqual('diagnostic_failure', metadata['status'])
            self.assertIn({'stage': 'finalize', 'error_type': 'OSError'}, metadata['errors'])
            self.assertTrue(metadata['file_closed'] and metadata['reader_stream_closed'])
            self.assertNotIn('SECRET', json.dumps(metadata))

    def test_popen_and_reader_start_errors_keep_file_and_stop_owned_resources(self):
        query = subprocess.CompletedProcess([], 0, ('package:' + log.VERIFICATION_PACKAGE + ' uid:10345\n').encode(), b'')
        with TemporaryDirectory() as root:
            path = Path(root) / 'start-failed.txt'
            with patch.object(log.subprocess, 'run', side_effect=[current_user(), query]), \
                    patch.object(log.subprocess, 'Popen', side_effect=OSError('SECRET binary')):
                with self.assertRaises(log.NativeLogStartError) as error:
                    log.start_native_log_capture('adb', 'phone', path)
            self.assertEqual('logcat_start_failed', error.exception.code)
            self.assertEqual(b'', path.read_bytes())
            process = Process()
            with patch.object(log.subprocess, 'run', side_effect=[current_user(), query]), \
                    patch.object(log.subprocess, 'Popen', return_value=process), \
                    patch.object(threading.Thread, 'start', side_effect=RuntimeError('SECRET thread')):
                with self.assertRaises(log.NativeLogStartError) as error:
                    log.start_native_log_capture('adb', 'phone', Path(root) / 'reader-failed.txt')
            self.assertEqual('reader_start_failed', error.exception.code)
            self.assertEqual(1, process.terminated)
            self.assertTrue(process.stdout.closed)

    def test_actual_substring_result_selects_exact_app_not_instrumentation_sibling(self):
        app = b'package:com.nextgis.mobile.verification.debug uid:10426'
        test = b'package:com.nextgis.mobile.verification.debug.test uid:10428'
        for data in [test + b'\r\n' + app + b'\r\n', app + b'\n' + test + b'\n']:
            with self.subTest(data=data), TemporaryDirectory() as root:
                capture, run, popen = self.start(Path(root) / 'log.txt', Process(b'app log'),
                                               query=subprocess.CompletedProcess([], 0, data, b''))
                metadata = capture.stop()
                self.assertEqual(10426, metadata['uid'])
                self.assertEqual(0, metadata['android_user'])
                self.assertIn('--uid=10426', popen.call_args.args[0])
                self.assertNotIn('--uid=10428', popen.call_args.args[0])
                self.assertEqual(['--user', '0', '-U', 'com.nextgis.mobile.verification.debug'], run.call_args.args[0][-4:])

    def test_explicit_current_secondary_user_selects_its_app_uid(self):
        data = b'package:com.nextgis.mobile.verification.debug.test uid:1010428\npackage:com.nextgis.mobile.verification.debug uid:1010426\n'
        with TemporaryDirectory() as root:
            capture, run, popen = self.start(Path(root) / 'log.txt', Process(), user=10,
                                           query=subprocess.CompletedProcess([], 0, data, b''))
            metadata = capture.stop()
            self.assertEqual(10, metadata['android_user'])
            self.assertEqual(1010426, metadata['uid'])
            self.assertEqual(['--user', '10', '-U', 'com.nextgis.mobile.verification.debug'], run.call_args.args[0][-4:])
            self.assertIn('--uid=1010426', popen.call_args.args[0])

    def test_invalid_or_denied_current_user_never_queries_packages_or_starts_logcat(self):
        for data in [b'', b'-1\n', b'21474\n', b'0\n10\n', b'Current user: 0\n', b'0 SECRET']:
            with self.subTest(data=data), TemporaryDirectory() as root:
                with patch.object(log.subprocess, 'run', return_value=subprocess.CompletedProcess([], 0, data, b'')) as run, \
                        patch.object(log.subprocess, 'Popen') as popen:
                    with self.assertRaises(log.NativeLogStartError) as error:
                        log.start_native_log_capture('adb', 'phone', Path(root) / 'log.txt')
                self.assertEqual('current_user_malformed', error.exception.code)
                self.assertEqual(1, run.call_count)
                popen.assert_not_called()
        with TemporaryDirectory() as root, patch.object(log.subprocess, 'run', side_effect=subprocess.TimeoutExpired('SECRET', 10)):
            with self.assertRaises(log.NativeLogStartError) as error:
                log.start_native_log_capture('adb', 'phone', Path(root) / 'log.txt')
            self.assertEqual('current_user_query_timeout', error.exception.code)
            self.assertNotIn('SECRET', str(error.exception))
        with TemporaryDirectory() as root, patch.object(log.subprocess, 'run', return_value=
                subprocess.CompletedProcess([], 1, b'', b'SECRET current-user permission denial')) as run:
            with self.assertRaises(log.NativeLogStartError) as error:
                log.start_native_log_capture('adb', 'phone', Path(root) / 'log.txt')
            self.assertEqual('current_user_query_failed', error.exception.code)
            self.assertEqual(1, run.call_count)
            self.assertNotIn('SECRET', str(error.exception))

    def test_missing_duplicate_conflicting_and_wrong_profile_uid_fail_closed(self):
        app = b'package:com.nextgis.mobile.verification.debug uid:10426'
        sibling = b'package:com.nextgis.mobile.verification.debug.test uid:10428'
        cases = [(sibling + b'\n', b'', 0, 'uid_query_missing'),
                 (app + b'\n' + app + b'\n', b'', 0, 'uid_query_malformed'),
                 (app + b'\n' + app.replace(b'10426', b'10427') + b'\n', b'', 0, 'uid_query_malformed'),
                 (app + b'\n' + sibling.replace(b'10428', b'10426') + b'\n', b'', 0, 'uid_query_conflict'),
                 (app.replace(b'10426', b'1010426') + b'\n', b'', 0, 'uid_user_mismatch'),
                 (app + b'\n', b'SECRET Shell does not have permission to access user 10', 1, 'uid_query_failed')]
        for data, stderr, code, expected in cases:
            with self.subTest(expected=expected, data=data), TemporaryDirectory() as root:
                with patch.object(log.subprocess, 'run', side_effect=[current_user(), subprocess.CompletedProcess([], code, data, stderr)]) as run, \
                        patch.object(log.subprocess, 'Popen') as popen:
                    with self.assertRaises(log.NativeLogStartError) as error:
                        log.start_native_log_capture('adb', 'phone', Path(root) / 'log.txt')
                self.assertEqual(expected, error.exception.code)
                self.assertNotIn('SECRET', str(error.exception))
                self.assertEqual(2, run.call_count)
                self.assertIn('--user', run.call_args.args[0])
                self.assertNotIn('all', run.call_args.args[0])
                popen.assert_not_called()


if __name__ == '__main__':
    unittest.main()
