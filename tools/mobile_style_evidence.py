"""Retain bounded PNG evidence from fixed QA folders before verification data is reset."""
from __future__ import annotations
import hashlib
import json
from pathlib import Path
import re
import subprocess
from mobile_test_support import PACKAGE


def collect_style_images(adb: Path, serial: str, destination: Path) -> list[dict]:
    prefix = [str(adb), '-s', serial, 'exec-out', 'run-as', PACKAGE]
    def read(*args):
        result = subprocess.run(prefix + list(args), capture_output=True, timeout=10)
        return result.stdout if result.returncode == 0 else b''
    images = []
    total = 0
    for folder in ('firefly-qa', 'polygon-editor-qa'):
        names = read('ls', '-1', 'files/' + folder).decode('utf-8', errors='replace').splitlines()
        for name in names[:32]:
            if not re.fullmatch(r'[A-Za-z0-9_.-]+\.png', name):
                continue
            remote = 'files/' + folder + '/' + name
            try:
                size = int(read('stat', '-c', '%s', remote))
            except ValueError:
                continue
            if not 8 <= size <= 4 * 1024 * 1024 or total + size > 16 * 1024 * 1024:
                continue
            data = read('cat', remote)
            if len(data) != size or not data.startswith(b'\x89PNG\r\n\x1a\n'):
                continue
            local = destination / folder / name
            local.parent.mkdir(parents=True, exist_ok=True)
            local.write_bytes(data)
            total += size
            images.append({'path': folder + '/' + name, 'bytes': size, 'sha256': hashlib.sha256(data).hexdigest()})
    if images:
        (destination / 'index.json').write_text(json.dumps(images, indent=2), encoding='utf-8')
    return images
