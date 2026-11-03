"""Project App-owned documentation registries and check their standalone YAML views."""

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
    "config-keys": None,
    "dependencies": None,
    "ecosystem": None,
}


def registry_layout(index: dict, name: str) -> tuple[list[dict], list[str]]:
    facets = index["facets"]
    field = REGISTRIES[name]
    if field is None:
        layout = facets["registry-layout"]["value"]
        notes = facets["registry-notes"]["value"]
    else:
        layout = [{"section": field, "shape": "mapping", "keys": facets["registry-order"]["value"]}]
        notes = []

    if not isinstance(layout, list) or not layout:
        raise ValueError(f"Missing section order for {name}")
    sections = set()
    for section in layout:
        label, shape, keys = section["section"], section["shape"], section["keys"]
        if (not isinstance(label, str) or not label or label == "schema_version"
                or label in sections or shape not in ("mapping", "list")
                or not isinstance(keys, list) or not keys
                or any(not isinstance(key, str) or not key for key in keys)
                or len(keys) != len(set(keys))):
            raise ValueError(f"Invalid {name} registry section or key order: {label}")
        sections.add(label)
    if (not isinstance(notes, list)
            or any(not isinstance(line, str) or not line.startswith("# ")
                   or line.splitlines() != [line] for line in notes)):
        raise ValueError(f"Invalid {name} registry notes")
    return layout, notes


def project_registry(owner: Path, name: str) -> tuple[dict, list[str]]:
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
    if type(version) is not int or version != 1:
        raise ValueError(f"Invalid {name} registry version")

    layout, notes = registry_layout(index, name)
    targets = [relation["target"] for relation in index["relations"] if relation["type"] == "contains"]
    row_count = sum(len(section["keys"]) for section in layout)
    if len(targets) != row_count or len(targets) != len(records) or set(targets) != set(records):
        raise ValueError(f"{name} registry order, records and relations differ")

    registry = {"schema_version": version}
    ordered_targets = iter(targets)
    for section in layout:
        label, shape = section["section"], section["shape"]
        definitions = {}
        for key in section["keys"]:
            target = next(ordered_targets)
            record = records[target]
            facets = record["facets"]
            definition = facets["definition"]["value"]
            if (record["kind"] != "contract" or facets["registry-key"]["value"] != key
                    or not isinstance(definition, dict)
                    or (REGISTRIES[name] is None and facets["registry-section"]["value"] != label)):
                raise ValueError(f"Invalid {name} definition for {key}: {target}")
            if shape == "list":
                row_key = (f"{definition['from']}-to-{definition['to']}"
                           if label == "edges" else definition["id"])
                if row_key != key:
                    raise ValueError(f"Invalid {name} list key for {key}: {target}")
            definitions[key] = definition
        registry[label] = definitions if shape == "mapping" else list(definitions.values())
    return registry, notes


def render_registry(name: str, registry: dict, notes: list[str]) -> str:
    footer = "\n" + "\n".join(notes) + "\n" if notes else ""
    return (
        f"# Generated from agent-system/knowledge/{name}.\n"
        "# Edit the owner records, then run python -B docs/scripts/keyed_registry_knowledge.py --write.\n"
        + yaml.safe_dump(registry, allow_unicode=True, sort_keys=False, width=120)
        + footer
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
    registry, notes = project_registry(owner, name)
    check_view(docs_root / "registry" / f"{name}.yaml", name, render_registry(name, registry, notes))
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
            name: render_registry(name, *project_registry(owner, name))
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
