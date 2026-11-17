import argparse
import copy
from pathlib import Path
import sys
import unittest
from unittest.mock import Mock, patch
import requests

sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from perf01_night_benchmark import NightVerification,compare_native,distribution,polygon_coordinates,server_rows


class NightOraclesTest(unittest.TestCase):
    def setUp(self):
        self.feature=dict(id=7,fields=dict(fixture_key="night-0006",revision=1,test_label="Test",nullable_text=None),
                          geom="POLYGON ((0 0,1 0,1 1,0 0))")
        self.expected=server_rows([self.feature])
        self.native=dict(count=1,rows=[dict(id=7,geom=dict(type="Polygon",coordinates=[[[0,0],[1,0],[1,1],[0,0]]]),**self.feature["fields"])])

    def test_authored_full_fields_fid_and_vertices_match(self):
        compare_native(self.native,self.expected)

    def test_same_count_cannot_hide_changed_identity_fields_or_vertices(self):
        changes=[lambda r:r.update(id=8),lambda r:r.update(nullable_text=""),lambda r:r.update(revision=True),
                 lambda r:r["geom"]["coordinates"][0][1].__setitem__(0,3),
                 lambda r:r["geom"].update(type="MultiPolygon"),lambda r:r["geom"]["coordinates"][0][1].__setitem__(0,True)]
        for change in changes:
            with self.subTest(change=change):
                observed=copy.deepcopy(self.native);change(observed["rows"][0])
                with self.assertRaises(ValueError):compare_native(observed,self.expected)

    def test_missing_duplicate_and_extra_rows_are_rejected(self):
        for rows in [[],self.native["rows"]*2]:
            with self.assertRaises(ValueError):compare_native(dict(count=len(rows),rows=rows),self.expected)
        with self.assertRaises(ValueError):server_rows([self.feature,self.feature])

    def test_unreviewed_holes_nonfinite_and_open_rings_are_rejected(self):
        for value in ["POLYGON ((0 0,1 0,1 1,0 0),(0 0,0 1,1 1,0 0))","POLYGON ((0 0,1 0,1 1,2 2))","POLYGON ((0 0,nan 0,1 1,0 0))"]:
            with self.assertRaises(ValueError):polygon_coordinates(value)

    def test_p95_requires_twenty_complete_positive_observations(self):
        self.assertIsNone(distribution([2])["p95_ms"])
        self.assertEqual(dict(n=20,p50_ms=10.5,p95_ms=19),distribution(list(range(1,21))))
        for values in [[True],[float("nan")],[0],[-1]]:
            with self.assertRaises(ValueError):distribution(values)


class NightDeviceBoundaryTest(unittest.TestCase):
    def runner(self):
        value=object.__new__(NightVerification);value.phone="selected";value.run_id="a"*32;value.args=argparse.Namespace(phone_serial="selected",phone_only=True)
        def shell(serial,*args):
            if args==("am","get-current-user"):return "0"
            if args==("dumpsys","trust"):return 'User "Owner" (id=0, flags=0x4c13) (current): deviceLocked=0'
            return ""
        value.shell=Mock(side_effect=shell);value.native=Mock(return_value=dict(passed=["a#b"],failed=False,skipped=[]));return value

    def test_generic_reset_is_always_forbidden(self):
        with self.assertRaisesRegex(RuntimeError,"preserve"):self.runner().prepare("selected")

    def test_invocation_preserves_accounts_and_stops_only_isolated_pair(self):
        value=self.runner();value.invoke("a#b","test")
        self.assertIs(value.native.call_args.kwargs["reset"],False)
        self.assertEqual(value.run_id,value.native.call_args.kwargs["extra"]["nightRun"])
        stopped=[call.args[3] for call in value.shell.call_args_list if call.args[1:3]==("am","force-stop")]
        self.assertEqual(["com.nextgis.mobile.verification.debug","com.nextgis.mobile.verification.debug.test"],stopped)
        self.assertFalse(any("clear" in call.args for call in value.shell.call_args_list))

    def test_wrong_serial_other_user_and_locked_screen_stop_before_native(self):
        changes=[lambda v:setattr(v.args,"phone_serial","other"),lambda v:setattr(v.args,"phone_only",False),
                 lambda v:setattr(v,"emulator","emulator-1"),lambda v:setattr(v.shell,"side_effect",lambda *a:"10"),
                 lambda v:setattr(v.shell,"side_effect",lambda serial,*args:"0" if args==("am","get-current-user") else 'User "Owner" (id=0, flags=0x4c13) (current): deviceLocked=1')]
        for change in changes:
            with self.subTest(change=change):
                value=self.runner();change(value)
                with self.assertRaises(RuntimeError):value.invoke("a#b","test")
                value.native.assert_not_called()


class HostOracleBoundaryTest(unittest.TestCase):
    def runner(self):
        value=object.__new__(NightVerification);value.fixture={'base':'https://azimuth.nextgis.com'};value.checks={};return value

    @patch.dict('os.environ',{'LISA_NIGHT_NGW_LOGIN':'synthetic','LISA_NIGHT_NGW_PASSWORD':'synthetic'})
    def test_only_safe_host_read_retries_transport_failure_and_records_it(self):
        value=self.runner();reply=Mock(status_code=200,content=b'{}');reply.json.return_value={}
        with patch('perf01_night_benchmark.requests.request',side_effect=[requests.ConnectionError('do not log raw secret text'),reply]) as request,patch('perf01_night_benchmark.time.sleep'):
            self.assertEqual({},value.api('GET','/api/resource/1549/feature/?private=query'))
        self.assertEqual(2,request.call_count)
        self.assertEqual([{'path':'/api/resource/1549/feature/','category':'ConnectionError','failed_attempt':1}],value.checks['host_oracle_read_retries'])

    @patch.dict('os.environ',{'LISA_NIGHT_NGW_LOGIN':'synthetic','LISA_NIGHT_NGW_PASSWORD':'synthetic'})
    def test_ambiguous_write_is_never_replayed_by_the_host_oracle(self):
        for method in ['POST','PUT','PATCH','DELETE']:
            with self.subTest(method=method),patch('perf01_night_benchmark.requests.request',side_effect=requests.Timeout('ambiguous')) as request:
                with self.assertRaises(requests.Timeout):self.runner().api(method,'/api/resource/1549/feature/')
                self.assertEqual(1,request.call_count)

    @patch.dict('os.environ',{'LISA_NIGHT_NGW_LOGIN':'synthetic','LISA_NIGHT_NGW_PASSWORD':'synthetic'})
    def test_read_retry_budget_is_bounded_and_http_errors_are_not_retried(self):
        value=self.runner()
        with patch('perf01_night_benchmark.requests.request',side_effect=requests.ConnectionError('unavailable')) as request,patch('perf01_night_benchmark.time.sleep'):
            with self.assertRaises(requests.ConnectionError):value.api('GET','/api/resource/1549')
            self.assertEqual(3,request.call_count)
        with patch('perf01_night_benchmark.requests.request',return_value=Mock(status_code=503,ok=False,content=b'{}')) as request:
            with self.assertRaises(ValueError):value.api('GET','/api/resource/1549')
            self.assertEqual(1,request.call_count)


if __name__=="__main__":unittest.main()
