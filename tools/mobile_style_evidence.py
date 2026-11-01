"""Retain bounded images and one source-binding witness from the verification APK."""
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
    for folder in ('firefly-qa', 'polygon-editor-qa', 'line-label-qa', 'maintenance-qa', 'large-text-qa', 'underlay-qa'):
        names = read('ls', '-1', 'files/' + folder).decode('utf-8', errors='replace').splitlines()
        for name in names[:32]:
            binding = folder == 'firefly-qa' and name == 'classic-binding.json'
            if not binding and not re.fullmatch(r'[A-Za-z0-9_.-]+\.png', name):
                continue
            remote = 'files/' + folder + '/' + name
            try:
                size = int(read('stat', '-c', '%s', remote))
            except ValueError:
                continue
            limit = 64 * 1024 if binding else 4 * 1024 * 1024
            if not 8 <= size <= limit or total + size > 16 * 1024 * 1024:
                continue
            data = read('cat', remote)
            if len(data) != size:
                continue
            if binding:
                try:
                    if not isinstance(json.loads(data.decode('utf-8')), dict):
                        continue
                except (ValueError, UnicodeDecodeError):
                    continue
            elif not data.startswith(b'\x89PNG\r\n\x1a\n'):
                continue
            local = destination / folder / name
            local.parent.mkdir(parents=True, exist_ok=True)
            local.write_bytes(data)
            total += size
            images.append({'path': folder + '/' + name, 'bytes': size, 'sha256': hashlib.sha256(data).hexdigest()})
    if images:
        (destination / 'index.json').write_text(json.dumps(images, indent=2), encoding='utf-8')
    return images
