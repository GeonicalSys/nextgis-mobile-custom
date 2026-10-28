import json
from pathlib import Path
import sys
from tempfile import TemporaryDirectory
import unittest
from unittest.mock import patch
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from prepare_native_heap_apk import copy_with_manifest, member_inventory, package_identity, run, verify_members


class NativeHeapApkTest(unittest.TestCase):
    def baseline(self, path):
        entries = {'AndroidManifest.xml': b'original-manifest', 'classes.dex': b'original-bytecode',
                   'classes2.dex': b'original-second-dex', 'lib/arm64-v8a/libmaplibre.so': b'original-native',
                   'resources.arsc': b'original-resource-table', 'res/layout/main.xml': b'original-layout',
                   'assets/font.pbf': b'original-assets', 'META-INF/version-control-info.textproto': b'original-provenance',
                   'META-INF/GEONICAL.SF': b'old-signature', 'META-INF/GEONICAL.RSA': b'old-certificate',
                   'META-INF/MANIFEST.MF': b'old-digests'}
        with zipfile.ZipFile(path, 'w') as archive:
            for name, value in entries.items(): archive.writestr(name, value)
        return entries

    def test_only_manifest_and_actual_signature_members_change(self):
        with TemporaryDirectory() as directory:
            source, output = Path(directory)/'baseline.apk', Path(directory)/'profile.apk'
            entries = self.baseline(source); copy_with_manifest(source, output, b'profileable-manifest')
            proof = verify_members(member_inventory(source), member_inventory(output))
            self.assertEqual(7, proof['unchanged_member_count'])
            self.assertEqual(2, len(proof['dex_sha256'])); self.assertEqual(1, len(proof['native_libraries_sha256']))
            with zipfile.ZipFile(output) as archive:
                self.assertEqual(entries['META-INF/version-control-info.textproto'], archive.read('META-INF/version-control-info.textproto'))
                self.assertEqual(b'profileable-manifest', archive.read('AndroidManifest.xml'))
                self.assertNotIn('META-INF/GEONICAL.SF', archive.namelist())

    def test_any_dex_library_resource_asset_or_provenance_change_is_rejected(self):
        with TemporaryDirectory() as directory:
            path = Path(directory)/'baseline.apk'; self.baseline(path); before = member_inventory(path)
            for name in ('classes.dex', 'lib/arm64-v8a/libmaplibre.so', 'resources.arsc', 'assets/font.pbf',
                         'res/layout/main.xml', 'META-INF/version-control-info.textproto'):
                after = dict(before); after['AndroidManifest.xml'] = 'different'; after[name] = 'tampered'
                with self.subTest(name=name), self.assertRaisesRegex(ValueError, 'outside manifest'): verify_members(before, after)
            for after in ({**before, 'injected.dex': 'bad'}, {key: value for key, value in before.items() if key != 'classes.dex'}):
                after['AndroidManifest.xml'] = 'different'
                with self.assertRaises(ValueError): verify_members(before, after)

    def test_unchanged_manifest_and_duplicate_archive_names_are_not_accepted(self):
        with TemporaryDirectory() as directory:
            path = Path(directory)/'baseline.apk'; self.baseline(path); before = member_inventory(path)
            with self.assertRaisesRegex(ValueError, 'not changed'): verify_members(before, before)
            with zipfile.ZipFile(path, 'a') as archive:
                with self.assertWarns(UserWarning): archive.writestr('classes.dex', b'shadow')
            with self.assertRaisesRegex(ValueError, 'Duplicate'): member_inventory(path)

    def test_copy_refuses_an_existing_artifact_and_keeps_the_baseline_untouched(self):
        with TemporaryDirectory() as directory:
            source, output = Path(directory)/'baseline.apk', Path(directory)/'retained.apk'
            self.baseline(source); before = member_inventory(source)
            output.write_bytes(b'retained evidence')
            with self.assertRaises(FileExistsError): copy_with_manifest(source, output, b'profileable')
            self.assertEqual(b'retained evidence', output.read_bytes())
            self.assertEqual(before, member_inventory(source))

    def test_package_version_sdk_and_release_flag_are_independent_identity_fields(self):
        text = "package: name='com.example.app' versionCode='224' versionName='3.1.2.30'\nsdkVersion:'26'\ntargetSdkVersion:'36'\n"
        identity = package_identity(text)
        self.assertEqual({'package': 'com.example.app', 'version_code': '224', 'version_name': '3.1.2.30',
                          'minSdkVersion': '26', 'targetSdkVersion': '36', 'debuggable': False}, identity)
        self.assertEqual(identity, package_identity(text.replace("sdkVersion:'26'", "minSdkVersion: '26'")))
        self.assertTrue(package_identity(text+'application-debuggable\n')['debuggable'])
        with self.assertRaises(ValueError): package_identity(text.replace("sdkVersion:'26'", ''))

    def test_tool_failure_never_echoes_private_output_or_command_arguments(self):
        class Failure:
            returncode = 7
            stdout = b'PRIVATE_PASSWORD'
            stderr = b'PRIVATE_ALIAS'
        with patch('prepare_native_heap_apk.subprocess.run', return_value=Failure()):
            with self.assertRaises(RuntimeError) as error: run(['java.exe', 'PRIVATE_ARGUMENT'])
        text = str(error.exception)
        self.assertNotIn('PRIVATE', text); self.assertEqual('java.exe failed (exit 7)', text)


if __name__ == '__main__': unittest.main()
