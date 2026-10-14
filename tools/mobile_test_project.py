"""Select the explicitly permitted Collector project and copy only its layer policies."""
from __future__ import annotations
import json
import re

SOURCE_PACKAGE = 'com.nextgis.mobile.geonical'
PROJECT_ROOT = '/sdcard/Android/data/' + SOURCE_PACKAGE + '/files/map/collector_projects'
DEFAULT_PROJECT = '1403'


class ProjectSelectionRequired(ValueError):
    pass


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
