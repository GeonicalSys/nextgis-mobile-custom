import sys
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from mobile_style_evidence import collect_style_images
from mobile_test_support import PACKAGE

PNG = b'\x89PNG\r\n\x1a\nfixture'

class StyleEvidenceTest(unittest.TestCase):
    def collect(self, files, declared=None):
        calls=[]
        def run(argv, **kwargs):
            self.assertEqual(['adb','-s','phone','exec-out','run-as',PACKAGE], argv[:6])
            args=argv[6:];calls.append(args)
            if args[0]=='ls': data='\n'.join(files if args[-1]=='files/polygon-editor-qa' else []).encode()
            elif args[0]=='stat': data=str((declared or {}).get(args[-1].split('/')[-1],len(files[args[-1].split('/')[-1]]))).encode()
            else: data=files[args[-1].split('/')[-1]]
            return subprocess.CompletedProcess(argv,0,data,b'')
        with tempfile.TemporaryDirectory() as directory, patch('mobile_style_evidence.subprocess.run',side_effect=run):
            result=collect_style_images(Path('adb'),'phone',Path(directory)/'evidence')
            for image in result:self.assertEqual(files[image['path'].split('/')[-1]],(Path(directory)/'evidence'/image['path']).read_bytes())
            return result,calls
    def test_collects_only_bounded_owned_pngs_with_hash_and_no_path_traversal(self):
        result,calls=self.collect({'valid.png':PNG,'../escape.png':PNG,'other.txt':PNG,'bad.png':b'not an image'})
        self.assertEqual(['polygon-editor-qa/valid.png'],[x['path'] for x in result])
        self.assertEqual(64,len(result[0]['sha256']))
        self.assertFalse(any('escape' in x[-1] or 'other.txt' in x[-1] for x in calls))
    def test_large_or_changed_files_are_never_retained(self):
        result,calls=self.collect({'large.png':PNG,'changed.png':PNG},{'large.png':4194305,'changed.png':100})
        self.assertEqual([],result)
        self.assertFalse(any(x[0]=='cat' and x[-1].endswith('large.png') for x in calls))
