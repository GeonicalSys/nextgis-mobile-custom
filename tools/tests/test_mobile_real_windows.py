"""Keep every former cloud-only window profile in local acceptance, including failures."""
from pathlib import Path
import sys
import unittest
from unittest.mock import Mock

sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from run_mobile_tests import Verification


class RealWindowMatrixTest(unittest.TestCase):
    def runner(self, failed_index=None):
        runner=Verification.__new__(Verification)
        runner.emulator='owned-emulator';runner.phone='working-phone'
        runner.results=[];runner.checks={};runner.prepare=Mock();runner.shell=Mock()
        def native(*args,**kwargs):
            runner.results.append({'name':args[2],'failed':len(runner.results)==failed_index})
        runner.native=Mock(side_effect=native)
        return runner

    def test_all_six_legacy_window_profiles_run_only_on_the_emulator(self):
        runner=self.runner();runner.real_window_matrix()
        runner.prepare.assert_called_once_with('owned-emulator')
        self.assertEqual([('owned-emulator','wm','density','620'),
                          ('owned-emulator','settings','put','system','font_scale','2.0')],
                         [call.args for call in runner.shell.call_args_list])
        profiles=[]
        for call in runner.native.call_args_list:
            self.assertEqual('owned-emulator',call.args[0])
            self.assertFalse(call.kwargs['reset'])
            args=call.kwargs['extra']
            profiles.append((args['uiOrientation'],args.get('dialogStyle','settings')))
        self.assertEqual([('portrait','native'),('portrait','compat'),('portrait','settings'),
                          ('landscape','native'),('landscape','compat'),('landscape','settings')],profiles)
        self.assertTrue(runner.checks['real_window_matrix'])

    def test_one_failed_profile_is_retained_while_other_profiles_finish(self):
        runner=self.runner(failed_index=1);runner.real_window_matrix()
        self.assertEqual(6,len(runner.results))
        self.assertFalse(runner.checks['real_window_matrix'])
        with self.assertRaises(RuntimeError):runner.require_native_success()
