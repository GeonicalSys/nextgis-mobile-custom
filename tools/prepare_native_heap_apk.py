"""Prepare a private profileable copy of an exact existing APK, without rebuilding app code.

Only AndroidManifest.xml and signatures may change. All other ZIP members, including
DEX, native libraries, assets and resources.arsc, must remain byte-identical.
This developer tool never installs an APK, controls a device or publishes anything.
Signing properties are read in the Java helper from their existing private file;
no credential value enters command arguments, environment variables or evidence.
"""
from __future__ import annotations
import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import zipfile

HELPER = Path(__file__).with_name('NativeHeapManifest.java')
SIGNATURE_MEMBER = re.compile(r'^META-INF/(?:MANIFEST\.MF|[^/]+\.(?:SF|RSA|DSA|EC))$', re.I)
MANIFEST = 'AndroidManifest.xml'


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''): digest.update(block)
    return digest.hexdigest()


def member_inventory(path: Path) -> dict[str, str]:
    result = {}
    with zipfile.ZipFile(path) as archive:
        for entry in archive.infolist():
            if entry.filename in result: raise ValueError('Duplicate APK member: '+entry.filename)
            digest = hashlib.sha256()
            with archive.open(entry) as stream:
                for block in iter(lambda: stream.read(1024 * 1024), b''): digest.update(block)
            result[entry.filename] = digest.hexdigest()
    if MANIFEST not in result: raise ValueError('APK has no manifest')
    return result


def verify_members(before: dict[str, str], after: dict[str, str]) -> dict:
    def protected(values): return {k: v for k, v in values.items() if k != MANIFEST and not SIGNATURE_MEMBER.fullmatch(k)}
    original, candidate = protected(before), protected(after)
    if original != candidate:
        changed = sorted(k for k in original.keys() | candidate.keys() if original.get(k) != candidate.get(k))
        raise ValueError('APK content changed outside manifest/signatures: '+', '.join(changed[:12]))
    if before.get(MANIFEST) == after.get(MANIFEST): raise ValueError('The profiling manifest was not changed')
    serialized = json.dumps(original, sort_keys=True, separators=(',', ':')).encode()
    return {'unchanged_member_count': len(original), 'unchanged_member_inventory_sha256': hashlib.sha256(serialized).hexdigest(),
            'dex_sha256': {k: v for k, v in original.items() if re.fullmatch(r'classes\d*\.dex', k)},
            'native_libraries_sha256': {k: v for k, v in original.items() if k.startswith('lib/') and k.endswith('.so')},
            'resources_arsc_sha256': original.get('resources.arsc')}


def copy_with_manifest(source: Path, output: Path, manifest: bytes) -> None:
    with zipfile.ZipFile(source) as original, zipfile.ZipFile(output, 'x') as candidate:
        for entry in original.infolist():
            if SIGNATURE_MEMBER.fullmatch(entry.filename): continue
            if entry.filename == MANIFEST: candidate.writestr(entry, manifest)
            else:
                with original.open(entry) as stream, candidate.open(entry, 'w') as destination:
                    shutil.copyfileobj(stream, destination, 1024 * 1024)


def package_identity(badging: str) -> dict:
    match = re.search(r"^package: name='([^']+)' versionCode='([^']+)' versionName='([^']*)'", badging, re.M)
    if not match: raise ValueError('Cannot read APK package/version')
    identity = dict(zip(('package', 'version_code', 'version_name'), match.groups()))
    for name, aliases in (('minSdkVersion', r'(?:minSdkVersion|sdkVersion)'), ('targetSdkVersion', 'targetSdkVersion')):
        match = re.search(r"^"+aliases+r":\s*'([^']+)'", badging, re.M)
        if not match: raise ValueError('Cannot read APK '+name)
        identity[name] = match.group(1)
    identity['debuggable'] = bool(re.search(r'^application-debuggable(?:\s|$)', badging, re.M))
    return identity


def run(command: list[str | Path], *, timeout: int = 120) -> str:
    result = subprocess.run([str(word) for word in command], capture_output=True, timeout=timeout)
    if result.returncode:
        # Do not echo SDK/helper output: signing exceptions may mention private inputs.
        raise RuntimeError(Path(command[0]).name+' failed (exit '+str(result.returncode)+')')
    return result.stdout.decode('utf-8', 'replace').strip()


def plain_directory(path: Path) -> Path:
    path = path.absolute()
    for ancestor in (path, *path.parents):
        if ancestor.exists() and (ancestor.is_symlink() or ancestor.is_junction()):
            raise ValueError('Reparse point in owned output/scratch path: '+str(ancestor))
    if path != path.resolve(): raise ValueError('Unexpected physical path: '+str(path))
    return path


def single_jar(cache: Path, group: str, artifact: str, version: str) -> Path:
    jars = list((cache/'caches/modules-2/files-2.1'/group/artifact/version).glob('*/*.jar'))
    jars = [p for p in jars if not p.name.endswith(('-sources.jar', '-javadoc.jar'))]
    if len(jars) != 1: raise ValueError('Expected one installed '+artifact+' '+version+' JAR')
    return jars[0]


def prepare(args) -> dict:
    if os.name != 'nt': raise ValueError('This installed-tool preparation route is Windows-only')
    source = args.source_apk.resolve(strict=True)
    output = plain_directory(args.output_apk.parent)/args.output_apk.name
    receipt_path = plain_directory(args.receipt.parent)/args.receipt.name
    scratch = plain_directory(args.scratch_root)
    if source == output or output == receipt_path or output.exists() or receipt_path.exists():
        raise ValueError('Do not overwrite an APK or prior evidence')
    source_hash = sha256(source)
    if source_hash != args.expected_source_sha256.lower(): raise ValueError('Source APK SHA-256 does not match the explicit baseline')
    scratch.mkdir(parents=True, exist_ok=True); output.parent.mkdir(parents=True, exist_ok=True)
    receipt_path.parent.mkdir(parents=True, exist_ok=True)
    if shutil.disk_usage(scratch).free < 30 * 1024**3: raise ValueError('Less than30GiB free: reuse/cleanup plan required before APK copies')
    if output.drive.lower() != scratch.drive.lower(): raise ValueError('Scratch and retained APK must share a drive for no-overwrite finalization')
    tools = args.sdk_root/'build-tools'/args.build_tools
    aapt, align = tools/'aapt2.exe', tools/'zipalign.exe'
    java, javac = args.jbr_root/'bin/java.exe', args.jbr_root/'bin/javac.exe'
    jars = [single_jar(args.gradle_cache, 'com.android.tools.build', 'aapt2-proto', '9.1.0-14792394'),
            single_jar(args.gradle_cache, 'com.google.protobuf', 'protobuf-java', '4.28.3'), tools/'lib/apksigner.jar']
    for path in [aapt, align, java, javac, *jars, HELPER, args.signing_properties]:
        if not path.is_file(): raise ValueError('Required installed input is unavailable: '+str(path))
    receipt = {'schema_version': 1, 'owner_task': args.owner_task, 'purpose': 'Private native-heap profiling only; no app rebuild/install/publication',
               'created_utc': datetime.now(timezone.utc).isoformat(), 'status': 'active', 'source_apk': str(source),
               'source_sha256': source_hash, 'output_apk': str(output), 'cleanup': 'pending',
               'scope': 'Only profileable/shell=true manifest child and APK signatures; app data untouched',
               'tools': {str(path): sha256(path) for path in [aapt, align, java, javac, *jars, HELPER, Path(__file__)]}}
    receipt_path.write_text(json.dumps(receipt, ensure_ascii=False, indent=2), encoding='utf-8')
    temporary = tempfile.TemporaryDirectory(prefix='native-heap-apk-', dir=scratch)
    work = Path(temporary.name)
    receipt['temporary_directory'] = str(work)
    stage = 'validate-baseline'
    try:
        (work/'storage-manifest.json').write_text(json.dumps({'owner_task': args.owner_task, 'state': 'active',
            'purpose': 'Manifest-only APK transformation and signing intermediates', 'created_utc':receipt['created_utc'],
            'planned_retention': 'Remove all intermediates in finally; retain only verified APK and compact provenance outside this directory'}), encoding='utf-8')
        identity = package_identity(run([aapt, 'dump', 'badging', source]))
        if identity['debuggable']: raise ValueError('Baseline must remain a non-debuggable release APK')
        inventory = member_inventory(source)
        classes = work/'classes'; classes.mkdir()
        classpath = os.pathsep.join(str(path) for path in jars)
        stage = 'compile-installed-api-helper'
        run([javac, '-encoding', 'UTF-8', '-cp', classpath, '-d', classes, HELPER])
        def helper(*words): return run([java, '-cp', str(classes)+os.pathsep+classpath, 'NativeHeapManifest', *words])
        certificate = helper('certificate', source)
        if not re.fullmatch('[0-9a-f]{64}', certificate): raise ValueError('Baseline signer verification returned invalid evidence')
        def mini(path: Path, data: bytes):
            with zipfile.ZipFile(path, 'x', compression=zipfile.ZIP_STORED) as archive: archive.writestr(MANIFEST, data)
        def manifest(path: Path) -> bytes:
            with zipfile.ZipFile(path) as archive: return archive.read(MANIFEST)
        stage = 'convert-manifest-only-to-proto'
        mini(work/'original-mini.apk', manifest(source))
        run([aapt, 'convert', '--output-format', 'proto', '-o', work/'original-proto.apk', work/'original-mini.apk'])
        (work/'original.pb').write_bytes(manifest(work/'original-proto.apk'))
        stage = 'add-profileable-shell'
        helper('patch', work/'original.pb', work/'patched.pb')
        mini(work/'patched-proto.apk', (work/'patched.pb').read_bytes())
        run([aapt, 'convert', '--output-format', 'binary', '--keep-raw-values', '-o', work/'patched-binary.apk', work/'patched-proto.apk'])
        run([aapt, 'convert', '--output-format', 'proto', '-o', work/'roundtrip-proto.apk', work/'patched-binary.apk'])
        (work/'roundtrip.pb').write_bytes(manifest(work/'roundtrip-proto.apk'))
        if helper('verify-manifest', work/'original.pb', work/'roundtrip.pb') != 'manifest-only-profileable':
            raise ValueError('Manifest semantic proof missing')
        stage = 'copy-unchanged-members'
        copy_with_manifest(source, work/'unsigned.apk', manifest(work/'patched-binary.apk'))
        verify_members(inventory, member_inventory(work/'unsigned.apk'))
        stage = 'align-and-sign-existing-certificate'
        run([align, '-P', '16', '4', work/'unsigned.apk', work/'aligned.apk'])
        helper('sign', work/'aligned.apk', work/'signed.apk', args.signing_properties, source, identity['minSdkVersion'], args.signing_base_dir)
        run([align, '-c', '-P', '16', '4', work/'signed.apk'])
        if helper('certificate', work/'signed.apk') != certificate: raise ValueError('Output signing certificate differs from baseline')
        if package_identity(run([aapt, 'dump', 'badging', work/'signed.apk'])) != identity:
            raise ValueError('Package/version/SDK/debuggable identity changed')
        proof = verify_members(inventory, member_inventory(work/'signed.apk'))
        stage = 'retain-verified-private-apk'
        output_hash = sha256(work/'signed.apk')
        os.rename(work/'signed.apk', output)  # Windows fails if destination appeared; never overwrites it.
        if sha256(output) != output_hash: raise ValueError('Retained APK readback differs')
        receipt.update(status='verified', package_identity=identity, signer_certificate_sha256=certificate,
                       output_sha256=output_hash, output_bytes=output.stat().st_size,
                       manifest_semantics='Only one new profileable child with android:shell=true', **proof)
    except Exception as failure:
        receipt.update(status='failed', failed_stage=stage, error_type=type(failure).__name__, error=str(failure))
        raise
    finally:
        try:
            temporary.cleanup()
            if work.exists(): raise RuntimeError('Temporary directory remains after cleanup')
            receipt['cleanup'] = 'removed-and-absence-verified'
        except Exception as failure:
            receipt.update(cleanup='failed', cleanup_error_type=type(failure).__name__, status='failed')
            raise
        finally:
            receipt_path.write_text(json.dumps(receipt, ensure_ascii=False, indent=2), encoding='utf-8')
    return receipt


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source-apk', type=Path, required=True); parser.add_argument('--output-apk', type=Path, required=True)
    parser.add_argument('--expected-source-sha256', required=True); parser.add_argument('--receipt', type=Path, required=True)
    parser.add_argument('--scratch-root', type=Path, required=True); parser.add_argument('--signing-properties', type=Path, required=True)
    parser.add_argument('--signing-base-dir', type=Path, default=Path(__file__).resolve().parents[1]/'app')
    parser.add_argument('--sdk-root', type=Path, default=Path(r'C:\Users\lyubi\AppData\Local\Android\Sdk'))
    parser.add_argument('--jbr-root', type=Path, default=Path(r'C:\Program Files\Android\Android Studio\jbr'))
    parser.add_argument('--gradle-cache', type=Path, default=Path(r'Q:\Codex\Cache\gradle'))
    parser.add_argument('--build-tools', default='36.0.0'); parser.add_argument('--owner-task', required=True)
    args = parser.parse_args()
    if not re.fullmatch('[0-9a-fA-F]{64}', args.expected_source_sha256): parser.error('Expected a complete baseline SHA-256')
    try: receipt = prepare(args)
    except Exception as failure:
        print('Private native-heap APK preparation failed: '+str(failure)); return 1
    print(json.dumps({key: receipt[key] for key in ('status', 'output_apk', 'output_sha256', 'output_bytes', 'cleanup')}, ensure_ascii=False))
    return 0


if __name__ == '__main__': raise SystemExit(main())
