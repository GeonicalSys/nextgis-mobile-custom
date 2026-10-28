"""Bounded, UID-filtered logcat for the fixed disposable verification APK only."""
from __future__ import annotations

import copy
from datetime import datetime, timezone
from pathlib import Path
import re
import subprocess
import threading

VERIFICATION_PACKAGE = 'com.nextgis.mobile.verification.debug'
HEAD_BYTES = 2 * 1024 * 1024
TAIL_BYTES = 2 * 1024 * 1024
CAP_BYTES = HEAD_BYTES + TAIL_BYTES
_CHUNK_BYTES = 64 * 1024
_PER_USER_RANGE = 100000  # Android UserHandle.PER_USER_RANGE; SDK36 source contract.
_MAX_USER = 21473  # UserHandle.MAX_SECONDARY_USER_ID.


def _now() -> str:
    return datetime.now(timezone.utc).isoformat()


class NativeLogStartError(RuntimeError):
    """Fixed stage/code only; never copy ADB output or an exception message."""
    def __init__(self, code: str, error_type: str = 'NativeLogStartError'):
        self.code = code
        self.error_type = error_type
        super().__init__(code)


def _query(adb: str, serial: str, command: list[str], stage: str, limit: int) -> bytes:
    try:
        result = subprocess.run(
            [adb, '-s', serial, 'shell'] + command,
            capture_output=True, stdin=subprocess.DEVNULL, timeout=10,
            creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0))
    except subprocess.TimeoutExpired:
        raise NativeLogStartError(stage + '_timeout', 'TimeoutExpired') from None
    except (OSError, subprocess.SubprocessError) as error:
        raise NativeLogStartError(stage + '_failed', type(error).__name__) from None
    if result.returncode != 0 or result.stderr or len(result.stdout or b'') > limit:
        raise NativeLogStartError(stage + '_failed')
    return result.stdout or b''


def _uid(adb: str, serial: str) -> int:
    observed = _query(adb, serial, ['am', 'get-current-user'], 'current_user_query', 128)
    if not re.fullmatch(rb'[0-9]{1,5}(?:\r?\n)?', observed) or not 0 <= int(observed) <= _MAX_USER:
        raise NativeLogStartError('current_user_malformed')
    user = int(observed)
    observed = _query(adb, serial, ['cmd', 'package', 'list', 'packages', '--user', str(user),
                                   '-U', VERIFICATION_PACKAGE], 'uid_query', 4096)
    # Android's package filter is a substring filter: the instrumentation sibling is expected.
    # Explicit --user avoids querying inaccessible work profiles. Never select the test UID.
    lines = observed.split(b'\n')
    if lines[-1] == b'':
        lines.pop()
    if not lines:
        raise NativeLogStartError('uid_query_missing')
    if len(lines) > 2:
        raise NativeLogStartError('uid_query_malformed')
    packages = {}
    for line in lines:
        if line.endswith(b'\r'):
            line = line[:-1]
        match = re.fullmatch(rb'package:([A-Za-z0-9_.]+) uid:([0-9]{1,10})', line)
        if match is None:
            raise NativeLogStartError('uid_query_malformed')
        name, uid = match[1].decode('ascii'), int(match[2])
        if name not in (VERIFICATION_PACKAGE, VERIFICATION_PACKAGE + '.test') or name in packages:
            raise NativeLogStartError('uid_query_malformed')
        if not 10000 <= uid <= 2147483647:
            raise NativeLogStartError('uid_query_malformed')
        if uid // _PER_USER_RANGE != user:
            raise NativeLogStartError('uid_user_mismatch')
        if not 10000 <= uid % _PER_USER_RANGE <= 19999:  # Process application UID range.
            raise NativeLogStartError('uid_query_malformed')
        packages[name] = uid
    if VERIFICATION_PACKAGE not in packages:
        raise NativeLogStartError('uid_query_missing')
    uid = packages[VERIFICATION_PACKAGE]
    if packages.get(VERIFICATION_PACKAGE + '.test') == uid:
        raise NativeLogStartError('uid_query_conflict')
    return uid


class _Window:
    """Persist a head and circular tail without repeatedly rewriting two MiB per chunk."""
    def __init__(self, output):
        self.output = output
        self.seen = 0
        self.head = 0
        self.tail = 0
        self.cursor = 0
        self.layout = 'head_then_tail'

    def _write(self, data) -> None:
        view = memoryview(data)
        while view:
            count = self.output.write(view)
            if not count:
                raise OSError('log_sink_short_write')
            view = view[count:]

    def append(self, data: bytes) -> None:
        self.seen += len(data)
        view = memoryview(data)
        count = min(len(view), HEAD_BYTES - self.head)
        if count:
            self.output.seek(self.head)
            self._write(view[:count])
            self.head += count
            view = view[count:]
        if not view:
            return
        if len(view) >= TAIL_BYTES:
            self.output.seek(HEAD_BYTES)
            self._write(view[-TAIL_BYTES:])
            self.tail = TAIL_BYTES
            self.cursor = 0
        else:
            count = min(len(view), TAIL_BYTES - self.cursor)
            self.output.seek(HEAD_BYTES + self.cursor)
            self._write(view[:count])
            if count < len(view):
                self.output.seek(HEAD_BYTES)
                self._write(view[count:])
            self.tail = min(TAIL_BYTES, self.tail + len(view))
            self.cursor = (self.cursor + len(view)) % TAIL_BYTES
        self.layout = 'head_then_circular_tail' if self.tail == TAIL_BYTES and self.cursor else 'head_then_tail'

    def finish(self) -> None:
        if self.tail == TAIL_BYTES and self.cursor:
            self.output.seek(HEAD_BYTES)
            tail = self.output.read(TAIL_BYTES)
            if len(tail) != TAIL_BYTES:
                raise OSError('log_sink_short_read')
            self.output.seek(HEAD_BYTES)
            self._write(tail[self.cursor:] + tail[:self.cursor])
            self.cursor = 0
        self.layout = 'head_then_tail'


class NativeLogCapture:
    """Own exactly one host ADB client and its reader; stop never masks a test failure."""
    def __init__(self, process, output, path: Path, serial: str, uid: int, started: str):
        self._process = process
        self._window = _Window(output)
        self._path = path
        self._serial = serial
        self._uid = uid
        self._started = started
        self._errors = []
        self._lock = threading.Lock()
        self._stop_lock = threading.Lock()
        self._abandon = threading.Event()
        self._metadata = None
        self._reader = threading.Thread(target=self._pump, name='verification-uid-logcat', daemon=True)

    def _error(self, stage: str, error_type: str) -> None:
        with self._lock:
            if len(self._errors) < 8:
                self._errors.append(dict(stage=stage, error_type=error_type[:80]))

    def _pump(self) -> None:
        try:
            while not self._abandon.is_set():
                chunk = self._process.stdout.read(_CHUNK_BYTES)
                if not chunk:
                    break
                if not isinstance(chunk, bytes):
                    raise TypeError('log_reader_requires_bytes')
                with self._lock:
                    if self._abandon.is_set() or self._window.output.closed:
                        break
                    self._window.append(chunk)
        except Exception as error:
            self._error('stream', type(error).__name__)

    def stop(self) -> dict:
        with self._stop_lock:
            if self._metadata is not None:
                return copy.deepcopy(self._metadata)
            returncode = None
            early_exit = False
            try:
                returncode = self._process.poll()
                early_exit = returncode is not None
            except Exception as error:
                self._error('client_poll', type(error).__name__)
            if returncode is None:
                try:
                    self._process.terminate()
                except Exception as error:
                    self._error('client_terminate', type(error).__name__)
                try:
                    returncode = self._process.wait(timeout=3)
                except Exception as error:
                    if not isinstance(error, subprocess.TimeoutExpired):
                        self._error('client_wait', type(error).__name__)
                    try:
                        self._process.kill()
                        returncode = self._process.wait(timeout=3)
                    except Exception as kill_error:
                        self._error('client_kill_wait', type(kill_error).__name__)
            if early_exit and returncode != 0:
                self._error('client_exit', 'NonzeroExit')
            try:
                self._reader.join(timeout=3)
            except RuntimeError as error:
                self._error('reader_join', type(error).__name__)
            if self._reader.is_alive():
                self._abandon.set()
                self._error('reader_join', 'Timeout')
            # Unbuffered Popen stdout avoids closing a BufferedReader whose read lock another
            # thread might hold. Normal termination already supplied EOF and joined the reader.
            try:
                self._process.stdout.close()
            except Exception as error:
                self._error('reader_close', type(error).__name__)
            if self._reader.is_alive():
                self._reader.join(timeout=1)
            with self._lock:
                try:
                    self._window.finish()
                except Exception as error:
                    if len(self._errors) < 8:
                        self._errors.append(dict(stage='finalize', error_type=type(error).__name__[:80]))
                finally:
                    try:
                        self._window.output.close()
                    except Exception as error:
                        if len(self._errors) < 8:
                            self._errors.append(dict(stage='sink_close', error_type=type(error).__name__[:80]))
                try:
                    retained = self._path.stat().st_size
                except OSError:
                    retained = 0
                    if len(self._errors) < 8:
                        self._errors.append(dict(stage='sink_stat', error_type='OSError'))
                self._metadata = dict(
                    schema_version=1, kind='verification_uid_logcat', package=VERIFICATION_PACKAGE,
                    serial=self._serial, uid=self._uid, android_user=self._uid // _PER_USER_RANGE, path=str(self._path),
                    started_at=self._started, stopped_at=_now(),
                    status='diagnostic_failure' if self._errors else 'ended_before_stop' if early_exit else 'captured',
                    bytes_seen=self._window.seen, bytes_retained=retained,
                    bytes_dropped=max(0, self._window.seen - retained), cap_bytes=CAP_BYTES,
                    head_bytes=self._window.head, tail_bytes=self._window.tail,
                    truncated=self._window.seen > CAP_BYTES, layout=self._window.layout,
                    tail_cursor=self._window.cursor, client_returncode=returncode,
                    reader_stopped=not self._reader.is_alive(), file_closed=self._window.output.closed,
                    reader_stream_closed=bool(getattr(self._process.stdout, 'closed', False)),
                    errors=copy.deepcopy(self._errors))
            return copy.deepcopy(self._metadata)


def start_native_log_capture(adbPath: str | Path, serial: str, outputFile: str | Path) -> NativeLogCapture:
    """Start only the verification UID log. Caller supplies a new owned host output on Q.

    No application interaction, device log clear, whole-system capture or ADB-server operation.
    UID/start errors raise fixed NativeLogStartError; the caller retains its original test result.
    A successful start must be paired with capture.stop() in the caller's finally block.
    """
    if not isinstance(serial, str) or not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9_.:-]*', serial):
        raise ValueError('One explicit ADB serial is required')
    path = Path(outputFile)
    if not path.is_absolute():
        raise ValueError('Native log output must be an absolute owned path')
    if path.exists() or path.is_symlink():
        raise FileExistsError('Native log output already exists')
    for parent in path.parents:
        if parent.is_symlink() or getattr(parent, 'is_junction', lambda: False)():
            raise ValueError('Native log output must have ordinary directory ancestors')
    uid = _uid(str(adbPath), serial)
    path.parent.mkdir(parents=True, exist_ok=True)
    output = path.open('x+b', buffering=0)
    started = _now()
    try:
        process = subprocess.Popen(
            [str(adbPath), '-s', serial, 'logcat', '--uid=' + str(uid), '-T1', '-vthreadtime'],
            stdin=subprocess.DEVNULL, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, bufsize=0,
            creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0))
    except Exception as error:
        output.close()  # Keep even an empty partial file; never overwrite prior evidence.
        raise NativeLogStartError('logcat_start_failed', type(error).__name__) from None
    capture = NativeLogCapture(process, output, path, serial, uid, started)
    try:
        capture._reader.start()
    except Exception as error:
        capture.stop()
        raise NativeLogStartError('reader_start_failed', type(error).__name__) from None
    return capture
