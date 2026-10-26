"""The CI profile must retain every track-delivery method on the isolated Full route."""
import json
from pathlib import Path
import re
import shlex
import sys
import unittest

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools'))
from mobile_test_support import expand_selectors, native_catalog


class CiTrackRoutingTest(unittest.TestCase):
    def test_cloud_workflows_require_an_explicit_manual_dispatch(self):
        for path in sorted((ROOT / '.github/workflows').glob('*.yml')):
            with self.subTest(workflow=path.name):
                match=re.search(r'(?ms)^on:[ \t]*\n(.*?)(?=^\S|\Z)',path.read_text(encoding='utf-8'))
                self.assertIsNotNone(match)
                self.assertEqual(['workflow_dispatch'],re.findall(r'^  ([A-Za-z_]+):',match.group(1),re.M))

    def test_all_delivery_methods_use_versioned_verification_without_plain_runner_duplicate(self):
        workflow = (ROOT / '.github/workflows/android-reliability.yml').read_text(encoding='utf-8')
        plan = json.loads((ROOT / 'tools/mobile-test-plan.json').read_text(encoding='utf-8'))
        catalog = native_catalog(ROOT)
        selectors = set()
        for line in workflow.splitlines():
            command = line.strip()
            if command.startswith(('python3 tools/run_mobile_tests.py ', 'python tools/run_mobile_tests.py ')):
                argv = shlex.split(command)
                self.assertEqual('related', argv[argv.index('--scope') + 1])
                for index, value in enumerate(argv):
                    if value == '--area': selectors.update(plan['areas'][argv[index + 1]])
            if 'shell am instrument' in command and 'TrackUploadTest' in command:
                self.fail('Track delivery must use VerificationTestRunner startup/workspace barriers')
        delivery = catalog['com.nextgis.mobile.reliability.TrackUploadTest']
        self.assertTrue(delivery)
        self.assertLessEqual(delivery, expand_selectors(sorted(selectors), catalog))
