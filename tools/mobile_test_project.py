"""Select the explicitly permitted Collector project and copy only its layer policies."""
from __future__ import annotations
import json
import re
from pathlib import Path

SOURCE_PACKAGE = 'com.nextgis.mobile.geonical'
PROJECT_ROOT = '/sdcard/Android/data/' + SOURCE_PACKAGE + '/files/map/collector_projects'
DEFAULT_PROJECT = '1403'


class ProjectSelectionRequired(ValueError):
    pass


def load_policy_fixture(path: Path, selector: str) -> dict:
    """Accept only an explicitly selected, bounded policy snapshot, never project/business data."""
    if path.stat().st_size>256*1024: raise ValueError('Policy snapshot is too large')
    raw=path.read_bytes()
    if len(raw)>256*1024: raise ValueError('Policy snapshot is too large')
    fixture=json.loads(raw.decode('utf-8-sig'))
    if not isinstance(fixture,dict) or set(fixture)!={'schema_version','project_id','layers'} \
            or type(fixture['schema_version']) is not int or fixture['schema_version']!=1:
        raise ValueError('Unknown policy snapshot schema')
    project_id=fixture['project_id']
    if type(project_id) is not int or project_id<=0 or selector!=str(project_id):
        raise ProjectSelectionRequired('Snapshot project ID must match the explicitly selected --test-project')
    layers=fixture['layers']
    if not isinstance(layers,list) or not 0<len(layers)<=500: raise ValueError('Invalid policy layer count')
    keys={'name','geometry_type','ngw','managed','is_editable','collector_editable','sync_direction','expected_editable'}
    boolean_keys={'ngw','managed','is_editable','collector_editable','expected_editable'}
    for layer in layers:
        if not isinstance(layer,dict) or set(layer)!=keys: raise ValueError('Unexpected data in policy snapshot')
        if not isinstance(layer['name'],str) or not 0<len(layer['name'])<=512: raise ValueError('Invalid layer name')
        if type(layer['geometry_type']) is not int or layer['geometry_type'] not in range(1,7):
            raise ValueError('Invalid vector geometry type')
        if any(type(layer[key]) is not bool for key in boolean_keys): raise ValueError('Invalid policy boolean')
        direction=layer['sync_direction']
        if type(direction) is not int or direction not in range(4): raise ValueError('Invalid sync direction')
        allowed=layer['collector_editable'] and bool(direction&1) if layer['managed'] else layer['is_editable'] and layer['collector_editable']
        if layer['expected_editable']!=allowed: raise ValueError('Inconsistent editability expectation')
    if not any(layer['expected_editable'] for layer in layers): raise ValueError('No editable vector layers')
    return fixture


def choose_project(projects: list[dict], selector: str) -> dict:
    matches = [p for p in projects if selector == p['directory']
               or selector == str(p['id']) or selector.casefold() == p['name'].casefold()]
    if len(matches) != 1:
        choices = '; '.join(str(p['id']) + ' «' + p['name'] + '» [' + p['directory'] + ']'
                            for p in projects) or 'нет доступных проектов'
        raise ProjectSelectionRequired('Разрешённый тестовый проект ' + selector
                                       + ' не найден однозначно. На каком проекте можно протестировать? '
                                       + choices)
    return matches[0]


def read_projects(shell) -> list[dict]:
    projects = []
    for directory in shell('ls', PROJECT_ROOT).splitlines():
        if not re.fullmatch(r'[A-Za-z0-9_-]+', directory):
            continue
        try:
            metadata = json.loads(shell('cat', PROJECT_ROOT + '/' + directory + '/project.json'))
        except (ValueError, TypeError):
            # An unrelated interrupted import must not hide the permitted intact project.
            continue
        projects.append({'directory': directory, 'id': metadata.get('project_remote_id'),
                         'name': str(metadata.get('name', ''))})
    return projects


def snapshot_project(shell, project: dict) -> dict:
    """No accounts, endpoints, features, forms, coordinates or credentials enter the fixture."""
    base = PROJECT_ROOT + '/' + project['directory']
    composition = json.loads(shell('cat', base + '/map.ngm'))
    layers = []
    for entry in composition.get('layers', []):
        directory = entry.get('path', '')
        if not re.fullmatch(r'layer_[A-Za-z0-9_-]+', directory):
            continue
        config = json.loads(shell('cat', base + '/' + directory + '/config.json'))
        if 'geometry_type' not in config:
            continue
        managed = bool((config.get('layer_origin') or {}).get('managed_by_project'))
        editable = bool(config.get('is_editable', True))
        collector_editable = bool(config.get('collector_editable', True))
        direction = int(config.get('sync_direction', 3))
        allowed = collector_editable and bool(direction & 1) if managed else editable and collector_editable
        layers.append({'name': str(config['name']), 'geometry_type': int(config['geometry_type']),
                       'ngw': 'account' in config, 'managed': managed,
                       'is_editable': editable, 'collector_editable': collector_editable,
                       'sync_direction': direction, 'expected_editable': allowed})
    if not any(layer['expected_editable'] for layer in layers):
        raise ValueError('The selected test project has no editable vector layers')
    return {'schema_version': 1, 'project_id': project['id'], 'layers': layers}
