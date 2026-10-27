import copy
import sys
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from mobile_render_acceptance import validate_stress, record_stress, check_stress_matrix
from mobile_test_support import check_publication_receipt
from mobile_render_acceptance import STRESS_CASE


class RenderAcceptanceTest(unittest.TestCase):
    def evidence(self, model='SM-X115'):
        return dict(schema_version=1,status='passed',elapsed_ms=90000,gestures=80,frames=100,
                    recreations=2,features=6480,vertices_per_feature=65,mvt_layers=2,
                    visible_features=2,gpu='Vendor / Renderer / OpenGL ES 3.2',model=model,
                    manufacturer='samsung',smallest_width_dp=600,peak_pss_kib=200000,
                    peak_java_bytes=100000000,peak_native_bytes=100000000,pan_changes=60,pinch_changes=20)
    def matrix(self):
        return {'schema_version':1,'entries':[
            {'kind':kind,'source_fingerprint':'abc','device':{'fingerprint':'Android/current'},
             'evidence':self.evidence(model)} for kind,model in
            [('phone','Phone'),('tablet','SM-X115'),('emulator','AVD')]]}
    def test_complete_current_hardware_matrix_is_accepted(self): check_stress_matrix(self.matrix(),'abc')
    def test_one_device_or_a_different_tablet_cannot_certify_the_incident(self):
        for remove in range(3):
            matrix=self.matrix();matrix['entries'].pop(remove)
            with self.assertRaises(ValueError):check_stress_matrix(matrix,'abc')
        matrix=self.matrix();matrix['entries'][1]['evidence']['model']='Different tablet'
        with self.assertRaisesRegex(ValueError,'SM-X115'):check_stress_matrix(matrix,'abc')
    def test_short_empty_or_unknown_gpu_workloads_are_rejected(self):
        for field,value in [('elapsed_ms',89999),('gestures',0),('frames',0),('visible_features',0),
                            ('mvt_layers',1),('gpu','null / null'),('model',''),('recreations',1),('features',8),
                            ('pan_changes',0),('pinch_changes',0),('peak_pss_kib',0),('peak_java_bytes',0),('peak_native_bytes',0)]:
            evidence=self.evidence();evidence[field]=value
            with self.subTest(field=field),self.assertRaises(ValueError):validate_stress(evidence)
    def test_changed_sources_or_missing_os_identity_are_rejected(self):
        with self.assertRaises(ValueError):check_stress_matrix(self.matrix(),'new')
        matrix=self.matrix();matrix['entries'][0]['device']={}
        with self.assertRaises(ValueError):check_stress_matrix(matrix,'abc')
    def test_recording_never_reuses_a_previous_source_or_mislabels_emulator_as_physical(self):
        with TemporaryDirectory() as directory:
            path=Path(directory)/'matrix.json'
            physical=dict(hardware='physical',characteristics='tablet',fingerprint='OS')
            record_stress(path,self.evidence(),physical,'old')
            result=record_stress(path,self.evidence('AVD'),dict(hardware='ranchu',fingerprint='AVD'),'new')
            self.assertEqual(1,len(result['entries']));self.assertEqual('emulator',result['entries'][0]['kind'])
    def test_otherwise_green_full_cannot_skip_hardware_stress(self):
        receipt=dict(scope='full',status='passed',source_fingerprint='abc',passed_cases=[STRESS_CASE],
                     checks=dict.fromkeys(('units','docs','versions','ui_matrix','real_window_matrix','test_project'),True),
                     devices={'phone':{'sdk':36},'emulator':{'sdk':36}})
        with self.assertRaises(ValueError):check_publication_receipt(receipt,'abc',{STRESS_CASE})
        receipt['checks']['render_stress']=self.matrix()
        check_publication_receipt(receipt,'abc',{STRESS_CASE})

if __name__=='__main__':unittest.main()
