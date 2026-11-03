"""Project App-owned keyed registries and check their standalone YAML views."""

from __future__ import annotations

import argparse
import json
from pathlib import Path

import yaml


OWNER = "android_projects/android_gisapp"
REGISTRIES = {
    "repositories": "repositories",
    "modules": "modules",
    "invariants": "invariants",
    "upstream-overlaps": "hotspots",
    "smoke-tests": "tests",
}


def project_registry(owner: Path, name: str) -> dict:
    directory = owner / "agent-system" / "knowledge" / name
    records = {}
    for path in sorted(directory.rglob("*.json")):
        record = json.loads(path.read_text(encoding="utf-8"))
        identity = record["id"]
        if (record["schema_version"] != 2 or record["owner"] != OWNER
                or record["topic"] != name or identity in records):
            raise ValueError(f"Invalid or duplicate {name} record: {path}")
        records[identity] = record

    index = records.pop(f"lisa:mobile:metadata:{name}-registry", None)
    if index is None or index["kind"] != "metadata":
        raise ValueError(f"Missing or invalid {name} registry metadata")
    version = index["facets"]["registry-schema"]["value"]
    order = index["facets"]["registry-order"]["value"]
    if (type(version) is not int or version != 1 or not isinstance(order, list)
            or not order or any(not isinstance(key, str) or not key for key in order)
            or len(set(order)) != len(order)):
        raise ValueError(f"Invalid {name} registry version or key order")

    targets = [relation["target"] for relation in index["relations"] if relation["type"] == "contains"]
    if len(targets) != len(order) or len(targets) != len(records) or set(targets) != set(records):
        raise ValueError(f"{name} registry order, records and relations differ")
    definitions = {}
    for key, target in zip(order, targets, strict=True):
        record = records[target]
        facets = record["facets"]
        definition = facets["definition"]["value"]
        if (record["kind"] != "contract" or facets["registry-key"]["value"] != key
                or not isinstance(definition, dict)):
            raise ValueError(f"Invalid {name} definition for {key}: {target}")
        definitions[key] = definition
    return {"schema_version": version, REGISTRIES[name]: definitions}


def render_registry(name: str, registry: dict) -> str:
    return (
        f"# Generated from agent-system/knowledge/{name}.\n"
        "# Edit the owner records, then run python -B docs/scripts/keyed_registry_knowledge.py --write.\n"
        + yaml.safe_dump(registry, allow_unicode=True, sort_keys=False, width=120)
    )


def check_view(view: Path, name: str, rendered: str) -> None:
    if view.read_text(encoding="utf-8") != rendered:
        raise ValueError(f"Generated {name} registry is stale: {view}")


def project_for_docs(docs_root: Path, name: str) -> dict | None:
    """Return None for an independent docs tree using the standalone YAML."""
    owner = docs_root.parent
    sources = owner / "agent-system"
    if docs_root.name != "docs" or not (sources.exists() or sources.is_symlink()):
        return None
    registry = project_registry(owner, name)
    check_view(docs_root / "registry" / f"{name}.yaml", name, render_registry(name, registry))
    return registry


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--check", action="store_true")
    mode.add_argument("--write", action="store_true")
    args = parser.parse_args()
    owner = Path(__file__).resolve().parents[2]
    try:
        views = {
            name: render_registry(name, project_registry(owner, name))
            for name in REGISTRIES
        }
        for name, rendered in views.items():
            view = owner / "docs" / "registry" / f"{name}.yaml"
            if args.write:
                view.write_text(rendered, encoding="utf-8", newline="\n")
            else:
                check_view(view, name, rendered)
    except (OSError, ValueError, KeyError, TypeError) as exc:
        parser.exit(1, f"Keyed registry projection failed: {exc}\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
