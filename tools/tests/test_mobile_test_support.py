import sys
from pathlib import Path
import unittest
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from mobile_test_support import check_publication_receipt, parse_instrumentation, validate_plan

CASE = 'example.Test#works'

def output(code=0, summary='OK (1 test)'):
    return ('INSTRUMENTATION_STATUS: class=example.Test\nINSTRUMENTATION_STATUS: test=works\n'
            'INSTRUMENTATION_STATUS_CODE: 1\nINSTRUMENTATION_STATUS: class=example.Test\n'
            f'INSTRUMENTATION_STATUS: test=works\nINSTRUMENTATION_STATUS_CODE: {code}\n{summary}\n'
            'INSTRUMENTATION_CODE: -1\n')

class NativeProtocolTest(unittest.TestCase):
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

if __name__ == '__main__': unittest.main()
