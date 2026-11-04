"""Seal structured views after the ordinary owner renderers check their bytes."""

from __future__ import annotations

import argparse
from hashlib import sha256
import json
from pathlib import Path
import runpy

OWNER = "android_projects/android_gisapp"
PROOF = Path("agent-system/provenance/structured-views.json")
FORMAT = "lisa.agent-system.app-structured-views.v1"
LIBRARY_FORMAT = "lisa.agent-system.module-structured-views.v1"
EASYPICKER_MODULE = "easypicker-module"
SCRIPTS = Path("docs/scripts")
EDITOR = SCRIPTS / "structured_view_proof.py"
MODULE_PROJECTOR = SCRIPTS / "module_manifest_knowledge.py"
KEYED = ("repositories", "modules", "invariants", "upstream-overlaps", "smoke-tests",
         "config-keys", "dependencies", "ecosystem")


def text_hash(raw: bytes) -> str:
    return sha256(raw.replace(b"\r\n", b"\n")).hexdigest()


def value_hash(value) -> str:
    return sha256(json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode()).hexdigest()


def library_view_declared(owner: Path) -> bool:
    proof = owner / PROOF
    if proof.exists() or proof.is_symlink():
        return True
    projector = runpy.run_path(str(owner.parent / MODULE_PROJECTOR))
    view = owner / projector["PROFILES"][EASYPICKER_MODULE].view
    with view.open(encoding="utf-8") as stream:
        return stream.readline().rstrip("\n") == projector["generated_heading"](EASYPICKER_MODULE)


def structured_views(owner: Path, *, module_id: str = "app"):
    if module_id == EASYPICKER_MODULE:
        projector = runpy.run_path(str(owner.parent / MODULE_PROJECTOR))
        profile = projector["PROFILES"][module_id]
        manifest = projector["project_manifest"](owner, module_id=module_id)
        yield (profile.view, profile.topic, profile.layout_id, MODULE_PROJECTOR,
               projector["render_manifest"](manifest, module_id=module_id))
        return
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


def build_proof(owner: Path, *, module_id: str = "app") -> dict:
    is_app = module_id == "app"
    owner_id = OWNER if is_app else OWNER + "/easypicker"
    scripts_owner = owner if is_app else owner.parent
    result = {}
    for view, topic, layout_id, projector, rendered in structured_views(owner, module_id=module_id):
        expected = rendered.encode("utf-8")
        if (owner / view).read_bytes().replace(b"\r\n", b"\n") != expected:
            raise ValueError(f"Generated structured view is stale: {view}")
        folder = owner / "agent-system/knowledge" / topic
        records = {}
        for path in sorted(folder.rglob("*.json")):
            record = json.loads(path.read_bytes())
            identity = record["id"]
            if identity in records or record["owner"] != owner_id or record["topic"] != topic:
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
            "source_fingerprints": {
                path.as_posix(): text_hash((root / path).read_bytes())
                for path, root in ((provenance, owner), (projector, scripts_owner), (EDITOR, scripts_owner))
            },
            "ordered_records_sha256": value_hash([(identity, value_hash(records[identity])) for identity in ordered]),
            "ordered_count": len(ordered),
        }
    return {"format": FORMAT if is_app else LIBRARY_FORMAT, "owner": owner_id, "views": result}


def check_proof(owner: Path, *, module_id: str = "app") -> None:
    if json.loads((owner / PROOF).read_bytes()) != build_proof(owner, module_id=module_id):
        raise ValueError("Structured view proof is stale. Verify the views and regenerate their proof.")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--check", action="store_true")
    mode.add_argument("--write", action="store_true")
    parser.add_argument("--module", choices=("app", EASYPICKER_MODULE), default="app")
    args = parser.parse_args()
    owner = Path(__file__).resolve().parents[2]
    if args.module == EASYPICKER_MODULE:
        owner /= "easypicker"
    try:
        if args.write:
            proof = build_proof(owner, module_id=args.module)
            (owner / PROOF).write_bytes((json.dumps(proof, ensure_ascii=False, indent=2) + "\n").encode("utf-8"))
        else:
            check_proof(owner, module_id=args.module)
    except (OSError, ValueError, KeyError) as error:
        print(str(error))
        return 1
    print("Structured views and their ordered record proof agree.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
