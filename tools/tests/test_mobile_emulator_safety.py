import sys
import os
import json
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import Mock, patch, MagicMock
from tempfile import TemporaryDirectory
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from mobile_emulator_safety import (require_software_graphics, require_whpx,
                                    require_windows_resources, require_memory_limit, require_cpu_vulkan, resource_stop_reason, launch_environment, GIB)
from run_mobile_tests import Verification


class EmulatorSafetyTest(unittest.TestCase):
    @unittest.skipUnless(os.name=='nt','The guarded GuestAngle profile supports Windows only')
    def test_guest_launch_is_cpu_only_and_bounded_before_the_first_vm_process(self):
        from mobile_angle_driver import file_sha256
        with TemporaryDirectory() as folder:
            root=Path(folder);runtime=root/'runtime';runtime.mkdir()
            (runtime/'emulator.exe').write_bytes(b'fixture runtime')
            pins={}
            for name in ('lib64/vulkan/libvulkan_lvp.dll','lib64/vulkan/lvp_icd.json','lib64/vulkan/vulkan-1.dll'):
                path=runtime/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(name.encode())
                pins[name]=file_sha256(path)
            (root/'tools').mkdir()
            (root/'tools/emulator-angle-driver.json').write_text(json.dumps({'schema_version':2,
                'emulator':{'exe_sha256':file_sha256(runtime/'emulator.exe'),'files_sha256':pins}}))
            runner=Verification.__new__(Verification)
            runner.args=SimpleNamespace(avd='owned-fixture',emulator_graphics='guest-angle',emulator_runtime=str(runtime))
            runner.sdk=root;runner.output=root;runner.checks={}
            runner.command=Mock(return_value='37.2.13.0\nWHPX(10.0.26300) is installed and usable')
            process=Mock();process.poll.return_value=1
            port=MagicMock();port.__enter__.return_value.connect_ex.return_value=1
            with patch('run_mobile_tests.ROOT',root),patch('run_mobile_tests.WindowsEmulatorProcess',return_value=process) as launch, \
                    patch('run_mobile_tests.socket.socket',return_value=port), \
                    patch('run_mobile_tests.windows_resources',return_value=self.resources()), \
                    patch('run_mobile_tests.cached_probe',return_value={}):
                with self.assertRaisesRegex(RuntimeError,'exited before boot'):runner.start_emulator()
            argv,stream,environment,working=launch.call_args.args
            for flag,expected in (('-gpu','lavapipe'),('-feature','Vulkan,GuestAngle'),('-cores','2'),('-memory','2560')):
                self.assertEqual(expected,argv[argv.index(flag)+1])
            self.assertTrue({'-read-only','-no-snapshot','-no-window'}.issubset(set(argv)))
            self.assertEqual('lavapipe',environment['ANDROID_EMU_VK_ICD'])
            self.assertEqual(str(runtime/'lib64/vulkan/lvp_icd.json'),environment['VK_DRIVER_FILES'])
            self.assertEqual('2',environment['LP_NUM_THREADS'])

    def test_changed_owned_transport_cannot_receive_even_an_allowed_failure_adb_command(self):
        class Owned:
            def owns_console(self,port):return False
        runner=Verification.__new__(Verification)
        runner.adb='adb';runner.owned_emulator='emulator-5556';runner.emulator_transport_bound=True
        runner.emulator_process=Owned()
        with patch('run_mobile_tests.WindowsEmulatorProcess',Owned),patch('run_mobile_tests.subprocess.run') as launch:
            with self.assertRaisesRegex(RuntimeError,'changed owner'):
                runner.command(['adb','-s','emulator-5556','shell','am','force-stop','verification'],allow_failure=True)
        launch.assert_not_called()

    def test_actual_software_renderer_is_required(self):
        for renderer in ('Google SwiftShader', 'Mesa llvmpipe', 'Mesa lavapipe'):
            line = 'GLES: Google, ' + renderer + ', OpenGL ES3.1'
            self.assertEqual(line, require_software_graphics('diagnostic header\n  '+line+'\nother state'))

    def test_host_unknown_or_ambiguous_renderer_is_refused(self):
        for text in ('', 'SwiftShader somewhere outside GLES',
                     'GLES: Google (NVIDIA), Android Emulator OpenGL ES Translator (GTX1660Ti), GLES3.1',
                     'GLES: unknown', 'GLES: SwiftShader\nGLES: NVIDIA'):
            with self.subTest(text=text), self.assertRaises(ValueError): require_software_graphics(text)

    def test_missing_explicit_admission_stops_before_adb_or_emulator_start(self):
        runner = Verification.__new__(Verification)
        runner.args = SimpleNamespace(phone_only=False, scope='related', phone_serial=None)
        runner.command = Mock(side_effect=AssertionError('No process may be started'))
        with self.assertRaisesRegex(ValueError, 'on hold'): runner.select_devices()
        runner.command.assert_not_called()

    def test_hardware_borrowed_avd_is_refused_before_install(self):
        with TemporaryDirectory() as folder:
            runner=Verification.__new__(Verification)
            runner.args=SimpleNamespace(phone_only=False,scope='related',phone_serial=None,
                emulator_serial=None,allow_emulator=True,test_project_fixture=None)
            runner.output=Path(folder);runner.adb='adb'
            runner.command=Mock(return_value='emulator-5556\tdevice\n')
            runner.identity=Mock(return_value='emulator-5556');runner.devices={'emulator':{}}
            runner.shell=Mock(return_value='GLES: Google, NVIDIA GTX1660Ti, GLES3.1')
            with self.assertRaisesRegex(ValueError,'software renderer'):runner.select_devices()
            self.assertEqual(1,runner.shell.call_count)

    def test_owned_launch_cannot_silently_select_host_gpu_or_vulkan(self):
        with TemporaryDirectory() as folder:
            runner = Verification.__new__(Verification)
            runner.args = SimpleNamespace(avd='technical-avd'); runner.sdk=Path(folder); runner.output=Path(folder)
            runner.checks={}; runner.command=Mock(return_value='WHPX(10.0.26300) is installed and usable')
            process=Mock(); process.poll.return_value=0
            port=MagicMock(); port.__enter__.return_value.connect_ex.return_value=1
            with patch('run_mobile_tests.WindowsEmulatorProcess' if os.name=='nt' else 'run_mobile_tests.subprocess.Popen', return_value=process) as launch, \
                    patch('run_mobile_tests.socket.socket', return_value=port), \
                    patch('run_mobile_tests.windows_resources', return_value=self.resources()):
                with self.assertRaisesRegex(RuntimeError, 'exited before boot'): runner.start_emulator()
            args=launch.call_args.args[0]
            self.assertEqual('swiftshader_indirect',args[args.index('-gpu')+1])
            self.assertEqual('-Vulkan',args[args.index('-feature')+1])
            self.assertIn('-read-only',args); self.assertIn('-no-snapshot',args)


    @staticmethod
    def resources():
        return {'available_ram_bytes': 6 * GIB, 'commit_bytes': 8 * GIB, 'commit_limit_bytes': 20 * GIB,
                'low_memory':False}

    def test_aehd_or_unusable_whpx_cannot_start_the_vm(self):
        require_whpx('accel:\n0\nWHPX(10.0.26300) is installed and usable')
        for text in ('AEHD is installed and usable', 'WHPX is not installed', ''):
            with self.subTest(text=text), self.assertRaisesRegex(ValueError, 'WHPX'):
                require_whpx(text)

    def test_low_ram_or_commit_reserve_is_refused_before_launch(self):
        for changes in ({'available_ram_bytes': 4 * GIB}, {'low_memory':True},
                        {'commit_bytes': 16 * GIB}, {'commit_limit_bytes': 0}):
            with self.subTest(changes=changes), self.assertRaisesRegex(ValueError, 'available Windows RAM'):
                require_windows_resources(dict(self.resources(), **changes))
        self.assertEqual(self.resources(), require_windows_resources(self.resources()))

    def test_sdk_cannot_silently_raise_ram_beyond_the_admitted_budget(self):
        require_memory_limit('INFO | Increasing RAM size to 2048MB')
        with self.assertRaisesRegex(ValueError,'silently increased'):
            require_memory_limit('INFO | Increasing RAM size to 2048MB',1536)
        with self.assertRaisesRegex(ValueError,'silently increased'):
            require_memory_limit('INFO | Increasing RAM size to 4096MB')

    def test_angle_cannot_borrow_an_existing_avd_or_touch_a_phone(self):
        runner = Verification.__new__(Verification)
        runner.args = SimpleNamespace(phone_only=False, scope='related', phone_serial=None,
                                      emulator_serial=None, allow_emulator=True,
                                      emulator_graphics='angle', test_project_fixture=None)
        runner.adb='adb';runner.command=Mock(return_value='emulator-5556\tdevice\n')
        runner.identity=Mock()
        with self.assertRaisesRegex(ValueError, 'new owned emulator'): runner.select_devices()
        runner.identity.assert_not_called()
        runner.command.reset_mock(); runner.args.phone_only=True
        with self.assertRaisesRegex(ValueError, 'PhoneOnly'): runner.select_devices()
        runner.command.assert_not_called()

    def test_cpu_vulkan_requires_guest_and_host_identity(self):
        surface='------------RE Vulkan (Ganesh)----------\nVulkan device initialized: 1\n'
        document={'devices':[{'properties':{'deviceType':4,'deviceName':'llvmpipe (LLVM21)'}}]}
        log='Selecting Vulkan device: llvmpipe (LLVM21)'
        self.assertEqual(4,require_cpu_vulkan(surface,document,log)['device_type'])
        bad=[('',document,log),(surface,{'devices':[]},log),
             (surface,{'devices':document['devices']*2},log),
             (surface,{'devices':[{'properties':{'deviceType':2,'deviceName':'NVIDIA'}}]},log),
             (surface,document,''),(surface,document,log+'\nSelecting Vulkan device: NVIDIA')]
        for args in bad:
            with self.subTest(args=args),self.assertRaises(ValueError):require_cpu_vulkan(*args)

    def test_low_memory_guard_stops_on_pressure_and_fails_closed(self):
        self.assertIsNone(resource_stop_reason(self.resources()))
        for changes in ({'available_ram_bytes':int(1.4*GIB)},{'low_memory':True},
                        {'commit_bytes':17*GIB},{'commit_limit_bytes':0}):
            self.assertIsNotNone(resource_stop_reason(dict(self.resources(),**changes)))
        with self.assertRaises(ValueError):require_windows_resources(dict(self.resources(),available_ram_bytes=5*GIB),6)

    def test_guest_environment_pins_only_cpu_lavapipe_and_separate_runtime(self):
        with TemporaryDirectory() as folder:
            runtime=Path(folder);icd=runtime/'lib64/vulkan/lvp_icd.json'
            icd.parent.mkdir(parents=True);icd.write_text('{}')
            env=launch_environment(runtime,True,avd_home='owned-avd',emulator_home='owned-home')
            self.assertEqual('lavapipe',env['ANDROID_EMU_VK_ICD'])
            self.assertEqual('LOCAL',env['ANDROID_EMU_VK_RUNTIME'])
            self.assertEqual(str(icd),env['VK_DRIVER_FILES'])
            self.assertEqual(str(icd),env['VK_ICD_FILENAMES'])
            self.assertEqual('2',env['LP_NUM_THREADS'])
            self.assertEqual('owned-avd',env['ANDROID_AVD_HOME'])
            icd.rename(icd.with_suffix('.held'))
            with self.assertRaisesRegex(ValueError,'Lavapipe ICD'):launch_environment(runtime,True)

if __name__ == '__main__': unittest.main()
