"""Project App-owned prose records to their established Markdown addresses."""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path, PurePosixPath


OWNER = "android_projects/android_gisapp"
DIRECTORY = Path("agent-system/knowledge/documentation")
MANIFEST = Path("agent-system/provenance/documentation.json")
SECTION_ID = re.compile(r"lisa:mobile:(resource|evidence|task):([a-z0-9][a-z0-9_.-]*)")


def markdown_lines(facets: dict, key: str, name: str) -> list[str]:
    lines = facets[key]["value"]
    if (not isinstance(lines, list) or not lines
            or any(not isinstance(line, str) or "\n" in line or "\r" in line for line in lines)):
        raise ValueError(f"Invalid documentation lines in {key}: {name}")
    return lines


def project_documents(owner: Path) -> dict[PurePosixPath, str]:
    manifest = json.loads((owner / MANIFEST).read_bytes())
    if manifest["schema_version"] != 1 or manifest["owner"] != OWNER or not manifest["sources"]:
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
                or record["owner"] != OWNER or record["topic"] != "documentation"
                or record["kind"] != kind
                or record["provenance"] != {"manifest": MANIFEST.as_posix(), "scopes": [identity]}):
            raise ValueError(f"Invalid documentation record: {name}")
        if record["facets"]["view-path"]["value"] != view.as_posix():
            raise ValueError(f"Documentation view path differs: {name}")
        return record

    for source in manifest["sources"]:
        view = PurePosixPath(source["path"])
        if (view.parts[:1] != ("docs",) or view.suffix != ".md"
                or ".." in view.parts or "\\" in str(view) or view in documents):
            raise ValueError(f"Invalid or duplicate documentation address: {source}")
        name = source["record"]
        record = read_record(name, source["id"], view, "resource")
        frontmatter = markdown_lines(record["facets"], "frontmatter", name)
        body = list(markdown_lines(record["facets"], "body", name))
        # Ordered contains relations are the layout. Provenance spans describe the original only.
        for edge in record["relations"]:
            if edge["type"] != "contains":
                continue
            identity = edge["target"]
            match = SECTION_ID.fullmatch(identity)
            if match is None or edge["status"] != "source_backed":
                raise ValueError(f"Invalid documentation section: {identity}")
            kind, slug = match.groups()
            section_name = f"{slug}.json"
            section = read_record(section_name, identity, view, kind)
            if ("frontmatter" in section["facets"]
                    or any(edge["type"] == "contains" for edge in section["relations"])):
                raise ValueError(f"Nested documentation section: {identity}")
            body.extend(markdown_lines(section["facets"], "body", section_name))
        documents[view] = "\n".join(["---", *frontmatter, "---", *body])
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
    args = parser.parse_args()
    owner = Path(__file__).resolve().parents[2]
    try:
        for view, text in project_documents(owner).items():
            if args.write:
                (owner / view).write_bytes(text.encode("utf-8"))
            else:
                check_generated(owner / view, text)
    except (OSError, ValueError, KeyError, TypeError) as exc:
        parser.exit(1, f"Documentation projection failed: {exc}\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
