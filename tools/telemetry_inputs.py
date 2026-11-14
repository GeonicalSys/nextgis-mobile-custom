"""Check/copy approved private build inputs without printing or replacing credentials."""
from __future__ import annotations
import argparse
import hashlib
import json
import re
import shutil
import sys
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import urlsplit

CONTRACT = Path(__file__).with_name('production-telemetry-contract.json')


def properties(path: Path) -> dict[str, str]:
    """The Java Properties escaping used by Gradle, including physical continuations."""
    def unescape(value: str) -> str:
        result = []; index = 0
        while index < len(value):
            char = value[index]; index += 1
            if char == '\\':
                if index == len(value): raise ValueError('Incomplete property escape')
                char = value[index]; index += 1
                if char == 'u':
                    digits = value[index:index + 4]
                    if not re.fullmatch('[0-9a-fA-F]{4}', digits): raise ValueError('Invalid property escape')
                    char = chr(int(digits, 16)); index += 4
                else: char = {'t': '\t', 'r': '\r', 'n': '\n', 'f': '\f'}.get(char, char)
            result.append(char)
        return ''.join(result)
    physical = path.read_text(encoding='iso-8859-1').splitlines(); logical = []; pending = ''
    for line in physical:
        line = line.lstrip(' \t\f')
        if not pending and (not line or line[0] in '#!'): continue
        pending += line
        if (len(pending) - len(pending.rstrip('\\'))) % 2:
            pending = pending[:-1]; continue
        logical.append(pending); pending = ''
    if pending: raise ValueError('Incomplete property continuation')
    result = {}
    for line in logical:
        escaped = False; end = len(line)
        for index, char in enumerate(line):
            if not escaped and char in '=:\t\f ': end = index; break
            escaped = char == '\\' and not escaped
        tail = line[end:].lstrip(' \t\f')
        if tail[:1] in ('=', ':'): tail = tail[1:].lstrip(' \t\f')
        result[unescape(line[:end])] = unescape(tail)
    return result


def inspect_inputs(workspace: Path) -> dict:
    workspace = workspace.resolve(); contract = json.loads(CONTRACT.read_text(encoding='utf-8'))
    files = {}; digests = {}
    for name, rule in contract['inputs'].items():
        path = workspace / rule['file']
        if rule['file'] not in files:
            if not path.is_file() or path.is_symlink() or path.stat().st_size > 65536:
                raise ValueError(f"Missing or invalid private input: {rule['file']}")
            try: parsed = properties(path)
            except (ValueError, OSError): raise ValueError(f"Cannot read private input: {rule['file']}") from None
            files[rule['file']] = (parsed, hashlib.sha256(path.read_bytes()).hexdigest())
        value = files[rule['file']][0].get(rule['key'], '')
        valid = bool(value) and value == value.strip() and len(value) <= 4096
        kind = rule['kind']
        if kind == 'client': valid &= bool(re.fullmatch(r'[A-Za-z0-9._~-]+', value))
        elif kind == 'token': valid &= bool(re.fullmatch(r'[A-Za-z0-9._~+/=\-]{16,4096}', value))
        elif kind == 'endpoint': valid &= value == rule['expected']
        elif kind == 'sentry':
            try:
                url = urlsplit(value)
                valid &= url.scheme == 'https' and url.hostname == rule['host'] and url.path == rule['path']
                valid &= url.port is None and not url.query and not url.fragment and url.password is None
                valid &= url.netloc == (url.username or '') + '@' + rule['host']
                valid &= bool(re.fullmatch(r'[A-Za-z0-9_-]{16,128}', url.username or ''))
            except ValueError: valid = False
        else: valid = False
        valid &= value.lower() not in {'null', 'undefined', 'changeme', 'your_token'}
        if not valid: raise ValueError(f"Missing or invalid {rule['key']} in {rule['file']}")
        digests[name] = hashlib.sha256(value.encode('utf-8')).hexdigest()
    return {'schema': 1, 'checked_at': datetime.now(timezone.utc).isoformat(), 'source': str(workspace),
            'contract_sha256': hashlib.sha256(CONTRACT.read_bytes()).hexdigest(),
            'files': {name: {'sha256': row[1]} for name, row in files.items()},
            'expected_telemetry_sha256': {k: v for k, v in digests.items() if k != 'clientId'},
            'expected_ngid_client_id_sha256': digests['clientId'],
            'signing': 'External private Gradle signing inputs and signed APK certificate still require their existing checks.'}


def copy_inputs(source: Path, destination: Path) -> dict:
    receipt = inspect_inputs(source); destination = destination.resolve()
    if not destination.is_dir() or destination.is_symlink(): raise ValueError('Destination must be an existing ordinary directory')
    # Preflight every file before making any change; never overwrite a different private input.
    for name, record in receipt['files'].items():
        target = destination / name
        if target.exists() and (not target.is_file() or target.is_symlink()
                or hashlib.sha256(target.read_bytes()).hexdigest() != record['sha256']):
            raise ValueError(f'Private destination differs; preserved: {name}')
    for name, record in receipt['files'].items():
        original = source.resolve() / name; target = destination / name
        if hashlib.sha256(original.read_bytes()).hexdigest() != record['sha256']:
            raise ValueError('Approved source changed during transfer')
        if not target.exists():
            # Exclusive creation also protects an input created concurrently after preflight.
            with original.open('rb') as src, target.open('xb') as dst: shutil.copyfileobj(src, dst)
    checked = inspect_inputs(destination)
    if checked['expected_telemetry_sha256'] != receipt['expected_telemetry_sha256']:
        raise ValueError('Transferred inputs failed verification')
    receipt['destination'] = str(destination)
    return receipt


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', required=True, type=Path)
    parser.add_argument('--copy-to', type=Path)
    parser.add_argument('--manifest', type=Path)
    args = parser.parse_args(argv)
    try:
        receipt = copy_inputs(args.source, args.copy_to) if args.copy_to else inspect_inputs(args.source)
        if args.manifest:
            with args.manifest.open('x', encoding='utf-8') as output: json.dump(receipt, output, ensure_ascii=False, indent=2)
        print(json.dumps(receipt, ensure_ascii=False, indent=2)); return 0
    except (OSError, ValueError) as error:
        print(str(error), file=sys.stderr); return 1


if __name__ == '__main__': raise SystemExit(main())
