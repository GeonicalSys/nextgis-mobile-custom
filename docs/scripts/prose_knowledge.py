"""Project owner documents to their established Markdown and module YAML addresses."""

from __future__ import annotations

import argparse
import json
import re
import runpy
from pathlib import Path, PurePosixPath


OWNER = "android_projects/android_gisapp"
DIRECTORY = Path("agent-system/knowledge/documentation")
MANIFEST = Path("agent-system/provenance/documentation.json")
SECTION_ID = re.compile(r"lisa:mobile:(resource|evidence|task):([a-z0-9][a-z0-9_.-]*)")
EASYPICKER_MODULE = "easypicker-module"
EASYPICKER_PATH = Path("easypicker")
EASYPICKER_VIEWS = {PurePosixPath("README.md"), PurePosixPath("docs/README.md")}
EASYPICKER_SECTION_ID = re.compile(r"lisa:easypicker:(resource|evidence|task):([a-z0-9][a-z0-9_.-]*)")


def markdown_lines(facets: dict, key: str, name: str) -> list[str]:
    lines = facets[key]["value"]
    if (not isinstance(lines, list) or not lines
            or any(not isinstance(line, str) or "\n" in line or "\r" in line for line in lines)):
        raise ValueError(f"Invalid documentation lines in {key}: {name}")
    return lines


def project_documents(owner: Path, *, module_id: str = "app") -> dict[PurePosixPath, str]:
    if module_id == EASYPICKER_MODULE:
        owner_id, section_id = OWNER + "/" + EASYPICKER_PATH.as_posix(), EASYPICKER_SECTION_ID
    elif module_id == "app":
        owner_id, section_id = OWNER, SECTION_ID
    else:
        raise ValueError(f"Unknown module documentation projection: {module_id}")
    manifest = json.loads((owner / MANIFEST).read_bytes())
    if manifest["schema_version"] != 1 or manifest["owner"] != owner_id or not manifest["sources"]:
        raise ValueError("Invalid documentation provenance")
    documents = {}
    expected = set()
    identities = set()

    def read_record(name: str, identity: str, view: PurePosixPath, kind: str) -> dict:
        if (Path(name).name != name or "\\" in name or not name.endswith(".json")
                or name in expected or identity in identities):
            raise ValueError(f"Invalid or duplicate documentation record: {name}")
        expected.add(name)
        identities.add(identity)
        record = json.loads((owner / DIRECTORY / name).read_bytes())
        if (record["schema_version"] != 2 or record["id"] != identity
                or record["owner"] != owner_id or record["topic"] != "documentation"
                or record["kind"] != kind
                or record["provenance"] != {"manifest": MANIFEST.as_posix(), "scopes": [identity]}):
            raise ValueError(f"Invalid documentation record: {name}")
        if record["facets"]["view-path"]["value"] != view.as_posix():
            raise ValueError(f"Documentation view path differs: {name}")
        return record

    for source in manifest["sources"]:
        view = PurePosixPath(source["path"])
        allowed = (
            view in EASYPICKER_VIEWS if module_id == EASYPICKER_MODULE
            else view.parts[:1] == ("docs",) or view == PurePosixPath("app/docs/README.md")
        )
        if not allowed or view.suffix != ".md" or ".." in view.parts or "\\" in str(view) or view in documents:
            raise ValueError(f"Invalid or duplicate documentation address: {source}")
        name = source["record"]
        record = read_record(name, source["id"], view, "resource")
        if view == PurePosixPath("README.md"):
            if "frontmatter" in record["facets"]:
                raise ValueError(f"Unexpected frontmatter in the library README: {name}")
            lines = []
        else:
            frontmatter = markdown_lines(record["facets"], "frontmatter", name)
            lines = ["---", *frontmatter, "---"]
        lines.extend(markdown_lines(record["facets"], "body", name))
        # Ordered contains relations are the layout. Provenance spans describe the original only.
        for edge in record["relations"]:
            if edge["type"] != "contains":
                continue
            identity = edge["target"]
            match = section_id.fullmatch(identity)
            if match is None or edge["status"] != "source_backed":
                raise ValueError(f"Invalid documentation section: {identity}")
            kind, slug = match.groups()
            section_name = f"{slug}.json"
            section = read_record(section_name, identity, view, kind)
            if ("frontmatter" in section["facets"]
                    or any(edge["type"] == "contains" for edge in section["relations"])):
                raise ValueError(f"Nested documentation section: {identity}")
            lines.extend(markdown_lines(section["facets"], "body", section_name))
        documents[view] = "\n".join(lines)
    if module_id == EASYPICKER_MODULE and documents.keys() != EASYPICKER_VIEWS:
        raise ValueError("EasyPicker requires both documentation views")
    if {path.relative_to(owner / DIRECTORY).as_posix()
            for path in (owner / DIRECTORY).rglob("*.json")} != expected:
        raise ValueError("Documentation manifest and record membership differ")
    return documents


def check_generated(view: Path, text: str) -> None:
    if view.read_bytes().replace(b"\r\n", b"\n") != text.encode("utf-8"):
        raise ValueError(f"Generated Markdown is stale: {view}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--check", action="store_true")
    mode.add_argument("--write", action="store_true")
    parser.add_argument("--module", choices=("app", EASYPICKER_MODULE), default="app")
    args = parser.parse_args()
    owner = Path(__file__).resolve().parents[2]
    module_readme = PurePosixPath("app/docs/README.md")
    if args.module == EASYPICKER_MODULE:
        owner /= EASYPICKER_PATH
        module_readme = PurePosixPath("docs/README.md")
    try:
        documents = project_documents(owner, module_id=args.module)
        manifest_view = None
        if module_readme in documents:
            projector = runpy.run_path(str(Path(__file__).with_name("module_manifest_knowledge.py")))
            manifest = projector["project_manifest"](owner, module_id=args.module)
            if args.write:
                manifest_view = (
                    owner / projector["PROFILES"][args.module].view,
                    projector["render_manifest"](manifest, module_id=args.module),
                )
            else:
                projector["check_view"](owner, manifest, module_id=args.module)
        for view, text in documents.items():
            if args.write:
                (owner / view).write_bytes(text.encode("utf-8"))
            else:
                check_generated(owner / view, text)
        if manifest_view is not None:
            view, text = manifest_view
            view.write_bytes(text.encode("utf-8"))
    except (OSError, ValueError, KeyError, TypeError) as exc:
        parser.exit(1, f"Documentation projection failed: {exc}\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
