import base64
import hashlib
import json
from pathlib import Path
import subprocess
import sys
from tempfile import TemporaryDirectory
from types import SimpleNamespace
import unittest
from unittest.mock import Mock, patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from mobile_sqlite_evidence import collect_sqlite_evidence, SQLITE_CLASS, VERIFICATION_PACKAGE, PNG_SIGNATURE
from run_mobile_tests import Verification, NativeCheckFailure

CASE = SQLITE_CLASS + '#sqliteSourceRendersAllOriginalPolygonsCategoriesAndLabelsWithoutGlobalFeatures'
FINGERPRINT = 'a' * 64
JSON = b'{"schema_version":1,"focus_rows":[{"id":"31","geometry":"Point"}]}'
PNG = base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jWZkAAAAASUVORK5CYII=')


class SqliteEvidenceTest(unittest.TestCase):
    def collect(self, root, files=None, *, frames=None, declared=None):
        files = files if files is not None else {'failure.json': JSON, 'failure.png': PNG}
        calls = []
        def run(argv, **kwargs):
            self.assertEqual(['adb', '-s', 'phone', 'exec-out', 'run-as', VERIFICATION_PACKAGE], argv[:6])
            operation, remote = argv[6], argv[-1]
            self.assertIn(operation, ('stat', 'cat'))
            self.assertIn(remote, ('files/native-sqlite-qa/failure.json', 'files/native-sqlite-qa/failure.png'))
            self.assertEqual(10, kwargs['timeout']); self.assertTrue(kwargs['capture_output'])
            calls.append((operation, remote))
            name = remote.rsplit('/', 1)[1]
            if (operation, name) in (frames or {}):
                code, stdout, stderr = frames[(operation, name)]
                return subprocess.CompletedProcess(argv, code, stdout, stderr)
            if name not in files:
                return subprocess.CompletedProcess(argv, 1, b'', f"{operation}: '{remote}': No such file or directory\n".encode())
            data = str((declared or {}).get(name, len(files[name]))).encode() if operation == 'stat' else files[name]
            return subprocess.CompletedProcess(argv, 0, data, b'')
        with patch('mobile_sqlite_evidence.subprocess.run', side_effect=run):
            result = collect_sqlite_evidence('adb', 'phone', Path(root) / 'evidence', case=CASE, source_fingerprint=FINGERPRINT)
        return result, calls

    def test_retains_only_verified_bytes_with_exact_case_fingerprint_and_hash(self):
        with TemporaryDirectory() as root:
            receipt, calls = self.collect(root)
            self.assertEqual('retained', receipt['status']); self.assertEqual(CASE, receipt['case'])
            self.assertEqual(FINGERPRINT, receipt['source_fingerprint']); self.assertEqual(4, len(calls))
            for row in receipt['artifacts']:
                data = (Path(root) / 'evidence' / row['path']).read_bytes()
                self.assertEqual(row['bytes'], len(data)); self.assertEqual(row['sha256'], hashlib.sha256(data).hexdigest())
            self.assertEqual(receipt, json.loads((Path(root) / 'evidence/index.json').read_text(encoding='utf-8')))

    def test_exit_zero_cat_error_is_verified_absence_never_a_retained_artifact(self):
        with TemporaryDirectory() as root:
            error = b'cat: files/native-sqlite-qa/failure.json: No such file or directory\n'
            receipt, calls = self.collect(root, frames={('cat', 'failure.json'): (0, error, b'')})
            row = receipt['artifacts'][0]
            self.assertEqual('absent_verified', row['status']); self.assertEqual('cat', row['stage'])
            self.assertNotIn('sha256', row); self.assertFalse((Path(root) / 'evidence/failure.json').exists())
            self.assertEqual('partial', receipt['status']); self.assertEqual('retained', receipt['artifacts'][1]['status'])

    def test_confirmed_stat_absence_does_not_attempt_cat(self):
        with TemporaryDirectory() as root:
            receipt, calls = self.collect(root, {})
            self.assertEqual('absent_verified', receipt['status'])
            self.assertTrue(all(operation == 'stat' for operation, _ in calls))

    def test_transport_permission_and_wrong_path_errors_are_not_absence(self):
        examples = [(1, b'', b'error: device offline'), (0, b'error: device offline', b''),
                    (0, b'cat: files/native-sqlite-qa/other.json: No such file or directory', b''),
                    (0, b'', b'cat: files/native-sqlite-qa/failure.json: Permission denied')]
        for frame in examples:
            with self.subTest(frame=frame), TemporaryDirectory() as root:
                receipt, _ = self.collect(root, frames={('cat', 'failure.json'): frame})
                self.assertEqual('diagnostic_failure', receipt['status'])
                self.assertEqual('diagnostic_failure', receipt['artifacts'][0]['status'])
                self.assertFalse((Path(root) / 'evidence/failure.json').exists())

    def test_corrupt_json_schema_and_png_are_rejected_without_discarding_valid_peer(self):
        for invalid in (b'not json', b'[]', b'{"schema_version":true}', b'{"schema_version":1,"x":NaN}', b'\xff',
                        b'{"schema_version":1,"x":' + b'[' * 1500 + b'0}'):
            with self.subTest(invalid=invalid), TemporaryDirectory() as root:
                receipt, _ = self.collect(root, {'failure.json': invalid, 'failure.png': PNG})
                self.assertEqual('diagnostic_failure', receipt['status']); self.assertEqual('retained', receipt['artifacts'][1]['status'])
                self.assertFalse((Path(root) / 'evidence/failure.json').exists())
        with TemporaryDirectory() as root:
            receipt, _ = self.collect(root, {'failure.json': JSON, 'failure.png': b'not a PNG'})
            self.assertEqual('retained', receipt['artifacts'][0]['status']); self.assertEqual('diagnostic_failure', receipt['artifacts'][1]['status'])

    def test_declared_oversize_is_not_read_and_changed_actual_size_is_rejected(self):
        for name, limit in (('failure.json', 64 * 1024), ('failure.png', 2 * 1024 * 1024)):
            with self.subTest(name=name), TemporaryDirectory() as root:
                receipt, calls = self.collect(root, declared={name: limit + 1})
                self.assertEqual('diagnostic_failure', receipt['status'])
                self.assertNotIn(('cat', 'files/native-sqlite-qa/' + name), calls)
        with TemporaryDirectory() as root:
            receipt, _ = self.collect(root, declared={'failure.json': len(JSON) - 1})
            self.assertEqual('diagnostic_failure', receipt['artifacts'][0]['status'])

    def test_timeout_is_a_diagnostic_failure_and_keeps_receipt(self):
        with TemporaryDirectory() as root, patch('mobile_sqlite_evidence.subprocess.run', side_effect=subprocess.TimeoutExpired('adb', 10)):
            receipt = collect_sqlite_evidence('adb', 'phone', Path(root), case=CASE, source_fingerprint=FINGERPRINT)
            self.assertEqual('diagnostic_failure', receipt['status']); self.assertEqual(2, len(receipt['artifacts']))
            self.assertTrue((Path(root) / 'index.json').is_file())

    def test_target_and_provenance_guards_run_before_any_adb_or_output_creation(self):
        for bad in ({'package': 'com.nextgis.mobile'}, {'package': 'com.nextgis.mobile.debug'},
                    {'case': 'com.nextgis.mobile.reliability.Other#test'}, {'case': SQLITE_CLASS},
                    {'case': CASE + ',other.Test#test'}, {'source_fingerprint': 'short'}, {'serial': 'phone; other'}):
            with self.subTest(bad=bad), TemporaryDirectory() as root, patch('mobile_sqlite_evidence.subprocess.run') as run:
                args = dict(case=CASE, source_fingerprint=FINGERPRINT, serial='phone'); args.update(bad)
                with self.assertRaises(ValueError): collect_sqlite_evidence('adb', destination=Path(root) / 'new', **args)
                run.assert_not_called(); self.assertFalse((Path(root) / 'new').exists())

    def test_previously_retained_evidence_is_never_overwritten(self):
        with TemporaryDirectory() as root:
            receipt, _ = self.collect(root)
            with patch('mobile_sqlite_evidence.subprocess.run') as run, self.assertRaises(FileExistsError):
                collect_sqlite_evidence('adb', 'phone', Path(root) / 'evidence', case=CASE, source_fingerprint=FINGERPRINT)
            run.assert_not_called(); self.assertEqual(receipt, json.loads((Path(root) / 'evidence/index.json').read_text()))


class SqliteRunnerFinallyTest(unittest.TestCase):
    def runner(self, root):
        runner = Verification.__new__(Verification)
        runner.args = SimpleNamespace(scope='related'); runner.plan = {}; runner.catalog = {SQLITE_CLASS: {CASE}}
        runner.adb = 'adb'; runner.phone = 'phone'; runner.output = Path(root); runner.fingerprint = FINGERPRINT
        runner.results = []; runner.checks = {}; runner.devices = {}; runner.prepare = Mock()
        return runner

    def test_native_failure_stays_primary_and_artifacts_are_collected_before_next_reset(self):
        with TemporaryDirectory() as root:
            runner = self.runner(root); order = []
            runner.prepare.side_effect = lambda serial: order.append('prepare')
            runner.command = Mock(return_value='FAILURES!!!\n')
            def collect(*args, **kwargs):
                order.append('collect'); self.assertEqual(CASE, kwargs['case']); self.assertEqual(FINGERPRINT, kwargs['source_fingerprint'])
                return {'status': 'diagnostic_failure', 'case': CASE}
            with patch('run_mobile_tests.collect_sqlite_evidence', side_effect=collect), patch('run_mobile_tests.collect_style_images'):
                with self.assertRaises(NativeCheckFailure): runner.native('phone', [CASE], 'case')
            self.assertEqual(['prepare', 'collect'], order)
            self.assertEqual('diagnostic_failure', runner.checks['native_sqlite_evidence'][0]['status'])

    def test_collection_also_runs_after_transport_failure_and_does_not_mask_it(self):
        with TemporaryDirectory() as root:
            runner = self.runner(root); runner.command = Mock(side_effect=RuntimeError('original transport failure'))
            with patch('run_mobile_tests.collect_sqlite_evidence', side_effect=OSError('collector IO failed')) as collect:
                with self.assertRaisesRegex(RuntimeError, 'original transport failure'): runner.native('phone', [CASE], 'case')
            collect.assert_called_once(); self.assertEqual('diagnostic_failure', runner.checks['native_sqlite_evidence'][0]['status'])

    def test_full_collects_the_previous_case_before_the_next_workspace_reset(self):
        with TemporaryDirectory() as root:
            runner = self.runner(root); runner.args.scope = 'full'; order = []
            runner.receipt = Mock(return_value={}); runner.command = Mock(return_value='FAILURES!!!\n')
            runner.prepare.side_effect = lambda serial: order.append('reset')
            def collect(*args, **kwargs):
                order.append('collect'); return {'status': 'absent_verified', 'case': kwargs['case']}
            with patch('run_mobile_tests.collect_sqlite_evidence', side_effect=collect), patch('run_mobile_tests.collect_style_images'):
                self.assertTrue(runner.native('phone', [CASE], 'first')['failed'])
                self.assertTrue(runner.native('phone', [CASE], 'second')['failed'])
            self.assertEqual(['reset', 'collect', 'reset', 'collect'], order)
            self.assertEqual(2, len(runner.checks['native_sqlite_evidence']))
            with self.assertRaisesRegex(RuntimeError, 'first'): runner.require_native_success()

    def test_unrelated_native_case_never_runs_sqlite_collector(self):
        with TemporaryDirectory() as root:
            runner = self.runner(root); other = 'example.Test#test'; runner.catalog = {'example.Test': {other}}
            runner.command = Mock(side_effect=RuntimeError('original'))
            with patch('run_mobile_tests.collect_sqlite_evidence') as collect:
                with self.assertRaisesRegex(RuntimeError, 'original'): runner.native('phone', [other], 'case')
            collect.assert_not_called()


if __name__ == '__main__': unittest.main()
