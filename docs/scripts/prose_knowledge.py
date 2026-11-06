"""Project owner documents and literal scaffold inputs to their established addresses."""

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
APP_SCAFFOLD_TEMPLATES = {
    name: PurePosixPath("docs/templates/module-docs") / f"{name}.tmpl"
    for name in ("AGENTS.md", "README.md", "manifest.yaml")
}
LITERAL_VIEWS = {
    PurePosixPath("README.md"),
    PurePosixPath("WHATS_NEW.md"),
    PurePosixPath(".github/PULL_REQUEST_TEMPLATE.md"),
    *APP_SCAFFOLD_TEMPLATES.values(),
}
EASYPICKER_MODULE = "easypicker-module"
LIBRARY_READMES = {PurePosixPath("README.md"), PurePosixPath("docs/README.md")}
LIBRARY_TOPICS = "resource|contract|evidence|risk|note"
LIBRARY_DOCS = {
    EASYPICKER_MODULE: ("easypicker", LIBRARY_READMES, "resource|evidence|task"),
    "maplib": ("maplib", LIBRARY_READMES | {
        PurePosixPath("docs/sqlite-vector-source-bridge.md"),
        PurePosixPath("docs/sqlite-vector-tile-buffer.md"),
    }, LIBRARY_TOPICS),
    "maplibui": ("maplibui", LIBRARY_READMES, LIBRARY_TOPICS),
}


def markdown_lines(facets: dict, key: str, name: str) -> list[str]:
    lines = facets[key]["value"]
    if (not isinstance(lines, list) or not lines
            or any(not isinstance(line, str) or "\n" in line or "\r" in line for line in lines)):
        raise ValueError(f"Invalid documentation lines in {key}: {name}")
    return lines


def project_documents(
    owner: Path, *, module_id: str = "app", only: set[PurePosixPath] | None = None
) -> dict[PurePosixPath, str]:
    expected_views = None
    if module_id == "app":
        owner_id, section_id = OWNER, SECTION_ID
    elif module_id in LIBRARY_DOCS:
        namespace, expected_views, kinds = LIBRARY_DOCS[module_id]
        owner_id = f"{OWNER}/{namespace}"
        section_id = re.compile(rf"lisa:{namespace}:({kinds}):([a-z0-9][a-z0-9_.-]*)")
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
        if only is not None and view not in only:
            continue
        allowed = (
            view in expected_views if expected_views is not None
            else view in LITERAL_VIEWS or (
                view.suffix == ".md"
                and (view.parts[:1] == ("docs",) or view == PurePosixPath("app/docs/README.md"))
            )
        )
        if not allowed or ".." in view.parts or "\\" in str(view) or view in documents:
            raise ValueError(f"Invalid or duplicate documentation address: {source}")
        name = source["record"]
        record = read_record(name, source["id"], view, "resource")
        if view in LITERAL_VIEWS:
            if "frontmatter" in record["facets"]:
                raise ValueError(f"Unexpected separate frontmatter in literal view: {name}")
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
    if only is not None:
        if documents.keys() != only:
            missing = ", ".join(sorted(path.as_posix() for path in only - documents.keys()))
            raise ValueError(f"Missing documentation declarations: {missing}")
        return documents
    if expected_views is not None and documents.keys() != expected_views:
        raise ValueError(f"Incomplete documentation views for {module_id}")
    if {path.relative_to(owner / DIRECTORY).as_posix()
            for path in (owner / DIRECTORY).rglob("*.json")} != expected:
        raise ValueError("Documentation manifest and record membership differ")
    return documents


def check_generated(view: Path, text: str) -> None:
    if view.read_bytes().replace(b"\r\n", b"\n") != text.encode("utf-8"):
        kind = "template" if view.suffix == ".tmpl" else "Markdown"
        raise ValueError(f"Generated {kind} is stale: {view}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--check", action="store_true")
    mode.add_argument("--write", action="store_true")
    parser.add_argument("--module", choices=("app", *LIBRARY_DOCS), default="app")
    args = parser.parse_args()
    owner = Path(__file__).resolve().parents[2]
    module_readme = PurePosixPath("app/docs/README.md")
    if args.module in LIBRARY_DOCS:
        owner /= LIBRARY_DOCS[args.module][0]
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
