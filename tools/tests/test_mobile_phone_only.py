import sys
from pathlib import Path
from types import SimpleNamespace
import unittest
from unittest.mock import Mock
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from run_mobile_tests import Verification


class PhoneOnlyTest(unittest.TestCase):
    def runner(self,scope='related',serial='explicit-phone'):
        runner=Verification.__new__(Verification)
        runner.args=SimpleNamespace(scope=scope,phone_serial=serial,phone_only=True,area=['editing'])
        runner.adb='adb'
        runner.command=Mock(return_value='List of devices attached\nexplicit-phone device\n')
        runner.identity=Mock(return_value='explicit-phone')
        runner.plan={'related_phone_areas':{'editing':['example.Test#stationary']}}
        return runner

    def test_no_implicit_phone_or_full_receipt_can_use_phone_only(self):
        for runner in (self.runner(serial=None),self.runner(scope='full')):
            with self.assertRaises(ValueError):runner.select_devices()
            runner.command.assert_not_called()

    def test_stationary_route_never_starts_or_configures_an_emulator(self):
        runner=self.runner();runner.select_devices()
        runner.identity.assert_called_once_with('explicit-phone','phone')
        self.assertEqual(['example.Test#stationary'],runner.related_phone_cases)
        self.assertFalse(hasattr(runner,'emulator'))
        runner.command.assert_called_once_with(['adb','devices'])

    def test_unclassified_areas_cannot_start_phone_checks(self):
        runner=self.runner();runner.plan={'related_phone_areas':{}}
        with self.assertRaises(ValueError):runner.select_devices()


if __name__=='__main__':unittest.main()
