"""Project App-owned UI evidence into the standalone catalogue."""

from __future__ import annotations

import argparse
import json
from pathlib import Path


OWNER = "android_projects/android_gisapp"
TOPIC = "ui-contracts"
DIRECTORY = Path("agent-system/knowledge/ui-contracts")
CATALOGUE = Path("docs/registry/ui-contracts.json")
INDEX_ID = "lisa:mobile:metadata:ui-contracts-registry"
SECTION_KINDS = {
    "sources": "evidence",
    "contracts": "contract",
    "widget_evidence": "evidence",
    "coverage": "metadata",
    "non_method_evidence": "evidence",
}


def evidence_key(section: str, row: dict) -> str | list[str]:
    if section == "widget_evidence":
        return [row["repository"], row["path"], row["id"]]
    if section == "non_method_evidence":
        return [row["repository"], row["path"]]
    return row["id"]


def project_catalogue(workspace: Path) -> dict:
    records = {}
    for path in sorted((workspace / DIRECTORY).rglob("*.json")):
        record = json.loads(path.read_text(encoding="utf-8"))
        identity = record["id"]
        if (record["schema_version"] != 2 or record["owner"] != OWNER
                or record["topic"] != TOPIC or identity in records):
            raise ValueError(f"Invalid or duplicate UI record: {path}")
        records[identity] = record

    index = records.pop(INDEX_ID, None)
    if index is None or index["kind"] != "metadata":
        raise ValueError("Missing or invalid UI catalogue metadata")
    schema = index["facets"]["registry-schema"]["value"]
    layout = index["facets"]["registry-layout"]["value"]
    if type(schema) is not int or schema != 1 or not isinstance(layout, dict) or set(layout) != set(SECTION_KINDS):
        raise ValueError("Invalid UI catalogue schema or sections")

    for name, section in layout.items():
        shape = "mapping" if name == "coverage" else "list"
        if section["shape"] != shape or type(section["entries"]) is not int or section["entries"] < 0:
            raise ValueError(f"Invalid UI catalogue section layout: {name}")
    targets = [relation["target"] for relation in index["relations"] if relation["type"] == "contains"]
    if (len(targets) != sum(section["entries"] for section in layout.values())
            or len(targets) != len(records) or set(targets) != set(records)):
        raise ValueError("UI catalogue layout, records and relations differ")

    catalogue = {"schema_version": schema}
    ordered_targets = iter(targets)
    for name, section in layout.items():
        rows = {}
        for _ in range(section["entries"]):
            target = next(ordered_targets)
            record = records[target]
            facets = record["facets"]
            key = facets["registry-key"]["value"]
            definition = facets["definition"]["value"]
            if record["kind"] != SECTION_KINDS[name] or facets["registry-section"]["value"] != name:
                raise ValueError(f"UI catalogue record does not belong to {name}: {target}")
            if name == "coverage":
                if not isinstance(key, str) or not key:
                    raise ValueError(f"Invalid UI coverage key: {target}")
            elif not isinstance(definition, dict) or key != evidence_key(name, definition):
                raise ValueError(f"Invalid UI evidence key in {name}: {target}")
            identity = tuple(key) if isinstance(key, list) else key
            if identity in rows:
                raise ValueError(f"Duplicate UI catalogue key in {name}: {key}")
            rows[identity] = definition
        catalogue[name] = rows if name == "coverage" else list(rows.values())
    return catalogue


def render_catalogue(catalogue: dict) -> str:
    return json.dumps(catalogue, ensure_ascii=False, indent=2, allow_nan=False) + "\n"


def check_generated(view: Path, catalogue: dict) -> None:
    if view.read_text(encoding="utf-8") != render_catalogue(catalogue):
        raise ValueError(f"Generated UI catalogue is stale: {view}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--check", action="store_true")
    mode.add_argument("--write", action="store_true")
    args = parser.parse_args()
    workspace = Path(__file__).resolve().parents[2]
    view = workspace / CATALOGUE
    try:
        catalogue = project_catalogue(workspace)
        if args.write:
            view.write_text(render_catalogue(catalogue), encoding="utf-8", newline="\n")
        else:
            check_generated(view, catalogue)
    except (OSError, ValueError, KeyError, TypeError) as exc:
        parser.exit(1, f"UI catalogue projection failed: {exc}\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
