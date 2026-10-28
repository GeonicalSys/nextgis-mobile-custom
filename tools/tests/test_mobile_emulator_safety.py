import sys
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import Mock, patch, MagicMock
from tempfile import TemporaryDirectory
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from mobile_emulator_safety import require_software_graphics
from run_mobile_tests import Verification


class EmulatorSafetyTest(unittest.TestCase):
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

    def test_hardware_borrowed_avd_is_refused_before_build_or_install(self):
        runner = Verification.__new__(Verification)
        runner.args = SimpleNamespace(phone_only=False, scope='related', phone_serial=None,
                                      emulator_serial=None, allow_emulator=True, test_project_fixture=None)
        runner.adb = 'adb'; runner.command = Mock(return_value='emulator-5556\tdevice\n')
        runner.identity = Mock(return_value='emulator-5556'); runner.devices={'emulator':{}}
        runner.shell = Mock(return_value='GLES: Google, NVIDIA GTX1660Ti, GLES3.1')
        with self.assertRaisesRegex(ValueError, 'software renderer'): runner.select_devices()

    def test_owned_launch_cannot_silently_select_host_gpu_or_vulkan(self):
        with TemporaryDirectory() as folder:
            runner = Verification.__new__(Verification)
            runner.args = SimpleNamespace(avd='technical-avd'); runner.sdk=Path(folder); runner.output=Path(folder)
            process=Mock(); process.poll.return_value=0
            port=MagicMock(); port.__enter__.return_value.connect_ex.return_value=1
            with patch('run_mobile_tests.subprocess.Popen', return_value=process) as launch, \
                    patch('run_mobile_tests.socket.socket', return_value=port):
                with self.assertRaisesRegex(RuntimeError, 'exited before boot'): runner.start_emulator()
            args=launch.call_args.args[0]
            self.assertEqual('swiftshader_indirect',args[args.index('-gpu')+1])
            self.assertEqual('-Vulkan',args[args.index('-feature')+1])
            self.assertIn('-read-only',args); self.assertIn('-no-snapshot',args)


if __name__ == '__main__': unittest.main()
