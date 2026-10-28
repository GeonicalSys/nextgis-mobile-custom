"""Preserve the two fixed native SQLite witnesses from the disposable verification APK."""
from __future__ import annotations
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import subprocess

SQLITE_CLASS = 'com.nextgis.mobile.rendering.NativeSqliteVectorRenderingTest'
VERIFICATION_PACKAGE = 'com.nextgis.mobile.verification.debug'
FILES = (('failure.json', 64 * 1024), ('failure.png', 2 * 1024 * 1024))
PNG_SIGNATURE = b'\x89PNG\r\n\x1a\n'


def _missing(result: subprocess.CompletedProcess, command: str, remote: str) -> bool:
    # Some adb/run-as combinations return0 and put this error in stdout.
    # Only an exact error for this exact fixed file establishes absence.
    allowed = {f'{command}: {path}: No such file or directory'.encode('utf-8')
               for path in (remote, "'" + remote + "'", '"' + remote + '"')}
    streams = [(result.stdout or b'').strip(), (result.stderr or b'').strip()]
    return any(stream in allowed for stream in streams) and all(not stream or stream in allowed for stream in streams)


def _detail(result: subprocess.CompletedProcess) -> str:
    return ((result.stderr or b'') + b' ' + (result.stdout or b''))[:320].decode('utf-8', errors='replace').strip()


def collect_sqlite_evidence(adb: str | Path, serial: str, destination: Path, *, case: str,
                            source_fingerprint: str, package: str = VERIFICATION_PACKAGE) -> dict:
    """No remote discovery/deletion. Retain only validated bytes and a per-case receipt.

    Target/provenance programmer errors raise before any ADB call. Remote absence and
    diagnostic failures are distinct returned statuses, so a native failure stays primary.
    The caller supplies its owned run output on Q; no temporary environment is created.
    """
    if package != VERIFICATION_PACKAGE:
        raise ValueError('SQLite evidence is limited to the isolated verification package')
    if not re.fullmatch(re.escape(SQLITE_CLASS) + r'#[A-Za-z_][A-Za-z0-9_]*', case):
        raise ValueError('SQLite evidence requires one exact NativeSqliteVectorRenderingTest case')
    if not re.fullmatch(r'[0-9a-f]{64}', source_fingerprint):
        raise ValueError('SQLite evidence requires the complete source fingerprint')
    if not re.fullmatch(r'[A-Za-z0-9_.:-]+', serial):
        raise ValueError('SQLite evidence requires one explicit ADB serial')
    destination = Path(destination)
    if destination.is_symlink() or getattr(destination, 'is_junction', lambda: False)():
        raise ValueError('SQLite evidence destination must be an owned ordinary directory')
    destination.mkdir(parents=True, exist_ok=True)
    # Never overwrite a previous witness or its provenance on a repeated collector call.
    if any((destination / name).exists() for name in ('index.json', 'failure.json', 'failure.png')):
        raise FileExistsError('SQLite evidence already exists; use a new run/case destination')
    result = dict(schema_version=1, kind='native_sqlite_failure_artifacts', case=case,
                  source_fingerprint=source_fingerprint, package=package, serial=serial,
                  created_at=datetime.now(timezone.utc).isoformat(), artifacts=[])
    prefix = [str(adb), '-s', serial, 'exec-out', 'run-as', VERIFICATION_PACKAGE]

    def read(*args):
        return subprocess.run(prefix + list(args), capture_output=True, timeout=10,
                              creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0))

    for name, limit in FILES:
        remote = 'files/native-sqlite-qa/' + name
        row = dict(remote=remote, path=name, limit_bytes=limit)
        result['artifacts'].append(row)
        try:
            observed = read('stat', '-c', '%s', remote)
            if _missing(observed, 'stat', remote):
                row.update(status='absent_verified', stage='stat'); continue
            if observed.returncode != 0 or observed.stderr:
                raise ValueError('remote_stat_failed: ' + _detail(observed))
            declared = (observed.stdout or b'').strip()
            if not re.fullmatch(rb'[0-9]{1,10}', declared):
                raise ValueError('remote_stat_malformed: ' + _detail(observed))
            size = int(declared)
            if not 1 <= size <= limit:
                raise ValueError('remote_size_out_of_bounds: ' + str(size))
            observed = read('cat', remote)
            if _missing(observed, 'cat', remote):
                # The file can disappear after the successful existence/size probe.
                row.update(status='absent_verified', stage='cat'); continue
            if observed.returncode != 0 or observed.stderr:
                raise ValueError('remote_read_failed: ' + _detail(observed))
            data = observed.stdout or b''
            if len(data) != size or len(data) > limit:
                raise ValueError('remote_read_size_changed: declared=' + str(size) + ', actual=' + str(len(data)))
            if name.endswith('.json'):
                def invalid_constant(value):
                    raise ValueError('Non-JSON numeric constant: ' + value)
                payload = json.loads(data.decode('utf-8'), parse_constant=invalid_constant)
                if not isinstance(payload, dict) or type(payload.get('schema_version')) is not int or payload['schema_version'] != 1:
                    raise ValueError('SQLite witness JSON must be a schema1 object')
            elif not data.startswith(PNG_SIGNATURE):
                raise ValueError('SQLite witness PNG signature is invalid')
            with (destination / name).open('xb') as output:
                output.write(data)
            row.update(status='retained', bytes=len(data), sha256=hashlib.sha256(data).hexdigest())
        except (OSError, subprocess.SubprocessError, UnicodeError, ValueError, RecursionError) as error:
            row.update(status='diagnostic_failure', error=(type(error).__name__ + ': ' + str(error))[:640])
    statuses = {row['status'] for row in result['artifacts']}
    result['status'] = ('diagnostic_failure' if 'diagnostic_failure' in statuses else
                        'partial' if len(statuses) > 1 else next(iter(statuses)))
    result['index'] = str(destination / 'index.json')
    with (destination / 'index.json').open('x', encoding='utf-8') as output:
        json.dump(result, output, ensure_ascii=False, indent=2)
        output.write('\n')
    return result
