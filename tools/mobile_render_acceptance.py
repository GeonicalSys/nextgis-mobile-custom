"""Hardware evidence for the mandatory cold-start MVT gesture workload."""
from __future__ import annotations
import json
from pathlib import Path

STRESS_CLASS = 'com.nextgis.mobile.rendering.MapRenderStressTest'
STRESS_CASE = STRESS_CLASS + '#coldStartPanPinchAndRecreationKeepHeavyMvtRenderingAlive'
STRESS_SEED = STRESS_CLASS + '#seedHeavyLocalMapForARealColdProcessStart'


def validate_stress(evidence: dict) -> None:
    if evidence.get('schema_version') != 1 or evidence.get('status') != 'passed':
        raise ValueError('Render stress did not complete successfully')
    for field, minimum in {'elapsed_ms': 90_000, 'gestures': 60, 'frames': 60,
                           'recreations': 2, 'features': 6480, 'vertices_per_feature': 65,
                           'mvt_layers': 2, 'visible_features': 1}.items():
        value = evidence.get(field)
        if type(value) is not int or value < minimum:
            raise ValueError('Missing/insufficient render stress evidence: ' + field)
    for field in ('gpu', 'model', 'manufacturer'):
        value = evidence.get(field)
        if not isinstance(value, str) or not value.strip() or 'null' in value.lower():
            raise ValueError('Missing actual hardware identity: ' + field)


def record_stress(path: Path, evidence: dict, device: dict, fingerprint: str) -> dict:
    validate_stress(evidence)
    kind = 'emulator' if device['hardware'] in ('ranchu', 'goldfish') else (
        'tablet' if 'tablet' in device.get('characteristics', '').split(',')
        or evidence.get('smallest_width_dp', 0) >= 600 else 'phone')
    row = {'source_fingerprint': fingerprint, 'kind': kind, 'device': device, 'evidence': evidence}
    previous = json.loads(path.read_text(encoding='utf-8')) if path.is_file() else {'entries': []}
    entries = [item for item in previous.get('entries', [])
               if item.get('source_fingerprint') == fingerprint and
               (item.get('kind'), item.get('evidence', {}).get('model')) != (kind, evidence['model'])]
    entries.append(row)
    result = {'schema_version': 1, 'entries': entries}
    path.parent.mkdir(parents=True, exist_ok=True)
    pending = path.with_suffix('.new')
    pending.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
    pending.replace(path)
    return result


def check_stress_matrix(matrix: dict, fingerprint: str) -> None:
    if not isinstance(matrix, dict) or matrix.get('schema_version') != 1:
        raise ValueError('Publication requires current hardware render stress evidence')
    rows = matrix.get('entries', [])
    kinds = set(); models = set()
    for row in rows:
        if row.get('source_fingerprint') != fingerprint:
            raise ValueError('Render stress evidence belongs to changed application/test sources')
        validate_stress(row.get('evidence', {}))
        if not row.get('device', {}).get('fingerprint'):
            raise ValueError('Render stress is missing the Android build fingerprint')
        kinds.add(row.get('kind'))
        if row.get('kind') != 'emulator': models.add(row['evidence']['model'])
    if not {'phone', 'tablet', 'emulator'} <= kinds:
        raise ValueError('Render stress requires a physical phone, a physical tablet and an emulator')
    if 'SM-X115' not in models:
        raise ValueError('The incident Samsung SM-X115 still needs physical render stress verification')
