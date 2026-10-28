"""Bind one successful verification build to its exact app/instrumentation APK pair."""
from __future__ import annotations
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import tempfile

PACKAGE = 'com.nextgis.mobile.verification.debug'
ROLES = {'app': PACKAGE, 'test': PACKAGE + '.test'}
GRADLE_ARGUMENTS = ('-PfieldVerificationChecks=true', '-PciReliabilityChecks=true',
                    '-Pkotlin.incremental=false', '--console=plain',
                    ':app:assembleLisaDebug', ':app:assembleLisaDebugAndroidTest')
RECIPE_ID = 'lisa-field-verification-v1'


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''): digest.update(block)
    return digest.hexdigest()


def recipe(root: Path) -> dict:
    launcher = 'gradlew.bat' if os.name == 'nt' else 'gradlew'
    return {'id': RECIPE_ID, 'arguments': list(GRADLE_ARGUMENTS),
            'launcher_sha256': sha256(root / launcher),
            'wrapper_properties_sha256': sha256(root / 'gradle/wrapper/gradle-wrapper.properties')}


def _digest(value: dict) -> str:
    payload = {key: item for key, item in value.items() if key != 'basis_sha256'}
    return hashlib.sha256(json.dumps(payload, sort_keys=True, separators=(',', ':'),
                                     ensure_ascii=False).encode('utf-8')).hexdigest()


def validate_basis(value: dict, fingerprint: str) -> None:
    if not isinstance(value, dict) or value.get('schema_version') != 1 or value.get('status') != 'built':
        raise ValueError('Missing/legacy verification build basis; build the verification APK pair once')
    if not fingerprint or value.get('source_fingerprint') != fingerprint:
        raise ValueError('Verification APKs were built from different sources; rebuild before testing')
    build_recipe = value.get('recipe', {})
    if not isinstance(build_recipe, dict) or build_recipe.get('id') != RECIPE_ID or build_recipe.get('arguments') != list(GRADLE_ARGUMENTS):
        raise ValueError('Unknown verification Gradle build recipe')
    for key in ('launcher_sha256', 'wrapper_properties_sha256'):
        if not re.fullmatch('[a-f0-9]{64}', str(build_recipe.get(key, ''))):
            raise ValueError('Incomplete verification build recipe')
    apks = value.get('apks')
    if not isinstance(apks, dict) or set(apks) != set(ROLES):
        raise ValueError('Verification build basis must contain the paired app/test APKs')
    for role, package in ROLES.items():
        item = apks[role]
        if not isinstance(item, dict) or item.get('package') != package or not isinstance(item.get('path'), str) or not item['path']:
            raise ValueError('Incorrect verification APK package/path in build basis')
        if type(item.get('bytes')) is not int or item['bytes'] <= 0 or not re.fullmatch('[a-f0-9]{64}', str(item.get('sha256', ''))):
            raise ValueError('Incomplete verification APK byte identity')
    if apks['app']['path'] == apks['test']['path']:
        raise ValueError('App/test APK identities cannot reference the same file')
    if value.get('basis_sha256') != _digest(value):
        raise ValueError('Verification build basis digest does not match its contents')


def _inventory(paths: dict[str, Path]) -> dict:
    if set(paths) != set(ROLES): raise ValueError('Exactly one app/test APK pair is required')
    result = {}
    for role, package in ROLES.items():
        path = Path(paths[role]).resolve(strict=True)
        if not path.is_file() or path.suffix.lower() != '.apk': raise ValueError('Invalid verification APK file')
        size = path.stat().st_size
        result[role] = {'path': str(path), 'package': package, 'bytes': size, 'sha256': sha256(path)}
        if size <= 0 or path.stat().st_size != size: raise ValueError('APK changed while its identity was read')
    return result


def verify_pair(value: dict, fingerprint: str, build_recipe: dict, paths: dict[str, Path]) -> None:
    validate_basis(value, fingerprint)
    if value['recipe'] != build_recipe: raise ValueError('Verification Gradle recipe changed after the build')
    if value['apks'] != _inventory(paths): raise ValueError('Verification APK pair changed/mixed after the build')


def load_basis(path: Path, fingerprint: str, build_recipe: dict, paths: dict[str, Path]) -> dict:
    if not path.is_file(): raise ValueError('No successful verification build receipt; run once without --skip-build')
    value = json.loads(path.read_text(encoding='utf-8'))
    verify_pair(value, fingerprint, build_recipe, paths)
    return value


def build_and_record(path: Path, expected_fingerprint: str, build_recipe: dict,
                     fingerprint, assemble, inspect_apks) -> dict:
    """Only this successful Gradle dataflow may mint a reusable basis, never skip-build."""
    path.unlink(missing_ok=True)  # Failed/interrupted new build cannot retain a prior green basis.
    before = fingerprint()
    if before != expected_fingerprint: raise ValueError('Sources changed before verification build')
    assemble()  # Must raise on Gradle failure. Existing arbitrary APKs alone are insufficient.
    if fingerprint() != before: raise ValueError('Sources changed during verification build')
    paths = inspect_apks()  # Caller verifies fixed package identities with aapt first.
    apks = _inventory(paths)
    if fingerprint() != before: raise ValueError('Sources changed while identifying verification APKs')
    value = {'schema_version': 1, 'status': 'built', 'source_fingerprint': before,
             'created_at': datetime.now(timezone.utc).isoformat(), 'recipe': build_recipe, 'apks': apks}
    value['basis_sha256'] = _digest(value)
    verify_pair(value, before, build_recipe, paths)
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(mode='w', encoding='utf-8', dir=path.parent,
                                         prefix='apk-build-', suffix='.part', delete=False) as stream:
            temporary = Path(stream.name)
            json.dump(value, stream, ensure_ascii=False, indent=2)
            stream.flush(); os.fsync(stream.fileno())
        os.replace(temporary, path)
    finally:
        if temporary is not None: temporary.unlink(missing_ok=True)
    return value
