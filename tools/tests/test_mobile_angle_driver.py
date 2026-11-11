"""Regression oracles for the real SwiftShader error and system-driver/package ownership."""
import copy,json,shlex,sys
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest
from unittest.mock import Mock
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import mobile_angle_driver as angle

LIBRARY='/system/lib64/libGLESv2_angle.so'
CORRECT={'renderer':'ANGLE (Mesa, Vulkan 1.3.0 (llvmpipe (LLVM21)))',
 'loaded_libraries':[LIBRARY], 'constant_control':{'rgba':[26,128,230,255]},
 'fragment_first':{'rgba':[51,76,178,255]},'shared_first':{'rgba':[51,76,178,255]}}

class Backend:
    def __init__(self,proof=None):
        self.proof=copy.deepcopy(CORRECT if proof is None else proof)
        self.properties={'ro.hardware.egl':'angle','debug.hwui.renderer':'skiavk',
                         'debug.renderengine.backend':'skiavkthreaded'}
        self.packages={};self.calls=[];self.debug='null';self.routing={};self.install_failure=False
    def __call__(self,argv,**options):
        argv=list(map(str,argv));self.calls.append(argv)
        if argv[3]=='install':
            self.packages[angle.PROBE_PACKAGE]=angle.file_sha256(argv[-1])
            if self.install_failure:raise RuntimeError('partial install failed')
            return 'Success'
        if argv[3]=='uninstall':
            self.packages.pop(argv[-1],None);return 'Success'
        args=shlex.split(argv[-1])
        if args[0]=='getprop':return self.properties[args[1]]
        if args[:3]==['settings','get','global']:return self.debug if args[3]=='angle_debug_package' else self.routing.get(args[3],'null')
        if args[:2]==['pm','path']:
            return 'package:/data/app/probe/base.apk' if angle.PROBE_PACKAGE in self.packages else ''
        if args[0]=='sha256sum':
            return ('a'*64 if args[1]==LIBRARY else self.packages[angle.PROBE_PACKAGE])+'  '+args[1]
        if args[:3]==['run-as',angle.PROBE_PACKAGE,'cat']:return json.dumps(self.proof)
        return ''

class SystemAngleTest(unittest.TestCase):
    def test_runtime_library_change_is_rejected_before_any_emulator_process(self):
        with TemporaryDirectory() as folder:
            runtime=Path(folder);expected={}
            for name in ('lib64/vulkan/libvulkan_lvp.dll','lib64/vulkan/lvp_icd.json','lib64/vulkan/vulkan-1.dll'):
                path=runtime/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(name.encode())
                expected[name]=angle.file_sha256(path)
            contract={'emulator':{'files_sha256':expected}}
            self.assertEqual(expected,angle.require_runtime_files(runtime,contract))
            (runtime/'lib64/vulkan/libvulkan_lvp.dll').write_bytes(b'changed driver')
            with self.assertRaisesRegex(ValueError,'changed since verification'):angle.require_runtime_files(runtime,contract)
            with self.assertRaisesRegex(ValueError,'complete pinned identity'):angle.require_runtime_files(runtime,{})

    def session(self,folder,backend):
        apk=Path(folder)/'probe.apk';apk.write_bytes(b'owned diagnostic')
        return angle.SystemAngleProbe(backend,'adb','emulator-5556',Path(folder),
                                      {'probe':apk,'probe_sha256':angle.file_sha256(apk)})
    def test_independent_pixel_oracles_detect_the_original_shader_corruption(self):
        self.assertIs(CORRECT,angle.require_guest_angle_proof(CORRECT))
        for name,rgba in [('fragment_first',[102,178,178,255]),
                          ('shared_first',[53,76,178,255]),('constant_control',[0,0,0,0])]:
            wrong=copy.deepcopy(CORRECT);wrong[name]['rgba']=rgba
            with self.subTest(name=name),self.assertRaisesRegex(ValueError,name):angle.require_guest_angle_proof(wrong)
    def test_renderer_name_cannot_replace_actual_loaded_system_library_proof(self):
        for paths in ([],['/data/app/pinned/libGLESv2_angle.so'],['/system/../data/libGLESv2_angle.so'],[1]):
            with self.subTest(paths=paths),self.assertRaises(ValueError):
                angle.require_guest_angle_proof(dict(CORRECT,loaded_libraries=paths))
        for renderer in ('SwiftShader','ANGLE (NVIDIA)',''):
            with self.subTest(renderer=renderer),self.assertRaises(ValueError):
                angle.require_guest_angle_proof(dict(CORRECT,renderer=renderer))
    def test_physical_device_is_refused_without_any_adb_action(self):
        backend=Mock()
        with self.assertRaisesRegex(ValueError,'owned emulator'):
            angle.SystemAngleProbe(backend,'adb','physical',Path('.'),{})
        backend.assert_not_called()
    def test_success_binds_library_hash_and_removes_only_the_installed_probe(self):
        backend=Backend()
        with TemporaryDirectory() as folder:
            session=self.session(folder,backend)
            result=session.verify()
            self.assertEqual({LIBRARY:'a'*64},result['system_library_sha256'])
            self.assertTrue((Path(folder)/'emulator-gles-proof.json').is_file())
            session.close()
        self.assertFalse(backend.packages)
        self.assertFalse(any('settings put' in call[-1] or 'settings delete' in call[-1] for call in backend.calls))
    def test_failed_shader_keeps_raw_failure_and_does_not_publish_valid_proof(self):
        bad=copy.deepcopy(CORRECT);bad['fragment_first']['rgba']=[102,178,178,255]
        backend=Backend(bad)
        with TemporaryDirectory() as folder:
            session=self.session(folder,backend)
            try:
                with self.assertRaisesRegex(ValueError,'fragment_first'):session.verify()
                self.assertEqual(bad,json.loads((Path(folder)/'emulator-gles-raw.json').read_text()))
                self.assertFalse((Path(folder)/'emulator-gles-proof.json').exists())
            finally:session.close()
        self.assertFalse(backend.packages)
    def test_unknown_existing_package_is_never_replaced_or_removed(self):
        backend=Backend();backend.packages[angle.PROBE_PACKAGE]='unknown'
        with TemporaryDirectory() as folder:
            session=self.session(folder,backend)
            try:
                with self.assertRaisesRegex(ValueError,'different identity'):session.verify()
            finally:session.close()
        self.assertEqual({angle.PROBE_PACKAGE:'unknown'},backend.packages)
        self.assertFalse(any(call[3] in ('install','uninstall') for call in backend.calls))
    def test_verified_existing_probe_is_reused_without_claiming_ownership(self):
        backend=Backend()
        with TemporaryDirectory() as folder:
            session=self.session(folder,backend)
            backend.packages[angle.PROBE_PACKAGE]=session.inputs['probe_sha256']
            session.verify();session.close()
        self.assertIn(angle.PROBE_PACKAGE,backend.packages)
        self.assertFalse(any(call[3] in ('install','uninstall') for call in backend.calls))
    def test_partial_install_is_finalized(self):
        backend=Backend();backend.install_failure=True
        with TemporaryDirectory() as folder:
            session=self.session(folder,backend)
            try:
                with self.assertRaisesRegex(RuntimeError,'partial install'):session.verify()
            finally:session.close()
        self.assertFalse(backend.packages)
    def test_debug_override_or_wrong_guest_boot_stops_before_installation(self):
        for debug,egl in [('separate.package','angle'),('null','emulation')]:
            backend=Backend();backend.debug=debug;backend.properties['ro.hardware.egl']=egl
            with TemporaryDirectory() as folder:
                session=self.session(folder,backend)
                with self.assertRaises(ValueError):session.verify()
            self.assertFalse(any(call[3]=='install' for call in backend.calls))
    def test_prepared_probe_hash_is_rechecked_before_installation(self):
        backend=Backend()
        with TemporaryDirectory() as folder:
            session=self.session(folder,backend);session.inputs['probe'].write_bytes(b'changed')
            with self.assertRaisesRegex(ValueError,'changed'):session.verify()
        self.assertFalse(any(call[3]=='install' for call in backend.calls))
    def test_per_app_override_cannot_make_the_probe_differ_from_the_verification_app(self):
        backend=Backend();backend.routing={'angle_gl_driver_selection_pkgs':'com.nextgis.mobile.verification.debug',
                                          'angle_gl_driver_selection_values':'native'}
        with TemporaryDirectory() as folder:
            session=self.session(folder,backend)
            with self.assertRaisesRegex(ValueError,'routing overrides'):session.verify()
        self.assertFalse(any(call[3]=='install' for call in backend.calls))

if __name__=='__main__':unittest.main()
