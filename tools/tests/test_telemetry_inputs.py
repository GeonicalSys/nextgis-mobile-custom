from __future__ import annotations
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

TOOLS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(TOOLS))
from telemetry_inputs import inspect_inputs, copy_inputs, properties

VALUES = {
    'local.properties': 'client.id=approved-mobile-client\n',
    'usage.properties': 'usage.endpoint=https://apps-geonical.ru/mobile-stats/api/events\nusage.uploadToken=synthetic-test-token-123456\n',
    'sentry.properties': 'sentry.dsn=https://abcdefghijklmnop@39invalid/errors/1\n'.replace('39invalid', 'apps-geonical.ru'),
    'diagnostics.properties': 'diagnostics.artifactEndpoint=https://apps-geonical.ru/error-artifacts\ndiagnostics.artifactUploadToken=synthetic-artifact-token-123456\n',
}


class PrivateInputsTest(unittest.TestCase):
    def fixture(self, root):
        for name, content in VALUES.items(): (root / name).write_text(content, encoding='ascii')

    def test_exact_inputs_are_portable_and_receipt_contains_only_hashes(self):
        with tempfile.TemporaryDirectory() as a, tempfile.TemporaryDirectory() as b:
            source, target = Path(a), Path(b); self.fixture(source)
            receipt = copy_inputs(source, target)
            self.assertEqual(inspect_inputs(source)['expected_telemetry_sha256'], inspect_inputs(target)['expected_telemetry_sha256'])
            self.assertNotIn('synthetic-artifact-token', json.dumps(receipt))
            self.assertEqual(4, len(receipt['files']))

    def test_missing_empty_wrong_server_project_and_header_injection_fail_closed(self):
        for name, replacement in [('diagnostics.properties', None), ('usage.properties', ''),
                ('usage.properties', VALUES['usage.properties'].replace('https:', 'http:')),
                ('sentry.properties', VALUES['sentry.properties'].replace('/errors/1', '/errors/3')),
                ('sentry.properties', VALUES['sentry.properties'].strip() + '?redirect=evil\n'),
                ('diagnostics.properties', VALUES['diagnostics.properties'].replace('synthetic-artifact-token-123456', 'secret\\nInjected:yes'))]:
            with self.subTest(name=name, replacement=replacement), tempfile.TemporaryDirectory() as d:
                root = Path(d); self.fixture(root)
                if replacement is None: (root / name).unlink()
                else: (root / name).write_text(replacement, encoding='ascii')
                with self.assertRaises(ValueError): inspect_inputs(root)

    def test_different_existing_private_file_stops_before_any_copy(self):
        with tempfile.TemporaryDirectory() as a, tempfile.TemporaryDirectory() as b:
            source, target = Path(a), Path(b); self.fixture(source)
            (target / 'diagnostics.properties').write_text('preserve existing private data')
            with self.assertRaises(ValueError): copy_inputs(source, target)
            self.assertEqual(['diagnostics.properties'], [p.name for p in target.iterdir()])
            self.assertEqual('preserve existing private data', (target / 'diagnostics.properties').read_text())

    def test_java_property_escapes_have_the_same_effective_value(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d); self.fixture(root)
            (root / 'usage.properties').write_text(VALUES['usage.properties'].replace('https:', 'https\\:').replace('123456', '123\\\n  456'))
            receipt = inspect_inputs(root)
            self.assertEqual(hashlib.sha256(b'synthetic-test-token-123456').hexdigest(), receipt['expected_telemetry_sha256']['usageToken'])

    def test_real_gradle_validator_checks_fixture_values_without_logging_secrets(self):
        home = Path(os.environ.get('GRADLE_USER_HOME', Path.home() / '.gradle'))
        libraries = list((home / 'wrapper/dists').glob('gradle-9.3.1-*/**/gradle-9.3.1/lib'))
        java = Path(os.environ.get('JAVA_HOME', '')) / 'bin' / ('java.exe' if os.name == 'nt' else 'java')
        if not libraries or not java.is_file(): self.skipTest('Installed JDK/Gradle 9.3.1 required for the actual Groovy validator')
        script = '''def loader=new GroovyClassLoader(this.class.classLoader)
loader.parseClass(new File(args[0]))
def validator=loader.loadClass('ProductionTelemetryInputs')
def contract=new groovy.json.JsonSlurper().parse(new File(args[1]))
try {validator.read(new File(args[2]),contract,true);println('ACCEPT')}
catch(IllegalArgumentException error) {println(error.message);System.exit(2)}'''
        with tempfile.TemporaryDirectory() as d:
            root = Path(d); source = root / 'source'; source.mkdir(); self.fixture(source)
            runner = root / 'validator.groovy'; runner.write_text(script, encoding='utf-8')
            command = [str(java), '-cp', str(libraries[0] / '*'), 'groovy.ui.GroovyMain', str(runner),
                    str(TOOLS.parent / 'gradle/production-telemetry.gradle'), str(TOOLS / 'production-telemetry-contract.json'), str(source)]
            result = subprocess.run(command, capture_output=True, text=True, timeout=30)
            self.assertEqual(0, result.returncode, result.stdout + result.stderr)
            self.assertEqual('ACCEPT', result.stdout.strip())
            (source / 'sentry.properties').write_text(VALUES['sentry.properties'].replace('/errors/1', '/errors/3'))
            result = subprocess.run(command, capture_output=True, text=True, timeout=30)
            self.assertEqual(2, result.returncode, result.stdout + result.stderr)
            self.assertIn('sentry.dsn', result.stdout); self.assertNotIn('abcdefghijklmnop', result.stdout + result.stderr)


if __name__ == '__main__': unittest.main()
