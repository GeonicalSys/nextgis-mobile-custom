import sys
from pathlib import Path
import unittest
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from mobile_test_support import avd_names, check_publication_receipt, parse_instrumentation, phone_unlocked, validate_plan

CASE = 'example.Test#works'

def output(code=0, summary='OK (1 test)'):
    return ('INSTRUMENTATION_STATUS: class=example.Test\nINSTRUMENTATION_STATUS: test=works\n'
            'INSTRUMENTATION_STATUS_CODE: 1\nINSTRUMENTATION_STATUS: class=example.Test\n'
            f'INSTRUMENTATION_STATUS: test=works\nINSTRUMENTATION_STATUS_CODE: {code}\n{summary}\n'
            'INSTRUMENTATION_CODE: -1\n')

class NativeProtocolTest(unittest.TestCase):
    def test_avd_names_exclude_windows_crash_reporter_output(self):
        self.assertEqual(['Medium_Phone_API_36.0'],avd_names(
            '[123:456:20261023,185744:ERROR filesystem_win.cc:130] GetFileAttributes Q:\\Temp failed\n'
            'Medium_Phone_API_36.0\n'))
    def test_android_final_minus_one_is_a_successful_run(self):
        self.assertEqual([CASE], parse_instrumentation(output(), {CASE})['passed'])
    def test_failed_case_cannot_pass_with_a_stale_ok_line(self):
        with self.assertRaises(ValueError): parse_instrumentation(output(-2), {CASE})
    def test_partial_and_zero_test_results_fail(self):
        for text in ('OK (0 tests)', output()):
            with self.assertRaises(ValueError): parse_instrumentation(text, {CASE, 'example.Test#missing'})
    def test_assumptions_do_not_count_as_passed(self):
        with self.assertRaises(ValueError): parse_instrumentation(output(-4), {CASE})
    def test_expected_fatal_is_explicit_and_method_scoped(self):
        text=output(1, 'INSTRUMENTATION_RESULT: shortMsg=Process crashed.')
        self.assertTrue(parse_instrumentation(text, {CASE}, fatal=CASE)['expected_crash'])
        with self.assertRaises(ValueError): parse_instrumentation(text, {CASE})
        with self.assertRaises(ValueError): parse_instrumentation(text, {CASE}, fatal='example.Test#another')

class PhoneLockStateTest(unittest.TestCase):
    def test_current_user_must_be_unlocked_even_when_another_profile_is_not(self):
        for state in ('0', 'false'):
            dump = (' User "Owner" (id=0, flags=0x1) (current): deviceLocked=' + state + '\n'
                    ' User "Work" (id=10, flags=0x2): deviceLocked=1\n')
            self.assertTrue(phone_unlocked(dump))

    def test_an_unlocked_work_profile_cannot_hide_a_locked_current_user(self):
        for state in ('1', 'true'):
            dump = (' User "Owner" (id=0, flags=0x1) (current): deviceLocked=' + state + '\n'
                    ' User "Work" (id=10, flags=0x2): deviceLocked=0\n')
            self.assertFalse(phone_unlocked(dump))

    def test_unknown_or_incomplete_state_cannot_start_ui_tests(self):
        for dump in ('', ' User "Work" (id=10, flags=0x2): deviceLocked=0',
                     ' User "Owner" (id=0, flags=0x1) (current): deviceLocked=unknown'):
            with self.assertRaises(ValueError):
                phone_unlocked(dump)

class FullReceiptTest(unittest.TestCase):
    def receipt(self):
        return {'scope':'full','status':'passed','source_fingerprint':'abc','passed_cases':[CASE],
                'checks':dict.fromkeys(('units','docs','versions','ui_matrix','test_project'),True),
                'devices':{'phone':{'sdk':36},'emulator':{'sdk':36}}}
    def test_a_complete_current_full_run_is_accepted(self):
        check_publication_receipt(self.receipt(),'abc',{CASE})
    def test_changed_code_or_a_related_run_is_rejected(self):
        for key,value in [('source_fingerprint','old'),('scope','related'),('status','failed')]:
            receipt=self.receipt(); receipt[key]=value
            with self.assertRaises(ValueError): check_publication_receipt(receipt,'abc',{CASE})
    def test_missing_case_check_or_phone_is_rejected(self):
        for key,value in [('passed_cases',[]),('checks',{}),('devices',{'emulator':{}})]:
            receipt=self.receipt(); receipt[key]=value
            with self.assertRaises(ValueError): check_publication_receipt(receipt,'abc',{CASE})
    def test_a_new_native_class_cannot_be_silently_omitted(self):
        plan={'phone':['example.Test'],'emulator':[],'diagnostics':[],'areas':{'one':['example.Test']}}
        self.assertEqual({CASE}, validate_plan(plan,{'example.Test':{CASE}}))
        with self.assertRaises(ValueError): validate_plan(plan,{'example.Test':{CASE},'new.Test':{'new.Test#case'}})
    def test_per_method_isolation_cannot_name_an_unrouted_class(self):
        plan={'phone':['example.Test'],'emulator':[],'diagnostics':[],'areas':{},
              'isolated_methods':['example.Test']}
        self.assertEqual({CASE},validate_plan(plan,{'example.Test':{CASE}}))
        plan['isolated_methods']=['unknown.Test']
        with self.assertRaises(ValueError):validate_plan(plan,{'example.Test':{CASE}})

    def test_related_phone_cases_must_be_known_and_belong_to_the_requested_area(self):
        plan={'phone':['example.Test'],'emulator':[],'diagnostics':[],
              'areas':{'editing':[CASE]},'related_phone_areas':{'editing':[CASE]}}
        self.assertEqual({CASE},validate_plan(plan,{'example.Test':{CASE}}))
        for mapping in ({'missing':[CASE]},{'editing':['example.Test#missing']}):
            plan['related_phone_areas']=mapping
            with self.assertRaises(ValueError):validate_plan(plan,{'example.Test':{CASE}})
        other='example.Test#another'
        plan['related_phone_areas']={'editing':[other]}
        with self.assertRaises(ValueError):validate_plan(plan,{'example.Test':{CASE,other}})

if __name__ == '__main__': unittest.main()
