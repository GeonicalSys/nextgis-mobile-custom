"""Project maintained module manifests from their ordered owner records."""

from __future__ import annotations

from datetime import date
import json
from pathlib import Path
from typing import NamedTuple

import yaml


OWNER = "android_projects/android_gisapp"
TOPIC = "app-module-manifest"
DIRECTORY = Path("agent-system/knowledge") / TOPIC
VIEW = Path("app/docs/manifest.yaml")
EASYPICKER_MODULE = "easypicker-module"
METADATA_FIELDS = ("schema_version", "module_id", "repository_id", "maturity", "last_verified")
OBJECT_FIELDS = {
    "key_components": ("path", "responsibility"),
    "public_contracts": ("id", "symbol", "path", "consumers"),
    "storage_contracts": ("id", "path"),
}
REFERENCE_FIELDS = {
    "entrypoints", "settings_refs", "invariant_refs", "global_docs", "smoke_tests", "upstream_hotspots",
}


class ManifestProfile(NamedTuple):
    owner: str
    namespace: str
    topic: str
    view: Path
    empty_reference_fields: frozenset[str] = frozenset()

    @property
    def layout_id(self) -> str:
        return f"lisa:{self.namespace}:metadata:{self.topic}"


PROFILES = {
    "app": ManifestProfile(OWNER, "mobile", TOPIC, VIEW),
    EASYPICKER_MODULE: ManifestProfile(
        OWNER + "/easypicker", "easypicker", "easypicker-module-manifest", Path("docs/manifest.yaml"),
        frozenset({"settings_refs", "invariant_refs", "upstream_hotspots"}),
    ),
}


def facet(record: dict, name: str):
    return record["facets"][name]["value"]


def project_manifest(owner: Path, *, module_id: str = "app") -> dict:
    profile = PROFILES[module_id]
    directory = DIRECTORY.parent / profile.topic
    provenance = Path("agent-system/provenance") / f"{profile.topic}.json"
    records = {}
    for path in sorted((owner / directory).rglob("*.json")):
        record = json.loads(path.read_bytes())
        identity = record["id"]
        if (record["schema_version"] != 2 or record["owner"] != profile.owner or record["topic"] != profile.topic
                or identity in records or path.stem != identity.rsplit(":", 1)[-1]
                or record["provenance"] != {"manifest": provenance.as_posix(), "scopes": [identity]}):
            raise ValueError(f"Invalid or duplicate module manifest record: {path}")
        records[identity] = record

    layout = records.pop(profile.layout_id)
    if layout["kind"] != "metadata" or facet(layout, "view-path") != profile.view.as_posix():
        raise ValueError("Invalid module manifest layout")
    metadata = facet(layout, "manifest-metadata")
    if (set(metadata) != set(METADATA_FIELDS) or type(metadata["schema_version"]) is not int
            or metadata["module_id"] != module_id):
        raise ValueError("Invalid module manifest metadata fields")
    # Canonical JSON storage can sort object keys. YAML field order is part of the view.
    manifest = {field: metadata[field] for field in METADATA_FIELDS}
    manifest["last_verified"] = date.fromisoformat(manifest["last_verified"])

    field_order = facet(layout, "manifest-field-order")
    if len(field_order) != len(set(field_order)) or set(field_order) != OBJECT_FIELDS.keys() | REFERENCE_FIELDS:
        raise ValueError("Invalid module manifest section order")
    manifest.update((field, []) for field in field_order)

    targets = [edge for edge in layout["relations"] if edge["type"] == "contains"]
    if any(edge["status"] != "source_backed" for edge in targets):
        raise ValueError("Module manifest order requires source-backed relations")
    target_ids = [edge["target"] for edge in targets]
    if len(target_ids) != len(records) or set(target_ids) != records.keys():
        raise ValueError("Module manifest order and record membership differ")
    actual_sections = []
    seen_reference_fields = set()
    for identity in target_ids:
        record = records[identity]
        field = facet(record, "manifest-field")
        value = facet(record, "definition")
        if not actual_sections or actual_sections[-1] != field:
            actual_sections.append(field)
        if field in OBJECT_FIELDS:
            keys = OBJECT_FIELDS[field]
            if record["kind"] != "contract" or not isinstance(value, dict) or set(value) != set(keys):
                raise ValueError(f"Invalid module manifest definition: {identity}")
            manifest[field].append({key: value[key] for key in keys})
        elif field in REFERENCE_FIELDS:
            if (record["kind"] != "resource" or field in seen_reference_fields or not isinstance(value, list)
                    or (not value and field not in profile.empty_reference_fields)
                    or any(not isinstance(item, str) or not item for item in value)):
                raise ValueError(f"Invalid module manifest reference list: {identity}")
            seen_reference_fields.add(field)
            manifest[field].extend(value)
        else:
            raise ValueError(f"Unknown module manifest section: {field}")
    if actual_sections != field_order:
        raise ValueError("Module manifest sections differ from their ordered records")
    return manifest


def generated_heading(module_id: str) -> str:
    return f"# Generated from agent-system/knowledge/{PROFILES[module_id].topic}."


def render_manifest(manifest: dict, *, module_id: str = "app") -> str:
    select_module = "" if module_id == "app" else f" --module {module_id}"
    return (
        f"{generated_heading(module_id)}\n"
        f"# Edit the owner records, then run python -B docs/scripts/prose_knowledge.py --write{select_module}.\n"
        + yaml.safe_dump(manifest, allow_unicode=True, sort_keys=False, width=120)
    )


def check_view(owner: Path, manifest: dict, *, module_id: str = "app") -> None:
    view = owner / PROFILES[module_id].view
    if view.read_text(encoding="utf-8") != render_manifest(manifest, module_id=module_id):
        raise ValueError(f"Generated module manifest is stale: {view}")
