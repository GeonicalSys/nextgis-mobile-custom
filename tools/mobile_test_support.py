"""Fail-closed parsing, source coverage and publication receipts for Android verification."""
from __future__ import annotations
import hashlib
import json
from pathlib import Path
import re
import subprocess

PACKAGE = 'com.nextgis.mobile.verification.debug'
RUNNER = PACKAGE + '.test/com.nextgis.mobile.util.VerificationTestRunner'
METHOD = re.compile(r'@Test(?:\([^)]*\))?.*?(?:public\s+void\s+|fun\s+)(\w+)\s*\(', re.S)


def phone_unlocked(trust_dump: str) -> bool:
    """Read the active Android user's keyguard state, never another profile's state."""
    for line in trust_dump.splitlines():
        if re.match(r'^\s*User .*\(id=\d+,[^)]*\)\s+\(current\):', line):
            state = re.search(r'\bdeviceLocked=(true|false|0|1)\b', line)
            if state:
                return state.group(1) in ('false', '0')
    raise ValueError('Cannot determine the selected phone lock state; UI verification cannot start')


def native_catalog(root: Path) -> dict[str, set[str]]:
    result = {}
    for path in sorted((root / 'app/src/androidTest/java').rglob('*')):
        if path.suffix not in ('.java', '.kt'):
            continue
        text = path.read_text(encoding='utf-8-sig')
        methods = set(METHOD.findall(text))
        if methods:
            package = re.search(r'^package\s+([\w.]+)', text, re.M).group(1)
            name = package + '.' + path.stem
            result[name] = {name + '#' + method for method in methods}
    return result


def avd_names(listing: str) -> list[str]:
    # Some Windows emulator builds merge crash-reporter diagnostics into -list-avds output.
    return [line.strip() for line in listing.splitlines() if re.fullmatch(r'[\w.-]+',line.strip(),re.ASCII)]


def expand_selectors(selectors: list[str], catalog: dict[str, set[str]]) -> set[str]:
    result = set()
    for selector in selectors:
        name, _, method = selector.partition('#')
        if name not in catalog:
            raise ValueError('Unknown native test class: ' + name)
        cases = catalog[name] if not method else {selector}
        if not cases <= catalog[name]:
            raise ValueError('Unknown native test method: ' + selector)
        result.update(cases)
    return result


def validate_plan(plan: dict, catalog: dict[str, set[str]]) -> set[str]:
    classified = set(plan['phone']) | set(plan['emulator']) | set(plan['diagnostics'])
    if classified != set(catalog):
        raise ValueError('Native test routing is incomplete: missing=' + str(sorted(set(catalog)-classified))
                         + ' obsolete=' + str(sorted(classified-set(catalog))))
    if set(plan['phone']) & (set(plan['emulator']) | set(plan['diagnostics'])):
        raise ValueError('A test class is routed to both phone and emulator')
    if not set(plan.get('isolated_methods', [])) <= classified:
        raise ValueError('Unknown class in per-method isolation routing')
    for selectors in plan['areas'].values():
        expand_selectors(selectors, catalog)
    for area,selectors in plan.get('related_phone_areas',{}).items():
        if area not in plan['areas']:
            raise ValueError('Unknown Related phone area: '+area)
        if not expand_selectors(selectors,catalog) <= expand_selectors(plan['areas'][area],catalog):
            raise ValueError('Related phone cases must belong to their selected area: '+area)
    return set().union(*catalog.values())


def parse_instrumentation(text: str, expected: set[str], *, fatal: str | None = None) -> dict:
    fields, passed, skipped, failed, started = {}, set(), set(), set(), set()
    for line in text.splitlines():
        if line.startswith('INSTRUMENTATION_STATUS: '):
            key, _, value = line[len('INSTRUMENTATION_STATUS: '):].partition('=')
            fields[key] = value.strip()
        elif line.startswith('INSTRUMENTATION_STATUS_CODE: '):
            code = int(line.rsplit(':', 1)[1])
            case = fields.get('class', '') + '#' + fields.get('test', '')
            if code == 1: started.add(case)
            elif code == 0: passed.add(case)
            elif code == -4: skipped.add(case)
            else: failed.add(case)
            fields = {}
    if fatal:
        if fatal not in started or 'shortMsg=Process crashed' not in text or 'FAILURES!!!' in text:
            raise ValueError('The explicitly selected fatal phase did not terminate as expected')
        return {'passed': [fatal], 'skipped': [], 'expected_crash': True}
    if failed or 'FAILURES!!!' in text or 'INSTRUMENTATION_FAILED' in text or 'Process crashed' in text:
        raise ValueError('Android instrumentation failed: ' + ', '.join(sorted(failed)))
    count = re.search(r'OK \((\d+) tests?\)', text)
    if not count or int(count.group(1)) <= 0 or passed | skipped != expected:
        raise ValueError('No complete native result for every requested test: missing='
                         + str(sorted(expected-passed-skipped)))
    if passed & skipped or not passed:
        raise ValueError('No successfully executed native cases')
    return {'passed': sorted(passed), 'skipped': sorted(skipped), 'expected_crash': False}


def source_fingerprint(root: Path) -> str:
    digest = hashlib.sha256()
    for prefix in ('', 'maplib/', 'maplibui/', 'easypicker/'):
        directory = root / prefix
        listing = subprocess.check_output(['git', '-C', str(directory), 'ls-files', '-z',
                                           '--cached', '--others', '--exclude-standard'])
        for relative in sorted(set(listing.decode('utf-8').split('\0'))):
            path = directory / relative
            if not relative or not path.is_file(): continue
            # Local secrets/build products are ignored by Git. Docs-only edits do not stale code tests.
            if relative.startswith(('docs/', 'update-reports/')) or '/docs/' in relative or path.suffix.lower() == '.md': continue
            digest.update((prefix + relative).encode('utf-8') + b'\0')
            digest.update(path.read_bytes())
    return digest.hexdigest()


def check_publication_receipt(receipt: dict, fingerprint: str, required: set[str]) -> None:
    if receipt.get('scope') != 'full' or receipt.get('status') != 'passed':
        raise ValueError('Publication requires a successful full run, not a related/CI run')
    if receipt.get('source_fingerprint') != fingerprint:
        raise ValueError('The application or test sources changed after the full run')
    if any(result.get('failed') for result in receipt.get('runs', [])):
        raise ValueError('The full run contains a failed native attempt, even if a repeat passed')
    if not required <= set(receipt.get('passed_cases', [])):
        raise ValueError('The full run did not execute every native case successfully')
    if not all(receipt.get('checks', {}).get(key) for key in ('units', 'docs', 'versions', 'ui_matrix', 'test_project')):
        raise ValueError('Unit/docs/version/UI acceptance is incomplete')
    devices = receipt.get('devices', {})
    if not devices.get('phone') or not devices.get('emulator'):
        raise ValueError('Both the physical phone and emulator results are required')
