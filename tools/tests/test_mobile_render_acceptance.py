import copy
import sys
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from mobile_render_acceptance import validate_stress, record_stress, check_stress_matrix
from mobile_test_support import check_publication_receipt
from mobile_render_acceptance import STRESS_CASE
from test_mobile_test_support import valid_build_basis


class RenderAcceptanceTest(unittest.TestCase):
    def evidence(self, model='Test device'):
        return dict(schema_version=2,status='passed',elapsed_ms=90000,workload_elapsed_ms=90000,
                    startup_elapsed_ms=3000,total_elapsed_ms=94000,seed_pid=101,pid=202,
                    render_backend='sqlite_native',sqlite_layers=2,source_checks=60,global_feature_count=0,
                    visible_required_count=125,visible_missing_count=0,
                    visible_coverage=[dict(fixture_features=6400,required_ids_count=100,rendered_ids_count=110,
                                           missing_ids_count=0,invalid_role_hits=0,unexpected_id_hits=0),
                                      dict(fixture_features=80,required_ids_count=25,rendered_ids_count=30,
                                           missing_ids_count=0,invalid_role_hits=0,unexpected_id_hits=0)],gestures=80,frames=100,
                    recreations=2,features=6480,vertices_per_feature=65,mvt_layers=2,
                    visible_features=140,gpu='Vendor / Renderer / OpenGL ES 3.2',model=model,
                    manufacturer='samsung',smallest_width_dp=600,peak_pss_kib=200000,
                    peak_java_bytes=100000000,peak_native_bytes=100000000,pan_changes=60,pinch_changes=20,
                    heading_updates=600,peak_process_rss_bytes=250000000)
    def matrix(self):
        phone = dict(self.evidence('Phone'), smallest_width_dp=400)
        tablet = self.evidence('Available tablet')
        return {'schema_version': 1, 'entries': [
            {'kind': kind, 'source_fingerprint': 'abc',
             'device': {'fingerprint': 'Android/current', 'hardware': 'physical',
                        'serial': serial, 'characteristics': kind},
             'evidence': evidence}
            for kind, serial, evidence in [('phone', 'phone-usb', phone), ('tablet', 'tablet-wifi', tablet)]
        ]}

    def test_one_selected_phone_or_tablet_is_sufficient(self):
        for row in self.matrix()['entries']:
            with self.subTest(role=row['kind']):
                check_stress_matrix({'schema_version': 1, 'entries': [row]}, 'abc', row['device'])

    def test_absent_selected_physical_device_cannot_be_replaced_by_an_emulator(self):
        matrix = self.matrix()
        row = matrix['entries'].pop(1)
        selected = row['device'].copy()
        row['device'].update(hardware='ranchu', serial='emulator-5554')
        matrix['entries'].append(row)
        with self.assertRaisesRegex(ValueError, 'hardware'):
            check_stress_matrix(matrix, 'abc', selected)
        row['kind'] = 'emulator'
        with self.assertRaisesRegex(ValueError, 'selected physical device'):
            check_stress_matrix(matrix, 'abc', selected)

    def test_selected_device_must_match_serial_hardware_and_android_build(self):
        matrix = self.matrix()
        selected = matrix['entries'][1]['device']
        for key, value in (('serial', 'other-serial'), ('hardware', 'other-hardware'),
                           ('fingerprint', 'Android/other')):
            with self.subTest(key=key), self.assertRaisesRegex(ValueError, 'selected physical device'):
                check_stress_matrix(matrix, 'abc', {**selected, key: value})

    def test_short_empty_or_unknown_gpu_workloads_are_rejected(self):
        for field,value in [('elapsed_ms',89999),('gestures',0),('frames',0),('visible_features',0),
                            ('mvt_layers',1),('gpu','null / null'),('model',''),('recreations',1),('features',8),
                            ('pan_changes',0),('pinch_changes',0),('peak_pss_kib',0),('peak_java_bytes',0),('peak_native_bytes',0),
                            ('heading_updates',299),('peak_process_rss_bytes',0)]:
            evidence=self.evidence();evidence[field]=value
            with self.subTest(field=field),self.assertRaises(ValueError):validate_stress(evidence)
    def test_changed_sources_or_missing_os_identity_are_rejected(self):
        matrix = self.matrix()
        selected = matrix['entries'][0]['device'].copy()
        with self.assertRaises(ValueError):
            check_stress_matrix(matrix, 'new', selected)
        matrix['entries'][0]['device'] = {}
        with self.assertRaises(ValueError):
            check_stress_matrix(matrix, 'abc', selected)
        with self.assertRaisesRegex(ValueError, 'selected physical Android device identity'):
            check_stress_matrix(self.matrix(), 'abc', {**selected, 'fingerprint': ''})
    def test_legacy_counter_cached_global_features_and_warm_process_cannot_pass(self):
        for field,value in [('schema_version',1),('render_backend','legacy_http'),('sqlite_layers',0),
                            ('source_checks',59),('global_feature_count',1),('global_feature_count',False),
                            ('seed_pid',202),('seed_pid',None),('pid',True)]:
            evidence=self.evidence();evidence[field]=value
            with self.subTest(field=field,value=value),self.assertRaises(ValueError):validate_stress(evidence)
    def test_stale_pre_sleep_duration_or_startup_time_cannot_replace_ninety_seconds_of_input(self):
        for changes in [dict(workload_elapsed_ms=89999),dict(elapsed_ms=89999),
                        dict(total_elapsed_ms=92999),dict(startup_elapsed_ms=-1),dict(workload_elapsed_ms=True)]:
            evidence=self.evidence();evidence.update(changes)
            with self.subTest(changes=changes),self.assertRaises(ValueError):validate_stress(evidence)
    def test_one_rendered_object_cannot_certify_a_missing_viewport_or_an_invalid_anchor(self):
        for field,value in [('missing_ids_count',1),('invalid_role_hits',1),('unexpected_id_hits',1),
                            ('rendered_ids_count',1),('rendered_ids_count',6401),('fixture_features',100),
                            ('required_ids_count',-1),('required_ids_count',True)]:
            evidence=self.evidence();evidence['visible_coverage'][0][field]=value
            with self.subTest(field=field),self.assertRaises(ValueError):validate_stress(evidence)
        for field,value in [('visible_coverage',[]),('visible_features',1),('visible_required_count',1),
                            ('visible_missing_count',1)]:
            evidence=self.evidence();evidence[field]=value
            with self.subTest(field=field),self.assertRaises(ValueError):validate_stress(evidence)
    def test_offscreen_second_layer_and_legitimate_edge_intersections_are_not_invented_missing_objects(self):
        evidence=self.evidence();evidence['visible_coverage'][1].update(required_ids_count=0,rendered_ids_count=0)
        evidence.update(visible_required_count=100,visible_features=110)
        validate_stress(evidence)
        evidence['visible_coverage'][0]['required_ids_count']=0;evidence['visible_required_count']=0
        with self.assertRaises(ValueError):validate_stress(evidence)
    def test_recording_never_reuses_a_previous_source_or_mislabels_emulator_as_physical(self):
        with TemporaryDirectory() as directory:
            path=Path(directory)/'matrix.json'
            physical=dict(hardware='physical',characteristics='tablet',fingerprint='OS')
            record_stress(path,self.evidence(),physical,'old')
            result=record_stress(path,self.evidence('AVD'),dict(hardware='ranchu',fingerprint='AVD'),'new')
            self.assertEqual(1,len(result['entries']));self.assertEqual('emulator',result['entries'][0]['kind'])
    def test_otherwise_green_full_requires_stress_from_the_selected_device(self):
        matrix = self.matrix()
        for physical in matrix['entries']:
            with self.subTest(role=physical['kind']):
                device = physical['device']
                receipt = dict(
                    scope='full', status='passed', source_fingerprint='abc', passed_cases=[STRESS_CASE],
                    build_basis=valid_build_basis(),
                    checks=dict.fromkeys(
                        ('units', 'docs', 'versions', 'ui_matrix', 'real_window_matrix', 'test_project'),
                        True),
                    devices={'phone': device, 'emulator': {'sdk': 36}},
                    runs=[{'device': device['serial'], 'passed': [STRESS_CASE]}])
                required = frozenset({STRESS_CASE})
                with self.assertRaisesRegex(ValueError, 'current hardware render stress'):
                    check_publication_receipt(receipt, 'abc', required, required_phone=required)
                other = [row for row in matrix['entries'] if row is not physical]
                receipt['checks']['render_stress'] = {'schema_version': 1, 'entries': other}
                with self.assertRaisesRegex(ValueError, 'selected physical device'):
                    check_publication_receipt(receipt, 'abc', required, required_phone=required)
                receipt['checks']['render_stress']['entries'] = [physical]
                check_publication_receipt(receipt, 'abc', required, required_phone=required)

if __name__=='__main__':unittest.main()
