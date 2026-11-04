"""Seal App structured views after the ordinary owner renderers check their bytes."""

from __future__ import annotations

import argparse
from hashlib import sha256
import json
from pathlib import Path
import runpy

OWNER = "android_projects/android_gisapp"
PROOF = Path("agent-system/provenance/structured-views.json")
FORMAT = "lisa.agent-system.app-structured-views.v1"
SCRIPTS = Path("docs/scripts")
EDITOR = SCRIPTS / "structured_view_proof.py"
KEYED = ("repositories", "modules", "invariants", "upstream-overlaps", "smoke-tests",
         "config-keys", "dependencies", "ecosystem")


def text_hash(raw: bytes) -> str:
    return sha256(raw.replace(b"\r\n", b"\n")).hexdigest()


def value_hash(value) -> str:
    return sha256(json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode()).hexdigest()


def structured_views(owner: Path):
    keyed_path = SCRIPTS / "keyed_registry_knowledge.py"
    keyed = runpy.run_path(str(owner / keyed_path))
    for name in KEYED:
        registry, notes = keyed["project_registry"](owner, name)
        yield Path(f"docs/registry/{name}.yaml"), name, f"lisa:mobile:metadata:{name}-registry", keyed_path, keyed["render_registry"](name, registry, notes)
    for path, topic, project, render in (
        (Path("docs/registry/change-impact.yaml"), "change-impact", "project_change_impact", "render_change_impact"),
        (Path("app/docs/manifest.yaml"), "app-module-manifest", "project_manifest", "render_manifest"),
        (Path("docs/registry/ui-contracts.json"), "ui-contracts", "project_catalogue", "render_catalogue"),
    ):
        tool_name = {
            "change-impact": "change_impact_knowledge.py",
            "app-module-manifest": "module_manifest_knowledge.py",
            "ui-contracts": "ui_catalogue_knowledge.py",
        }[topic]
        tool = SCRIPTS / tool_name
        projector = runpy.run_path(str(owner / tool))
        index = topic if topic == "app-module-manifest" else topic + "-registry"
        yield path, topic, f"lisa:mobile:metadata:{index}", tool, projector[render](projector[project](owner))


def build_proof(owner: Path) -> dict:
    result = {}
    for view, topic, layout_id, projector, rendered in structured_views(owner):
        expected = rendered.encode("utf-8")
        if (owner / view).read_bytes().replace(b"\r\n", b"\n") != expected:
            raise ValueError(f"Generated structured view is stale: {view}")
        folder = owner / "agent-system/knowledge" / topic
        records = {}
        for path in sorted(folder.rglob("*.json")):
            record = json.loads(path.read_bytes())
            identity = record["id"]
            if identity in records or record["owner"] != OWNER or record["topic"] != topic:
                raise ValueError(f"Invalid structured view member: {path}")
            records[identity] = record
        layout = records[layout_id]
        ordered = [layout_id, *(relation["target"] for relation in layout["relations"] if relation["type"] == "contains")]
        if len(ordered) != len(records) or set(ordered) != records.keys():
            raise ValueError(f"Structured view member order differs from its record directory: {view}")
        provenance = Path(layout["provenance"]["manifest"])
        result[view.as_posix()] = {
            "layout_id": layout_id,
            "sha256_utf8_lf": text_hash(expected),
            "source_fingerprints": {path.as_posix(): text_hash((owner / path).read_bytes()) for path in (provenance, projector, EDITOR)},
            "ordered_records_sha256": value_hash([(identity, value_hash(records[identity])) for identity in ordered]),
            "ordered_count": len(ordered),
        }
    return {"format": FORMAT, "owner": OWNER, "views": result}


def check_proof(owner: Path) -> None:
    if json.loads((owner / PROOF).read_bytes()) != build_proof(owner):
        raise ValueError("App structured view proof is stale. Verify the views and regenerate their proof.")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--check", action="store_true")
    mode.add_argument("--write", action="store_true")
    args = parser.parse_args()
    owner = Path(__file__).resolve().parents[2]
    try:
        if args.write:
            proof = build_proof(owner)
            (owner / PROOF).write_bytes((json.dumps(proof, ensure_ascii=False, indent=2) + "\n").encode("utf-8"))
        else:
            check_proof(owner)
    except (OSError, ValueError, KeyError) as error:
        print(str(error))
        return 1
    print("App structured views and their ordered record proof agree.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())