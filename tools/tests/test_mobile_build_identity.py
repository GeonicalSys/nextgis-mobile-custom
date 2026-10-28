import copy
import json
from pathlib import Path
import sys
from tempfile import TemporaryDirectory
from types import SimpleNamespace
import unittest
from unittest.mock import Mock, patch

sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from mobile_build_identity import build_and_record, load_basis, recipe, validate_basis, verify_pair
from run_mobile_tests import Verification


class BuildIdentityTest(unittest.TestCase):
    def setUp(self):
        self.temp=TemporaryDirectory(); self.addCleanup(self.temp.cleanup)
        self.root=Path(self.temp.name); (self.root/'gradle/wrapper').mkdir(parents=True)
        for name in ('gradlew','gradlew.bat','gradle/wrapper/gradle-wrapper.properties'):
            (self.root/name).write_text('technical wrapper',encoding='utf-8')
        self.source=self.root/'source.txt'; self.source.write_text('same.Test#case: assert A')
        self.fingerprint=lambda:__import__('hashlib').sha256(self.source.read_bytes()).hexdigest()
        self.paths={'app':self.root/'app.apk','test':self.root/'test.apk'}
        self.receipt=self.root/'build/verification/apk-build.json'
        self.recipe=recipe(self.root); self.assemblies=0

    def assemble(self):
        self.assemblies+=1
        self.paths['app'].write_bytes(b'compiled-A-app')
        self.paths['test'].write_bytes(b'compiled-A-test')

    def build(self):
        return build_and_record(self.receipt,self.fingerprint(),self.recipe,
                                self.fingerprint,self.assemble,lambda:self.paths)

    def test_exact_pair_reuse_never_rebuilds_per_method(self):
        basis=self.build()
        for _ in range(3): self.assertEqual(basis,load_basis(self.receipt,self.fingerprint(),self.recipe,self.paths))
        self.assertEqual(1,self.assemblies)
        self.assertEqual({'app','test'},set(basis['apks']))
        self.assertEqual([],list(self.receipt.parent.glob('*.part')))

    def test_compiled_A_cannot_be_credited_to_source_B_with_the_same_case_name(self):
        self.build(); self.source.write_text('same.Test#case: assert B')
        with self.assertRaisesRegex(ValueError,'different sources'):
            load_basis(self.receipt,self.fingerprint(),self.recipe,self.paths)
        self.assertEqual(1,self.assemblies)

    def test_missing_and_legacy_receipts_never_mint_a_basis_from_existing_apks(self):
        self.assemble()
        with self.assertRaisesRegex(ValueError,'No successful'):
            load_basis(self.receipt,self.fingerprint(),self.recipe,self.paths)
        self.receipt.parent.mkdir(parents=True); self.receipt.write_text('{"status":"passed"}')
        with self.assertRaisesRegex(ValueError,'legacy'):
            load_basis(self.receipt,self.fingerprint(),self.recipe,self.paths)

    def test_tampered_or_mixed_app_test_pair_is_rejected_even_at_the_same_size(self):
        for role in ('app','test'):
            basis=self.build(); old=self.paths[role].read_bytes()
            self.paths[role].write_bytes(b'B'+old[1:])
            with self.assertRaisesRegex(ValueError,'changed/mixed'):
                verify_pair(basis,self.fingerprint(),self.recipe,self.paths)

    def test_replaced_path_and_changed_recipe_are_rejected(self):
        basis=self.build(); alternate=self.root/'other.apk'; alternate.write_bytes(self.paths['app'].read_bytes())
        with self.assertRaisesRegex(ValueError,'changed/mixed'):
            verify_pair(basis,self.fingerprint(),self.recipe,{**self.paths,'app':alternate})
        changed={**self.recipe,'launcher_sha256':'e'*64}
        with self.assertRaisesRegex(ValueError,'recipe changed'):
            verify_pair(basis,self.fingerprint(),changed,self.paths)

    def test_failed_gradle_invalidates_prior_basis_without_inspecting_old_apks(self):
        self.build(); inspect=Mock(return_value=self.paths)
        def fail(): raise RuntimeError('Gradle failed')
        with self.assertRaisesRegex(RuntimeError,'Gradle failed'):
            build_and_record(self.receipt,self.fingerprint(),self.recipe,self.fingerprint,fail,inspect)
        self.assertFalse(self.receipt.exists()); inspect.assert_not_called()

    def test_source_change_during_build_cannot_publish_a_receipt(self):
        before=self.fingerprint()
        def changed(): self.assemble(); self.source.write_text('same.Test#case: assert B')
        with self.assertRaisesRegex(ValueError,'during verification build'):
            build_and_record(self.receipt,before,self.recipe,self.fingerprint,changed,lambda:self.paths)
        self.assertFalse(self.receipt.exists())

    def test_source_change_before_build_never_starts_gradle(self):
        before=self.fingerprint(); self.source.write_text('same.Test#case: assert B')
        assemble=Mock()
        with self.assertRaisesRegex(ValueError,'before verification build'):
            build_and_record(self.receipt,before,self.recipe,self.fingerprint,assemble,lambda:self.paths)
        assemble.assert_not_called(); self.assertFalse(self.receipt.exists())

    def test_atomic_write_failure_retains_neither_success_nor_temporary_receipt(self):
        with patch('mobile_build_identity.os.replace',side_effect=OSError('atomic write failed')):
            with self.assertRaisesRegex(OSError,'atomic write failed'): self.build()
        self.assertFalse(self.receipt.exists()); self.assertEqual([],list(self.receipt.parent.glob('*.part')))

    def test_source_change_during_apk_inspection_cannot_publish_a_receipt(self):
        def inspect(): self.source.write_text('same.Test#case: assert B'); return self.paths
        with self.assertRaisesRegex(ValueError,'identifying'):
            build_and_record(self.receipt,self.fingerprint(),self.recipe,self.fingerprint,self.assemble,inspect)
        self.assertFalse(self.receipt.exists())

    def test_wrong_package_missing_half_and_modified_receipt_are_rejected(self):
        basis=self.build()
        for change in ('package','half','digest'):
            value=copy.deepcopy(basis)
            if change=='package': value['apks']['app']['package']='production.package'
            if change=='half': value['apks'].pop('test')
            if change=='digest': value['created_at']='rewritten'
            with self.assertRaises(ValueError): validate_basis(value,self.fingerprint())

    def test_skip_build_failure_precedes_every_device_action(self):
        self.build(); self.source.write_text('same.Test#case: assert B')
        runner=Verification.__new__(Verification)
        runner.args=SimpleNamespace(scope='related',skip_build=True,area=[])
        runner.fingerprint=self.fingerprint(); runner.output=self.root/'result'; runner.output.mkdir()
        runner.results=[]; runner.checks={}; runner.devices={}; runner.build_basis=None
        runner.apks=[self.paths['app'],self.paths['test']]
        runner.discover_verification_apks=Mock(return_value=self.paths)
        runner.select_devices=Mock(); runner.install=Mock(); runner.command=Mock(); runner.restore=Mock()
        with patch('run_mobile_tests.ROOT',self.root):
            with self.assertRaisesRegex(ValueError,'different sources'): runner.run()
        runner.select_devices.assert_not_called(); runner.install.assert_not_called(); runner.command.assert_not_called()
        runner.restore.assert_called_once()
        self.assertEqual('failed',json.loads((runner.output/'result.json').read_text())['status'])

    def test_full_still_rejects_skip_before_any_gradle_command(self):
        runner=Verification.__new__(Verification)
        runner.args=SimpleNamespace(scope='full',skip_build=True); runner.command=Mock()
        with self.assertRaisesRegex(ValueError,'full run must build'): runner.build()
        runner.command.assert_not_called()

    def test_install_checks_pair_before_and_after_the_install_commands(self):
        runner=Verification.__new__(Verification)
        runner.apks=list(self.paths.values()); runner.adb='adb'
        runner.assert_build_pair=Mock(); runner.command=Mock(return_value='Success')
        order=[]
        runner.assert_build_pair.side_effect=lambda:order.append('check')
        runner.command.side_effect=lambda *a,**k:order.append('install') or 'Success'
        runner.install('explicit-verification-device')
        self.assertEqual(['check','install','install','check'],order)

    def test_an_apk_overwritten_during_install_is_not_credited(self):
        basis=self.build(); runner=Verification.__new__(Verification)
        runner.apks=list(self.paths.values()); runner.adb='adb'
        runner.assert_build_pair=lambda:verify_pair(basis,self.fingerprint(),self.recipe,self.paths)
        def installed(*args,**kwargs):
            if runner.command.call_count==2: self.paths['app'].write_bytes(b'overwritten-during-install')
            return 'Success'
        runner.command=Mock(side_effect=installed)
        with self.assertRaisesRegex(ValueError,'changed/mixed'): runner.install('explicit-verification-device')


if __name__=='__main__': unittest.main()
