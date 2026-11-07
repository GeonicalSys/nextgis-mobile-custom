"""Project App-owned change-impact records for the docs validator and YAML view."""

from __future__ import annotations

import argparse
import json
from pathlib import Path

import yaml


OWNER = "android_projects/android_gisapp"
TOPIC = "change-impact"
DIRECTORY = Path("agent-system/knowledge/change-impact")
REGISTRY = Path("docs/registry/change-impact.yaml")
INDEX_ID = "lisa:mobile:metadata:change-impact-registry"


def project_change_impact(workspace: Path) -> dict:
    records = {}
    for path in sorted((workspace / DIRECTORY).rglob("*.json")):
        record = json.loads(path.read_text(encoding="utf-8"))
        identity = record["id"]
        if (record["schema_version"] != 2 or record["owner"] != OWNER
                or record["topic"] != TOPIC or identity in records):
            raise ValueError(f"Invalid or duplicate change-impact record: {path}")
        records[identity] = record

    index = records.pop(INDEX_ID, None)
    if index is None or index["kind"] != "metadata":
        raise ValueError("Missing or invalid change-impact registry metadata")
    version = index["facets"]["registry-schema"]["value"]
    order = index["facets"]["registry-order"]["value"]
    if (type(version) is not int or version != 1 or not isinstance(order, list) or not order
            or any(not isinstance(key, str) or not key for key in order)
            or len(order) != len(set(order))):
        raise ValueError("Invalid change-impact registry version or trigger order")

    targets = [relation["target"] for relation in index["relations"] if relation["type"] == "contains"]
    if len(targets) != len(order) or len(targets) != len(records) or set(targets) != set(records):
        raise ValueError("Change-impact registry order, records and relations differ")

    triggers = []
    for key, target in zip(order, targets, strict=True):
        record = records[target]
        definition = record["facets"]["definition"]["value"]
        if (record["kind"] != "contract" or not isinstance(definition, dict)
                or definition.get("id") != key):
            raise ValueError(f"Invalid change-impact definition for {key}: {target}")
        triggers.append(definition)
    return {"schema_version": version, "triggers": triggers}


def render_change_impact(registry: dict) -> str:
    return (
        "# Generated from agent-system/knowledge/change-impact.\n"
        "# Edit the owner records, then run python -B docs/scripts/change_impact_knowledge.py --write.\n"
        + yaml.safe_dump(registry, allow_unicode=True, sort_keys=False, width=120)
    )


def check_generated(view: Path, registry: dict) -> None:
    if view.read_text(encoding="utf-8") != render_change_impact(registry):
        raise ValueError(f"Generated change-impact registry is stale: {view}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--check", action="store_true")
    mode.add_argument("--write", action="store_true")
    args = parser.parse_args()
    workspace = Path(__file__).resolve().parents[2]
    view = workspace / REGISTRY
    try:
        registry = project_change_impact(workspace)
        if args.write:
            view.write_text(render_change_impact(registry), encoding="utf-8", newline="\n")
        else:
            check_generated(view, registry)
    except (OSError, ValueError, KeyError, TypeError) as exc:
        parser.exit(1, f"Change-impact projection failed: {exc}\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
