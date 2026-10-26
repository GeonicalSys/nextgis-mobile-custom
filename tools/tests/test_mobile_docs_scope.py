"""Exercise the actual Windows docs wrapper on clean and changed temporary Git trees."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[2]


@unittest.skipUnless(os.name=='nt' and shutil.which('powershell.exe'),'Windows docs wrapper')
class LocalDocsScopeTest(unittest.TestCase):
    def test_clean_target_validates_without_empty_diff_flag_and_changed_paths_are_enforced(self):
        with tempfile.TemporaryDirectory(prefix='lisa-docs-scope-') as folder:
            root=Path(folder)
            for name in ('tools','docs/scripts','docs/tests'):(root/name).mkdir(parents=True,exist_ok=True)
            shutil.copyfile(ROOT/'tools/docs-check.ps1',root/'tools/docs-check.ps1')
            (root/'tools/update-report-policy.json').write_text(json.dumps({'base_branch':'main'}),encoding='utf-8')
            (root/'tools/check_update_reports.py').write_text('pass\n',encoding='utf-8')
            for name in ('tools/test_check_update_reports.py','docs/tests/test_interface.py'):
                (root/name).write_text('import unittest\nfrom pathlib import Path\n'
                    'class InterfaceTest(unittest.TestCase):\n'
                    ' def test_workspace_is_the_owned_fixture(self):\n'
                    '  self.assertTrue(Path("tools/update-report-policy.json").is_file())\n',encoding='utf-8')
            (root/'docs/scripts/validate_docs.py').write_text(
                'import argparse,json\nfrom pathlib import Path\n'
                'p=argparse.ArgumentParser();p.add_argument("--workspace-root");'
                'p.add_argument("--enforce-diff",action="store_true");'
                'p.add_argument("--changed-file",action="append",default=[]);a=p.parse_args()\n'
                'if a.enforce_diff and not a.changed_file:p.error("empty enforced diff")\n'
                'Path("validated.json").write_text(json.dumps(vars(a)),encoding="utf-8")\n',encoding='utf-8')
            (root/'.gitignore').write_text('validated.json\n',encoding='utf-8')
            def git(*args):subprocess.run(['git',*args],cwd=root,check=True,capture_output=True)
            git('init','-b','main');git('add','.')
            git('-c','user.name=Verification','-c','user.email=verification@example.invalid',
                '-c','commit.gpgsign=false','commit','-m','technical fixture')
            git('update-ref','refs/remotes/origin/main','HEAD')
            def check():
                result=subprocess.run(['powershell.exe','-NoProfile','-File',str(root/'tools/docs-check.ps1'),'-RunTests'],
                                      cwd=root,capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=60)
                self.assertEqual(0,result.returncode,result.stdout+'\n'+result.stderr)
                return json.loads((root/'validated.json').read_text(encoding='utf-8'))
            clean=check();self.assertFalse(clean['enforce_diff']);self.assertEqual([],clean['changed_file'])
            (root/'source with space.py').write_text('pass\n',encoding='utf-8')
            changed=check();self.assertTrue(changed['enforce_diff'])
            self.assertEqual(['source with space.py'],changed['changed_file'])
