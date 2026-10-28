"""Hardware evidence for the mandatory cold-process direct-SQLite gesture workload."""
from __future__ import annotations
import json
from pathlib import Path

STRESS_CLASS = 'com.nextgis.mobile.rendering.MapRenderStressTest'
STRESS_CASE = STRESS_CLASS + '#coldStartPanPinchAndRecreationKeepHeavyMvtRenderingAlive'
STRESS_SEED = STRESS_CLASS + '#seedHeavyLocalMapForARealColdProcessStart'


def validate_stress(evidence: dict) -> None:
    if evidence.get('schema_version') != 2 or evidence.get('status') != 'passed':
        raise ValueError('Render stress did not complete successfully')
    for field, minimum in {'elapsed_ms': 90_000, 'gestures': 60, 'frames': 60,
                           'workload_elapsed_ms': 90_000, 'source_checks': 60, 'sqlite_layers': 2,
                           'recreations': 2, 'features': 6480, 'vertices_per_feature': 65,
                           'mvt_layers': 2, 'visible_features': 1, 'peak_pss_kib': 1,
                           'pan_changes': 50, 'pinch_changes': 15,
                           'heading_updates': 300, 'peak_process_rss_bytes': 1,
                           'peak_java_bytes': 1, 'peak_native_bytes': 1}.items():
        value = evidence.get(field)
        if type(value) is not int or value < minimum:
            raise ValueError('Missing/insufficient render stress evidence: ' + field)
    if evidence.get('render_backend') != 'sqlite_native':
        raise ValueError('Render stress must exercise the actual direct SQLite backend')
    if evidence['sqlite_layers'] != 2 or evidence['mvt_layers'] != 2:
        raise ValueError('Both actual sources must use direct SQLite')
    if evidence['features'] != 6480 or evidence['vertices_per_feature'] != 65:
        raise ValueError('Render stress must preserve the complete independent6480-by65 fixture')
    if type(evidence.get('global_feature_count')) is not int or evidence['global_feature_count'] != 0:
        raise ValueError('Direct SQLite stress must not keep a global Java feature list')
    for field in ('seed_pid', 'pid'):
        if type(evidence.get(field)) is not int or evidence[field] <= 0:
            raise ValueError('Missing actual process identity: ' + field)
    if evidence['seed_pid'] == evidence['pid']:
        raise ValueError('The seeded map was not read in a new cold process')
    if evidence['elapsed_ms'] != evidence['workload_elapsed_ms']:
        raise ValueError('The interaction duration must be captured after the complete workload')
    startup, total = evidence.get('startup_elapsed_ms'), evidence.get('total_elapsed_ms')
    if type(startup) is not int or startup < 0 or type(total) is not int or total < startup + evidence['workload_elapsed_ms']:
        raise ValueError('Startup and complete workload timings are inconsistent')
    coverage = evidence.get('visible_coverage')
    if not isinstance(coverage, list) or len(coverage) != 2:
        raise ValueError('Both fixture layers require independent visible-ID coverage')
    required = rendered = 0
    for row, count in zip(coverage, (6400, 80)):
        if not isinstance(row, dict) or type(row.get('fixture_features')) is not int or row['fixture_features'] != count:
            raise ValueError('Visible coverage must use the unchanged fixture layers')
        for field in ('required_ids_count', 'rendered_ids_count', 'missing_ids_count', 'invalid_role_hits', 'unexpected_id_hits'):
            if type(row.get(field)) is not int or row[field] < 0:
                raise ValueError('Missing/invalid visible coverage count: ' + field)
        if not row['required_ids_count'] <= row['rendered_ids_count'] <= count:
            raise ValueError('Rendered IDs do not cover the independent viewport requirement')
        if row['missing_ids_count'] or row['invalid_role_hits'] or row['unexpected_id_hits']:
            raise ValueError('Native viewport contains missing objects, invalid roles or unknown IDs')
        required += row['required_ids_count']; rendered += row['rendered_ids_count']
    if (required <= 0 or type(evidence.get('visible_required_count')) is not int
            or evidence['visible_required_count'] != required
            or type(evidence.get('visible_missing_count')) is not int or evidence['visible_missing_count'] != 0
            or evidence['visible_features'] != rendered):
        raise ValueError('Visible-ID totals do not prove independent viewport completeness')
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
