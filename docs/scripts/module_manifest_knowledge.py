"""Project the App module manifest from its ordered owner records."""

from __future__ import annotations

from datetime import date
import json
from pathlib import Path

import yaml


OWNER = "android_projects/android_gisapp"
TOPIC = "app-module-manifest"
DIRECTORY = Path("agent-system/knowledge") / TOPIC
PROVENANCE = Path("agent-system/provenance") / f"{TOPIC}.json"
VIEW = Path("app/docs/manifest.yaml")
LAYOUT_ID = f"lisa:mobile:metadata:{TOPIC}"
METADATA_FIELDS = ("schema_version", "module_id", "repository_id", "maturity", "last_verified")
OBJECT_FIELDS = {
    "key_components": ("path", "responsibility"),
    "public_contracts": ("id", "symbol", "path", "consumers"),
    "storage_contracts": ("id", "path"),
}
REFERENCE_FIELDS = {
    "entrypoints", "settings_refs", "invariant_refs", "global_docs", "smoke_tests", "upstream_hotspots",
}


def facet(record: dict, name: str):
    return record["facets"][name]["value"]


def project_manifest(owner: Path) -> dict:
    records = {}
    for path in sorted((owner / DIRECTORY).rglob("*.json")):
        record = json.loads(path.read_bytes())
        identity = record["id"]
        if (record["schema_version"] != 2 or record["owner"] != OWNER or record["topic"] != TOPIC
                or identity in records or path.stem != identity.rsplit(":", 1)[-1]
                or record["provenance"] != {"manifest": PROVENANCE.as_posix(), "scopes": [identity]}):
            raise ValueError(f"Invalid or duplicate App module manifest record: {path}")
        records[identity] = record

    layout = records.pop(LAYOUT_ID)
    if layout["kind"] != "metadata" or facet(layout, "view-path") != VIEW.as_posix():
        raise ValueError("Invalid App module manifest layout")
    metadata = facet(layout, "manifest-metadata")
    if set(metadata) != set(METADATA_FIELDS) or type(metadata["schema_version"]) is not int:
        raise ValueError("Invalid App module manifest metadata fields")
    # Canonical JSON storage can sort object keys. YAML field order is part of the view.
    manifest = {field: metadata[field] for field in METADATA_FIELDS}
    manifest["last_verified"] = date.fromisoformat(manifest["last_verified"])

    field_order = facet(layout, "manifest-field-order")
    if len(field_order) != len(set(field_order)) or set(field_order) != OBJECT_FIELDS.keys() | REFERENCE_FIELDS:
        raise ValueError("Invalid App module manifest section order")
    manifest.update((field, []) for field in field_order)

    targets = [edge for edge in layout["relations"] if edge["type"] == "contains"]
    if any(edge["status"] != "source_backed" for edge in targets):
        raise ValueError("App module manifest order requires source-backed relations")
    target_ids = [edge["target"] for edge in targets]
    if len(target_ids) != len(records) or set(target_ids) != records.keys():
        raise ValueError("App module manifest order and record membership differ")
    actual_sections = []
    for identity in target_ids:
        record = records[identity]
        field = facet(record, "manifest-field")
        value = facet(record, "definition")
        if not actual_sections or actual_sections[-1] != field:
            actual_sections.append(field)
        if field in OBJECT_FIELDS:
            keys = OBJECT_FIELDS[field]
            if record["kind"] != "contract" or not isinstance(value, dict) or set(value) != set(keys):
                raise ValueError(f"Invalid App module manifest definition: {identity}")
            manifest[field].append({key: value[key] for key in keys})
        elif field in REFERENCE_FIELDS:
            if (record["kind"] != "resource" or manifest[field] or not isinstance(value, list) or not value
                    or any(not isinstance(item, str) or not item for item in value)):
                raise ValueError(f"Invalid App module manifest reference list: {identity}")
            manifest[field].extend(value)
        else:
            raise ValueError(f"Unknown App module manifest section: {field}")
    if actual_sections != field_order:
        raise ValueError("App module manifest sections differ from their ordered records")
    return manifest


def render_manifest(manifest: dict) -> str:
    return (
        "# Generated from agent-system/knowledge/app-module-manifest.\n"
        "# Edit the owner records, then run python -B docs/scripts/prose_knowledge.py --write.\n"
        + yaml.safe_dump(manifest, allow_unicode=True, sort_keys=False, width=120)
    )


def check_view(owner: Path, manifest: dict) -> None:
    view = owner / VIEW
    if view.read_text(encoding="utf-8") != render_manifest(manifest):
        raise ValueError(f"Generated App module manifest is stale: {view}")
