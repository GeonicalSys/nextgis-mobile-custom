#!/usr/bin/env python3
"""Create a non-destructive draft documentation pack for one registered module."""

from __future__ import annotations

import argparse
import json
import os
import sys
from datetime import date
from pathlib import Path
from string import Template

try:
    import yaml
except ImportError:
    print("PyYAML is required; install docs/requirements.txt", file=sys.stderr)
    raise SystemExit(2)


def fail(message: str) -> int:
    print(f"ERROR: {message}", file=sys.stderr)
    return 1


def load_mapping(path: Path) -> dict:
    try:
        value = yaml.safe_load(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, yaml.YAMLError) as exc:
        raise ValueError(f"cannot read {path}: {exc}") from exc
    if not isinstance(value, dict):
        raise ValueError(f"{path} must contain a mapping")
    return value


def portable_pack_root(workspace: Path, raw: object) -> Path:
    if not isinstance(raw, str) or not raw or "\\" in raw:
        raise ValueError("pack_root must be a portable relative path")
    candidate = (workspace / raw).resolve()
    try:
        candidate.relative_to(workspace.resolve())
    except ValueError as exc:
        raise ValueError("pack_root escapes workspace") from exc
    if not candidate.is_dir():
        raise ValueError(f"pack_root does not exist: {raw}")
    return candidate


def render(template_path: Path, values: dict[str, str]) -> str:
    return Template(template_path.read_text(encoding="utf-8")).substitute(values)


def write_exclusive(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("x", encoding="utf-8", newline="\n") as stream:
        stream.write(content)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("module_id")
    parser.add_argument(
        "--workspace-root",
        type=Path,
        default=Path(__file__).resolve().parents[2],
    )
    parser.add_argument(
        "--docs-root",
        type=Path,
        default=Path(__file__).resolve().parents[1],
    )
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    workspace = args.workspace_root.resolve()
    docs_root = args.docs_root.resolve()
    try:
        module_registry = load_mapping(docs_root / "registry" / "modules.yaml")
        repository_registry = load_mapping(
            docs_root / "registry" / "repositories.yaml"
        )
    except ValueError as exc:
        return fail(str(exc))
    modules = module_registry.get("modules")
    repositories = repository_registry.get("repositories")
    if not isinstance(modules, dict) or not isinstance(repositories, dict):
        return fail("modules/repositories registry is malformed")
    module = modules.get(args.module_id)
    if not isinstance(module, dict):
        return fail(f"module {args.module_id!r} is not registered")
    if module.get("docs_policy") != "required":
        return fail(
            f"module {args.module_id!r} has docs_policy={module.get('docs_policy')!r}; "
            "change it to 'required' before scaffolding"
        )
    repository_id = module.get("repository")
    if repository_id not in repositories:
        return fail(f"unknown repository {repository_id!r}")
    try:
        pack_root = portable_pack_root(workspace, module.get("pack_root"))
    except ValueError as exc:
        return fail(str(exc))

    targets = {
        "AGENTS.md": pack_root / "AGENTS.md",
        "README.md": pack_root / "docs" / "README.md",
        "manifest.yaml": pack_root / "docs" / "manifest.yaml",
    }
    missing = {name: path for name, path in targets.items() if not path.exists()}
    if not missing:
        return fail(f"documentation pack already exists for {args.module_id}")

    entrypoints = module.get("entrypoints")
    if not isinstance(entrypoints, list) or not entrypoints:
        return fail("registered module has no entrypoints")
    if not all(isinstance(value, str) and value for value in entrypoints):
        return fail("module entrypoints must be non-empty strings")

    try:
        central_ref = Path(os.path.relpath(docs_root, pack_root)).as_posix()
    except ValueError:
        # Windows cannot form a relative path across drive letters. This occurs
        # in standalone/temp workspaces; use the canonical root-repository URL.
        central_ref = (
            "https://github.com/GeonicalSys/nextgis-mobile-custom/"
            "tree/my-maplibre/docs"
        )
    entries_yaml = yaml.safe_dump(
        entrypoints, allow_unicode=True, sort_keys=False, default_flow_style=False
    ).rstrip()
    entries_yaml = "\n".join("  " + line for line in entries_yaml.splitlines())
    title = str(module.get("title") or args.module_id)
    values = {
        "module_id": args.module_id,
        "module_title": title,
        "module_title_yaml": json.dumps(title, ensure_ascii=False),
        "repository_id": str(repository_id),
        "today": date.today().isoformat(),
        "central_ref": central_ref,
        "entrypoints_yaml": entries_yaml,
        "first_entrypoint": entrypoints[0],
    }
    template_root = docs_root / "templates" / "module-docs"
    rendered = {
        "AGENTS.md": render(template_root / "AGENTS.md.tmpl", values),
        "README.md": render(template_root / "README.md.tmpl", values),
        "manifest.yaml": render(template_root / "manifest.yaml.tmpl", values),
    }

    print(f"Module: {args.module_id}")
    print(f"Pack root: {pack_root}")
    for name, path in missing.items():
        print(f"  create {path}")
    for name, path in targets.items():
        if name not in missing:
            print(f"  keep   {path}")
    if args.dry_run:
        print("DRY RUN: no files written")
        return 0

    created: list[Path] = []
    try:
        for name, path in missing.items():
            write_exclusive(path, rendered[name])
            created.append(path)
    except FileExistsError as exc:
        return fail(f"refusing to overwrite concurrently created file: {exc.filename}")
    except OSError as exc:
        return fail(f"cannot create documentation pack: {exc}")
    print(f"Created {len(created)} file(s); review TODO before maturity=maintained")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
