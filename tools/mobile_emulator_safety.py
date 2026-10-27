"""Fail closed before a graphics workload can touch a host GPU."""
import re

SOFTWARE_GPU = 'swiftshader_indirect'  # Installed Emulator35.6.11; no hardware fallback.


def require_software_graphics(surface_flinger: str) -> str:
    lines = re.findall(r'^\s*(GLES:.*)$', surface_flinger, re.MULTILINE)
    if len(lines) != 1 or not re.search(r'SwiftShader|llvmpipe|lavapipe', lines[0], re.IGNORECASE):
        raise ValueError('Emulator graphics must report an actual software renderer; host/unknown GPU is blocked')
    return lines[0].strip()
