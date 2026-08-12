#!/usr/bin/env python3
"""Validate GeonicalSystem NextGIS Mobile documentation and module packs."""

from __future__ import annotations

import argparse
import configparser
import re
import sys
from collections.abc import Iterable, Mapping
from pathlib import Path, PurePosixPath
from typing import Any
from urllib.parse import unquote

try:
    import yaml
except ImportError:  # pragma: no cover - exercised by a user without dependencies
    print(
        "PyYAML is required. Install docs/requirements.txt with the Python "
        "interpreter used for this command.",
        file=sys.stderr,
    )
    raise SystemExit(2)


REGISTRY_FILES = (
    "repositories.yaml",
    "modules.yaml",
    "dependencies.yaml",
    "ecosystem.yaml",
    "invariants.yaml",
    "change-impact.yaml",
    "config-keys.yaml",
    "upstream-overlaps.yaml",
    "smoke-tests.yaml",
)
CENTRAL_FRONTMATTER = {"title", "type", "last_verified", "related_code"}
MODULE_FRONTMATTER = {"title", "module_id", "last_verified"}
MODULE_MANIFEST_FIELDS = {
    "schema_version",
    "module_id",
    "repository_id",
    "maturity",
    "last_verified",
    "entrypoints",
    "key_components",
    "public_contracts",
    "settings_refs",
    "storage_contracts",
    "invariant_refs",
    "global_docs",
    "smoke_tests",
    "upstream_hotspots",
}
LINK_RE = re.compile(r"!?(?:\[[^\]]*\])\(([^)]+)\)")


class Validation:
    def __init__(self) -> None:
        self.errors: list[str] = []
        self.infos: list[str] = []

    def error(self, location: str, message: str) -> None:
        self.errors.append(f"{location}: {message}")

    def info(self, message: str) -> None:
        self.infos.append(message)

    def finish(self) -> int:
        for item in self.infos:
            print(f"INFO: {item}")
        if self.errors:
            print(f"FAILED: {len(self.errors)} documentation error(s)")
            for item in self.errors:
                print(f"  - {item}")
            return 1
        print("OK: documentation and module packs are valid")
        return 0


def as_mapping(value: Any) -> Mapping[str, Any]:
    return value if isinstance(value, Mapping) else {}


def as_list(value: Any) -> list[Any]:
    return value if isinstance(value, list) else []


def load_yaml(path: Path, validation: Validation) -> Any:
    if not path.is_file():
        validation.error(path.as_posix(), "file is missing")
        return {}
    try:
        return yaml.safe_load(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, yaml.YAMLError) as exc:
        validation.error(path.as_posix(), f"invalid YAML: {exc}")
        return {}


def require_mapping(
    value: Any, location: str, validation: Validation
) -> Mapping[str, Any]:
    if not isinstance(value, Mapping):
        validation.error(location, "must be a mapping")
        return {}
    return value


def require_fields(
    value: Mapping[str, Any], fields: set[str], location: str, validation: Validation
) -> None:
    missing = fields - set(value)
    if missing:
        validation.error(location, f"missing keys: {sorted(missing)}")


def portable_path(
    raw: Any, location: str, validation: Validation, *, allow_dot: bool = False
) -> str | None:
    if not isinstance(raw, str) or not raw.strip():
        validation.error(location, "path must be a non-empty string")
        return None
    value = raw.strip()
    if value == "." and allow_dot:
        return value
    if "\\" in value or re.match(r"^[A-Za-z]:", value) or value.startswith("/"):
        validation.error(location, f"path is not portable: {value!r}")
        return None
    parts = PurePosixPath(value).parts
    if not parts or any(part in {"", ".", ".."} for part in parts):
        validation.error(location, f"unsafe relative path: {value!r}")
        return None
    return value


def workspace_target(
    workspace: Path,
    raw: Any,
    location: str,
    validation: Validation,
    *,
    allow_dot: bool = False,
    kind: str = "any",
) -> Path | None:
    value = portable_path(raw, location, validation, allow_dot=allow_dot)
    if value is None:
        return None
    target = (workspace / value).resolve()
    try:
        target.relative_to(workspace.resolve())
    except ValueError:
        validation.error(location, f"path escapes workspace: {value!r}")
        return None
    if kind == "file" and not target.is_file():
        validation.error(location, f"file does not exist: {value!r}")
    elif kind == "dir" and not target.is_dir():
        validation.error(location, f"directory does not exist: {value!r}")
    elif kind == "any" and not target.exists():
        validation.error(location, f"path does not exist: {value!r}")
    return target


def read_frontmatter(
    path: Path, required: set[str], validation: Validation
) -> Mapping[str, Any]:
    location = path.as_posix()
    try:
        text = path.read_text(encoding="utf-8")
    except (OSError, UnicodeError) as exc:
        validation.error(location, f"cannot read UTF-8: {exc}")
        return {}
    lines = text.splitlines()
    if not lines or lines[0].strip() != "---":
        validation.error(location, "missing YAML frontmatter")
        return {}
    try:
        end = next(index for index, line in enumerate(lines[1:], 1) if line.strip() == "---")
    except StopIteration:
        validation.error(location, "unterminated YAML frontmatter")
        return {}
    try:
        data = yaml.safe_load("\n".join(lines[1:end]))
    except yaml.YAMLError as exc:
        validation.error(location, f"invalid frontmatter: {exc}")
        return {}
    mapping = require_mapping(data, location, validation)
    require_fields(mapping, required, location, validation)
    if not mapping.get("last_verified"):
        validation.error(location, "last_verified must be non-empty")
    return mapping


def validate_markdown_links(path: Path, workspace: Path, validation: Validation) -> None:
    try:
        text = path.read_text(encoding="utf-8")
    except (OSError, UnicodeError):
        return
    for raw_target in LINK_RE.findall(text):
        target = raw_target.strip()
        if target.startswith("<") and ">" in target:
            target = target[1 : target.index(">")]
        else:
            target = target.split(maxsplit=1)[0]
        target = unquote(target).split("#", 1)[0].split("?", 1)[0]
        if not target or target.startswith(("http://", "https://", "mailto:", "#")):
            continue
        resolved = (path.parent / target).resolve()
        try:
            resolved.relative_to(workspace.resolve())
        except ValueError:
            validation.error(path.as_posix(), f"link escapes workspace: {target!r}")
            continue
        if not resolved.exists():
            validation.error(path.as_posix(), f"broken local link: {target!r}")


def validate_central_markdown(
    docs_root: Path, workspace: Path, validation: Validation
) -> None:
    for path in sorted(docs_root.rglob("*.md")):
        if "templates" in path.relative_to(docs_root).parts:
            continue
        frontmatter = read_frontmatter(path, CENTRAL_FRONTMATTER, validation)
        for index, related in enumerate(as_list(frontmatter.get("related_code"))):
            workspace_target(
                workspace,
                related,
                f"{path.as_posix()}:related_code[{index}]",
                validation,
                allow_dot=True,
            )
        validate_markdown_links(path, workspace, validation)


def validate_repositories(
    data: Any, workspace: Path, validation: Validation
) -> tuple[Mapping[str, Any], set[str]]:
    root = require_mapping(data, "registry/repositories.yaml", validation)
    if root.get("schema_version") != 1:
        validation.error("registry/repositories.yaml", "schema_version must be 1")
    repos = require_mapping(root.get("repositories"), "repositories", validation)
    module_refs: list[tuple[str, str]] = []
    for repo_id, raw in repos.items():
        location = f"repositories.{repo_id}"
        repo = require_mapping(raw, location, validation)
        require_fields(
            repo,
            {"title", "path", "kind", "expected_branch", "origin", "upstream", "docs_policy", "module_ids"},
            location,
            validation,
        )
        workspace_target(
            workspace,
            repo.get("path"),
            f"{location}.path",
            validation,
            allow_dot=True,
            kind="dir",
        )
        if repo.get("kind") not in {"root", "submodule"}:
            validation.error(location, "kind must be root or submodule")
        if repo.get("docs_policy") not in {"required", "upstream"}:
            validation.error(location, "docs_policy must be required or upstream")
        for module_id in as_list(repo.get("module_ids")):
            if isinstance(module_id, str):
                module_refs.append((repo_id, module_id))
            else:
                validation.error(location, "module_ids must contain strings")

    gitmodules_path = workspace / ".gitmodules"
    if gitmodules_path.is_file():
        parser = configparser.ConfigParser()
        parser.read(gitmodules_path, encoding="utf-8")
        by_path: dict[str, str] = {}
        for section in parser.sections():
            if section.startswith("submodule ") and parser.has_option(section, "path"):
                by_path[parser.get(section, "path")] = parser.get(section, "url", fallback="")
        registry_submodules = {
            str(repo.get("path")): str(repo.get("origin"))
            for repo in repos.values()
            if isinstance(repo, Mapping) and repo.get("kind") == "submodule"
        }
        if set(by_path) != set(registry_submodules):
            validation.error(
                "registry/repositories.yaml",
                f"submodule paths differ from .gitmodules: registry={sorted(registry_submodules)}, .gitmodules={sorted(by_path)}",
            )
        for path_value, url in registry_submodules.items():
            if by_path.get(path_value) and by_path[path_value] != url:
                validation.error(
                    f"repositories.{path_value}",
                    "origin URL differs from .gitmodules",
                )
    return repos, {module_id for _, module_id in module_refs}


def validate_modules(
    data: Any,
    repos: Mapping[str, Any],
    workspace: Path,
    validation: Validation,
) -> Mapping[str, Any]:
    root = require_mapping(data, "registry/modules.yaml", validation)
    if root.get("schema_version") != 1:
        validation.error("registry/modules.yaml", "schema_version must be 1")
    modules = require_mapping(root.get("modules"), "modules", validation)
    settings_text = (workspace / "settings.gradle").read_text(encoding="utf-8")
    for module_id, raw in modules.items():
        location = f"modules.{module_id}"
        module = require_mapping(raw, location, validation)
        require_fields(
            module,
            {"title", "repository", "path", "pack_root", "gradle_path", "namespace", "role", "docs_policy", "entrypoints"},
            location,
            validation,
        )
        if module.get("repository") not in repos:
            validation.error(location, f"unknown repository {module.get('repository')!r}")
        workspace_target(workspace, module.get("path"), f"{location}.path", validation, kind="dir")
        workspace_target(workspace, module.get("pack_root"), f"{location}.pack_root", validation, kind="dir")
        gradle_path = module.get("gradle_path")
        if not isinstance(gradle_path, str) or gradle_path not in settings_text:
            validation.error(location, f"gradle_path {gradle_path!r} not found in settings.gradle")
        if module.get("docs_policy") not in {"required", "upstream"}:
            validation.error(location, "docs_policy must be required or upstream")
        entries = as_list(module.get("entrypoints"))
        if not entries:
            validation.error(location, "entrypoints must be non-empty")
        for index, entry in enumerate(entries):
            workspace_target(
                workspace,
                entry,
                f"{location}.entrypoints[{index}]",
                validation,
                kind="file",
            )
    return modules


def validate_dependencies(
    data: Any, modules: Mapping[str, Any], workspace: Path, validation: Validation
) -> set[str]:
    root = require_mapping(data, "registry/dependencies.yaml", validation)
    if root.get("schema_version") != 1:
        validation.error("registry/dependencies.yaml", "schema_version must be 1")
    seen: set[tuple[Any, Any, Any]] = set()
    for index, raw in enumerate(as_list(root.get("edges"))):
        location = f"dependencies.edges[{index}]"
        edge = require_mapping(raw, location, validation)
        require_fields(edge, {"from", "to", "type", "scope", "purpose", "contracts"}, location, validation)
        for field in ("from", "to"):
            if edge.get(field) not in modules:
                validation.error(location, f"unknown {field} module {edge.get(field)!r}")
        key = (edge.get("from"), edge.get("to"), edge.get("type"))
        if key in seen:
            validation.error(location, f"duplicate dependency edge {key}")
        seen.add(key)
        for item_index, path_value in enumerate(as_list(edge.get("contracts"))):
            workspace_target(workspace, path_value, f"{location}.contracts[{item_index}]", validation, kind="file")
    runtime = require_mapping(root.get("runtime_contracts"), "dependencies.runtime_contracts", validation)
    for contract_id, raw in runtime.items():
        location = f"runtime_contracts.{contract_id}"
        contract = require_mapping(raw, location, validation)
        require_fields(contract, {"owner", "defined_in", "implementers", "callers"}, location, validation)
        if contract.get("owner") not in modules:
            validation.error(location, f"unknown owner {contract.get('owner')!r}")
        workspace_target(workspace, contract.get("defined_in"), f"{location}.defined_in", validation, kind="file")
        for field in ("implementers", "callers"):
            for module_id in as_list(contract.get(field)):
                if module_id not in modules:
                    validation.error(location, f"unknown {field} module {module_id!r}")
    return set(runtime)


def validate_smoke_tests(data: Any, modules: Mapping[str, Any], validation: Validation) -> set[str]:
    root = require_mapping(data, "registry/smoke-tests.yaml", validation)
    if root.get("schema_version") != 1:
        validation.error("registry/smoke-tests.yaml", "schema_version must be 1")
    tests = require_mapping(root.get("tests"), "smoke-tests.tests", validation)
    for test_id, raw in tests.items():
        location = f"smoke-tests.{test_id}"
        test = require_mapping(raw, location, validation)
        require_fields(test, {"type", "scope", "expected"}, location, validation)
        if test.get("type") not in {"automatic", "device"}:
            validation.error(location, "type must be automatic or device")
        if test.get("type") == "automatic" and not isinstance(test.get("command"), str):
            validation.error(location, "automatic test requires command")
        if test.get("type") == "device" and not as_list(test.get("steps")):
            validation.error(location, "device test requires steps")
        scopes = test.get("scope") if isinstance(test.get("scope"), list) else [test.get("scope")]
        for scope in scopes:
            if scope not in modules and scope not in {"docs", "root"}:
                validation.error(location, f"unknown scope {scope!r}")
    return set(tests)


def validate_ecosystem(
    data: Any,
    repos: Mapping[str, Any],
    modules: Mapping[str, Any],
    smoke_ids: set[str],
    workspace: Path,
    validation: Validation,
) -> set[str]:
    root = require_mapping(data, "registry/ecosystem.yaml", validation)
    if root.get("schema_version") != 1:
        validation.error("registry/ecosystem.yaml", "schema_version must be 1")
    systems = require_mapping(root.get("systems"), "ecosystem.systems", validation)
    if not systems:
        validation.error("ecosystem.systems", "must be non-empty")

    external_roots: dict[str, Path] = {}
    for system_id, raw in systems.items():
        location = f"ecosystem.systems.{system_id}"
        item = require_mapping(raw, location, validation)
        require_fields(
            item,
            {
                "title",
                "kind",
                "location",
                "origin",
                "docs_entry",
                "docs_url",
                "source_of_truth",
            },
            location,
            validation,
        )
        kind = item.get("kind")
        if kind not in {"workspace", "external_workspace"}:
            validation.error(location, "kind must be workspace or external_workspace")
        for field in ("title", "location", "origin", "docs_entry", "docs_url", "source_of_truth"):
            if not isinstance(item.get(field), str) or not item.get(field, "").strip():
                validation.error(location, f"{field} must be a non-empty string")
        if isinstance(item.get("origin"), str) and not item["origin"].startswith("https://"):
            validation.error(location, "origin must be an HTTPS URL")
        if isinstance(item.get("docs_url"), str) and not item["docs_url"].startswith("https://"):
            validation.error(location, "docs_url must be an HTTPS URL")

        if kind == "workspace":
            repository_id = item.get("repository")
            if repository_id not in repos:
                validation.error(
                    location, f"unknown local repository {repository_id!r}"
                )
            local_root = workspace_target(
                workspace,
                item.get("location"),
                f"{location}.location",
                validation,
                allow_dot=True,
                kind="dir",
            )
            docs_entry = portable_path(
                item.get("docs_entry"), f"{location}.docs_entry", validation
            )
            if local_root is not None and docs_entry is not None:
                docs_path = local_root / docs_entry
                if not docs_path.is_file():
                    validation.error(location, f"docs entry does not exist: {docs_entry!r}")
        else:
            docs_entry = portable_path(
                item.get("docs_entry"), f"{location}.docs_entry", validation
            )
            current_path = item.get("current_machine_path")
            if current_path is not None and (
                not isinstance(current_path, str) or not current_path.strip()
            ):
                validation.error(location, "current_machine_path must be a non-empty string")
            elif isinstance(current_path, str):
                candidate = Path(current_path)
                if candidate.is_dir():
                    external_roots[str(system_id)] = candidate
                    if docs_entry is not None and not (candidate / docs_entry).is_file():
                        validation.error(
                            location,
                            f"available external docs entry does not exist: {docs_entry!r}",
                        )
                else:
                    validation.info(
                        f"{system_id}: external workspace is unavailable at "
                        f"{current_path!r}; docs_url remains the portable entry"
                    )

    seen_ids: set[str] = set()

    def validate_common_record(
        item: Mapping[str, Any], location: str, *, with_endpoints: bool
    ) -> None:
        record_id = item.get("id")
        if not isinstance(record_id, str) or not record_id:
            validation.error(location, "id must be a non-empty string")
        elif record_id in seen_ids:
            validation.error(location, f"duplicate ecosystem id {record_id!r}")
        else:
            seen_ids.add(record_id)
        if with_endpoints:
            for field in ("from", "to"):
                if item.get(field) not in systems:
                    validation.error(location, f"unknown {field} system {item.get(field)!r}")
        else:
            members = as_list(item.get("systems"))
            if len(members) < 2:
                validation.error(location, "systems must contain at least two entries")
            for system_id in members:
                if system_id not in systems:
                    validation.error(location, f"unknown system {system_id!r}")
        for owner in as_list(item.get("owners")):
            if owner not in modules:
                validation.error(location, f"unknown local owner module {owner!r}")
        for index, path_value in enumerate(as_list(item.get("local_docs"))):
            workspace_target(
                workspace,
                path_value,
                f"{location}.local_docs[{index}]",
                validation,
                kind="file",
            )
        for smoke in as_list(item.get("verify")):
            if smoke not in smoke_ids:
                validation.error(location, f"unknown smoke test {smoke!r}")

    for index, raw in enumerate(as_list(root.get("contracts"))):
        location = f"ecosystem.contracts[{index}]"
        item = require_mapping(raw, location, validation)
        require_fields(
            item,
            {
                "id",
                "from",
                "to",
                "type",
                "transport",
                "statement",
                "owners",
                "local_docs",
                "external_docs",
                "verify",
            },
            location,
            validation,
        )
        validate_common_record(item, location, with_endpoints=True)
        for ref_index, raw_ref in enumerate(as_list(item.get("external_docs"))):
            ref_location = f"{location}.external_docs[{ref_index}]"
            ref = require_mapping(raw_ref, ref_location, validation)
            require_fields(ref, {"system", "path"}, ref_location, validation)
            system_id = ref.get("system")
            if system_id not in systems:
                validation.error(ref_location, f"unknown system {system_id!r}")
                continue
            path_value = portable_path(ref.get("path"), f"{ref_location}.path", validation)
            external_root = external_roots.get(str(system_id))
            if external_root is not None and path_value is not None:
                if not (external_root / path_value).is_file():
                    validation.error(
                        ref_location,
                        f"available external document does not exist: {path_value!r}",
                    )

    for index, raw in enumerate(as_list(root.get("boundaries"))):
        location = f"ecosystem.boundaries[{index}]"
        item = require_mapping(raw, location, validation)
        require_fields(
            item,
            {"id", "systems", "statement", "owners", "local_docs", "verify"},
            location,
            validation,
        )
        validate_common_record(item, location, with_endpoints=False)

    local_systems = [
        system_id
        for system_id, raw in systems.items()
        if isinstance(raw, Mapping) and raw.get("kind") == "workspace"
    ]
    if len(local_systems) != 1:
        validation.error(
            "ecosystem.systems",
            f"exactly one local workspace is required, found {local_systems}",
        )
    return seen_ids


def collect_config_ids(data: Any, modules: Mapping[str, Any], repos: Mapping[str, Any], workspace: Path, validation: Validation) -> set[str]:
    root = require_mapping(data, "registry/config-keys.yaml", validation)
    if root.get("schema_version") != 1:
        validation.error("registry/config-keys.yaml", "schema_version must be 1")
    result: set[str] = set()
    for section, raw_section in root.items():
        if section == "schema_version":
            continue
        values = require_mapping(raw_section, f"config-keys.{section}", validation)
        for key, raw in values.items():
            result.add(str(key))
            location = f"config-keys.{section}.{key}"
            item = require_mapping(raw, location, validation)
            owner = item.get("owner")
            if owner not in modules and owner not in repos:
                validation.error(location, f"unknown owner {owner!r}")
            if "source" in item:
                source = portable_path(item.get("source"), f"{location}.source", validation)
                if source is not None and not item.get("optional", False):
                    workspace_target(workspace, source, f"{location}.source", validation)
            forbidden = {"value", "secret", "password", "token"} & set(item)
            if forbidden:
                validation.error(location, f"secret/value fields are forbidden: {sorted(forbidden)}")
    return result


def validate_invariants(
    data: Any,
    modules: Mapping[str, Any],
    smoke_ids: set[str],
    workspace: Path,
    validation: Validation,
) -> set[str]:
    root = require_mapping(data, "registry/invariants.yaml", validation)
    if root.get("schema_version") != 1:
        validation.error("registry/invariants.yaml", "schema_version must be 1")
    invariants = require_mapping(root.get("invariants"), "invariants", validation)
    for invariant_id, raw in invariants.items():
        location = f"invariants.{invariant_id}"
        item = require_mapping(raw, location, validation)
        require_fields(item, {"title", "owners", "statement", "related_code", "verify_smoke", "must_update_docs"}, location, validation)
        for owner in as_list(item.get("owners")):
            if owner not in modules:
                validation.error(location, f"unknown owner {owner!r}")
        for index, path_value in enumerate(as_list(item.get("related_code"))):
            workspace_target(workspace, path_value, f"{location}.related_code[{index}]", validation)
        for smoke in as_list(item.get("verify_smoke")):
            if smoke not in smoke_ids:
                validation.error(location, f"unknown smoke test {smoke!r}")
        for index, path_value in enumerate(as_list(item.get("must_update_docs"))):
            workspace_target(workspace, path_value, f"{location}.must_update_docs[{index}]", validation, kind="file")
    return set(invariants)


def validate_upstream_overlaps(
    data: Any,
    repos: Mapping[str, Any],
    invariant_ids: set[str],
    smoke_ids: set[str],
    workspace: Path,
    validation: Validation,
) -> set[str]:
    root = require_mapping(data, "registry/upstream-overlaps.yaml", validation)
    if root.get("schema_version") != 1:
        validation.error("registry/upstream-overlaps.yaml", "schema_version must be 1")
    hotspots = require_mapping(root.get("hotspots"), "upstream-overlaps.hotspots", validation)
    for hotspot_id, raw in hotspots.items():
        location = f"upstream-overlaps.{hotspot_id}"
        item = require_mapping(raw, location, validation)
        require_fields(item, {"repositories", "paths", "default_decision", "reason", "invariants", "verify"}, location, validation)
        for repo_id in as_list(item.get("repositories")):
            if repo_id not in repos:
                validation.error(location, f"unknown repository {repo_id!r}")
        if item.get("default_decision") not in {"upstream", "ours", "hybrid"}:
            validation.error(location, "default_decision must be upstream, ours or hybrid")
        for index, path_value in enumerate(as_list(item.get("paths"))):
            workspace_target(workspace, path_value, f"{location}.paths[{index}]", validation, kind="file")
        for invariant in as_list(item.get("invariants")):
            if invariant not in invariant_ids:
                validation.error(location, f"unknown invariant {invariant!r}")
        for smoke in as_list(item.get("verify")):
            if smoke not in smoke_ids:
                validation.error(location, f"unknown smoke test {smoke!r}")
    return set(hotspots)


def validate_change_impact(
    data: Any,
    modules: Mapping[str, Any],
    invariant_ids: set[str],
    smoke_ids: set[str],
    workspace: Path,
    validation: Validation,
) -> list[Mapping[str, Any]]:
    root = require_mapping(data, "registry/change-impact.yaml", validation)
    if root.get("schema_version") != 1:
        validation.error("registry/change-impact.yaml", "schema_version must be 1")
    triggers: list[Mapping[str, Any]] = []
    seen: set[str] = set()
    for index, raw in enumerate(as_list(root.get("triggers"))):
        location = f"change-impact.triggers[{index}]"
        item = require_mapping(raw, location, validation)
        require_fields(item, {"id", "paths", "owners", "read", "invariants", "must_update_docs", "verify", "strict_docs"}, location, validation)
        trigger_id = item.get("id")
        if not isinstance(trigger_id, str) or not trigger_id:
            validation.error(location, "id must be a non-empty string")
        elif trigger_id in seen:
            validation.error(location, f"duplicate id {trigger_id!r}")
        else:
            seen.add(trigger_id)
        for path_index, pattern in enumerate(as_list(item.get("paths"))):
            portable_path(pattern, f"{location}.paths[{path_index}]", validation)
        for owner in as_list(item.get("owners")):
            if owner not in modules:
                validation.error(location, f"unknown owner {owner!r}")
        for field in ("read", "must_update_docs"):
            for path_index, path_value in enumerate(as_list(item.get(field))):
                workspace_target(workspace, path_value, f"{location}.{field}[{path_index}]", validation, kind="file")
        for invariant in as_list(item.get("invariants")):
            if invariant not in invariant_ids:
                validation.error(location, f"unknown invariant {invariant!r}")
        for smoke in as_list(item.get("verify")):
            if smoke not in smoke_ids:
                validation.error(location, f"unknown smoke test {smoke!r}")
        if not isinstance(item.get("strict_docs"), bool):
            validation.error(location, "strict_docs must be boolean")
        triggers.append(item)
    return triggers


def contains_todo(value: Any) -> bool:
    if isinstance(value, str):
        return "TODO" in value.upper()
    if isinstance(value, Mapping):
        return any(contains_todo(item) for item in value.values())
    if isinstance(value, list):
        return any(contains_todo(item) for item in value)
    return False


def validate_manifest(
    manifest_path: Path,
    readme_path: Path,
    expected_module: str,
    expected_repo: str,
    modules: Mapping[str, Any],
    config_ids: set[str],
    invariant_ids: set[str],
    smoke_ids: set[str],
    hotspot_ids: set[str],
    workspace: Path,
    validation: Validation,
) -> None:
    location = manifest_path.as_posix()
    manifest = require_mapping(load_yaml(manifest_path, validation), location, validation)
    require_fields(manifest, MODULE_MANIFEST_FIELDS, location, validation)
    if manifest.get("schema_version") != 1:
        validation.error(location, "schema_version must be 1")
    if manifest.get("module_id") != expected_module:
        validation.error(location, f"module_id must be {expected_module!r}")
    if manifest.get("repository_id") != expected_repo:
        validation.error(location, f"repository_id must be {expected_repo!r}")
    if manifest.get("maturity") not in {"draft", "maintained"}:
        validation.error(location, "maturity must be draft or maintained")
    if manifest.get("maturity") == "maintained" and contains_todo(manifest):
        validation.error(location, "maintained manifest must not contain TODO")
    if not manifest.get("last_verified"):
        validation.error(location, "last_verified must be non-empty")

    entries = as_list(manifest.get("entrypoints"))
    if not entries:
        validation.error(location, "entrypoints must be non-empty")
    for index, path_value in enumerate(entries):
        workspace_target(workspace, path_value, f"{location}.entrypoints[{index}]", validation, kind="file")
    components = as_list(manifest.get("key_components"))
    if not components:
        validation.error(location, "key_components must be non-empty")
    for index, raw in enumerate(components):
        item_location = f"{location}.key_components[{index}]"
        item = require_mapping(raw, item_location, validation)
        require_fields(item, {"path", "responsibility"}, item_location, validation)
        workspace_target(workspace, item.get("path"), f"{item_location}.path", validation)
    for index, raw in enumerate(as_list(manifest.get("public_contracts"))):
        item_location = f"{location}.public_contracts[{index}]"
        item = require_mapping(raw, item_location, validation)
        require_fields(item, {"id", "symbol", "path", "consumers"}, item_location, validation)
        workspace_target(workspace, item.get("path"), f"{item_location}.path", validation, kind="file")
        for consumer in as_list(item.get("consumers")):
            if consumer not in modules:
                validation.error(item_location, f"unknown consumer {consumer!r}")
    for setting in as_list(manifest.get("settings_refs")):
        if setting not in config_ids:
            validation.error(location, f"unknown setting/config ref {setting!r}")
    for index, raw in enumerate(as_list(manifest.get("storage_contracts"))):
        item_location = f"{location}.storage_contracts[{index}]"
        item = require_mapping(raw, item_location, validation)
        require_fields(item, {"id", "path"}, item_location, validation)
        workspace_target(workspace, item.get("path"), f"{item_location}.path", validation)
    for invariant in as_list(manifest.get("invariant_refs")):
        if invariant not in invariant_ids:
            validation.error(location, f"unknown invariant ref {invariant!r}")
    for index, path_value in enumerate(as_list(manifest.get("global_docs"))):
        workspace_target(workspace, path_value, f"{location}.global_docs[{index}]", validation, kind="file")
    for smoke in as_list(manifest.get("smoke_tests")):
        if smoke not in smoke_ids:
            validation.error(location, f"unknown smoke test {smoke!r}")
    for hotspot in as_list(manifest.get("upstream_hotspots")):
        if hotspot not in hotspot_ids:
            validation.error(location, f"unknown upstream hotspot {hotspot!r}")

    readme_frontmatter = read_frontmatter(readme_path, MODULE_FRONTMATTER, validation)
    if readme_frontmatter.get("module_id") != expected_module:
        validation.error(readme_path.as_posix(), f"module_id must be {expected_module!r}")
    if manifest.get("maturity") == "maintained":
        try:
            if "TODO" in readme_path.read_text(encoding="utf-8").upper():
                validation.error(readme_path.as_posix(), "maintained README must not contain TODO")
        except OSError:
            pass
    validate_markdown_links(readme_path, workspace, validation)


def validate_module_packs(
    modules: Mapping[str, Any],
    config_ids: set[str],
    invariant_ids: set[str],
    smoke_ids: set[str],
    hotspot_ids: set[str],
    workspace: Path,
    validation: Validation,
    target_module: str | None,
    require_pack: bool,
) -> None:
    if target_module and target_module not in modules:
        validation.error("arguments", f"unknown module {target_module!r}")
        return
    selected = (
        {target_module: modules[target_module]}
        if target_module
        else modules
    )
    for module_id, raw in selected.items():
        module = as_mapping(raw)
        pack_root_raw = module.get("pack_root")
        pack_root_value = portable_path(pack_root_raw, f"modules.{module_id}.pack_root", validation)
        if pack_root_value is None:
            continue
        pack_root = workspace / pack_root_value
        agents = pack_root / "AGENTS.md"
        readme = pack_root / "docs" / "README.md"
        manifest = pack_root / "docs" / "manifest.yaml"
        paths = (agents, readme, manifest)
        existing = [path.is_file() for path in paths]
        policy = module.get("docs_policy")
        if not any(existing):
            if policy == "required" or require_pack:
                validation.error(f"module:{module_id}", "required documentation pack is missing")
            else:
                validation.info(f"{module_id}: upstream docs policy, local pack is absent")
            continue
        if not all(existing):
            missing = [path.relative_to(workspace).as_posix() for path, present in zip(paths, existing) if not present]
            validation.error(f"module:{module_id}", f"partial documentation pack; missing {missing}")
            continue
        expected_repo = str(module.get("repository"))
        validate_manifest(
            manifest,
            readme,
            module_id,
            expected_repo,
            modules,
            config_ids,
            invariant_ids,
            smoke_ids,
            hotspot_ids,
            workspace,
            validation,
        )


def pattern_matches(path_value: str, pattern: str) -> bool:
    path = PurePosixPath(path_value)
    return path.match(pattern) or (
        pattern.endswith("/**") and path_value.startswith(pattern[:-3].rstrip("/") + "/")
    )


def report_changed_files(
    triggers: list[Mapping[str, Any]],
    changed_files: list[str],
    enforce: bool,
    validation: Validation,
) -> None:
    normalized: list[str] = []
    for index, raw in enumerate(changed_files):
        value = portable_path(raw.replace("\\", "/"), f"arguments.changed_file[{index}]", validation)
        if value:
            normalized.append(value)
    changed_set = set(normalized)
    for trigger in triggers:
        patterns = [str(value) for value in as_list(trigger.get("paths"))]
        if not any(pattern_matches(changed, pattern) for changed in normalized for pattern in patterns):
            continue
        trigger_id = trigger.get("id")
        required = [str(value) for value in as_list(trigger.get("must_update_docs"))]
        validation.info(
            f"change trigger {trigger_id}: read={as_list(trigger.get('read'))}, "
            f"update={required}, verify={as_list(trigger.get('verify'))}"
        )
        if enforce and trigger.get("strict_docs") and not changed_set.intersection(required):
            validation.error(
                f"change-impact:{trigger_id}",
                "strict trigger matched but none of must_update_docs were included in --changed-file",
            )


def main(argv: Iterable[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--docs-root",
        type=Path,
        default=Path(__file__).resolve().parents[1],
    )
    parser.add_argument(
        "--workspace-root",
        type=Path,
        default=Path(__file__).resolve().parents[2],
    )
    parser.add_argument("--module", help="Validate only this module pack")
    parser.add_argument(
        "--require-pack",
        action="store_true",
        help="Fail if the selected module pack is missing",
    )
    parser.add_argument(
        "--changed-file",
        action="append",
        default=[],
        help="Report change-impact requirements for a changed workspace path",
    )
    parser.add_argument(
        "--enforce-diff",
        action="store_true",
        help="Fail strict triggers if required docs are absent from --changed-file",
    )
    args = parser.parse_args(list(argv) if argv is not None else None)
    if args.require_pack and not args.module:
        parser.error("--require-pack requires --module")
    if args.enforce_diff and not args.changed_file:
        parser.error("--enforce-diff requires at least one --changed-file")

    docs_root = args.docs_root.resolve()
    workspace = args.workspace_root.resolve()
    validation = Validation()
    if not docs_root.is_dir():
        validation.error("arguments", f"docs root does not exist: {docs_root}")
        return validation.finish()
    if not workspace.is_dir():
        validation.error("arguments", f"workspace root does not exist: {workspace}")
        return validation.finish()

    registry_root = docs_root / "registry"
    loaded = {
        name: load_yaml(registry_root / name, validation) for name in REGISTRY_FILES
    }
    repos, declared_repo_modules = validate_repositories(
        loaded["repositories.yaml"], workspace, validation
    )
    modules = validate_modules(loaded["modules.yaml"], repos, workspace, validation)
    if declared_repo_modules != set(modules):
        validation.error(
            "registry/repositories.yaml",
            f"module_ids differ from modules.yaml: repositories={sorted(declared_repo_modules)}, modules={sorted(modules)}",
        )
    validate_dependencies(loaded["dependencies.yaml"], modules, workspace, validation)
    smoke_ids = validate_smoke_tests(loaded["smoke-tests.yaml"], modules, validation)
    validate_ecosystem(
        loaded["ecosystem.yaml"],
        repos,
        modules,
        smoke_ids,
        workspace,
        validation,
    )
    config_ids = collect_config_ids(
        loaded["config-keys.yaml"], modules, repos, workspace, validation
    )
    invariant_ids = validate_invariants(
        loaded["invariants.yaml"], modules, smoke_ids, workspace, validation
    )
    hotspot_ids = validate_upstream_overlaps(
        loaded["upstream-overlaps.yaml"],
        repos,
        invariant_ids,
        smoke_ids,
        workspace,
        validation,
    )
    triggers = validate_change_impact(
        loaded["change-impact.yaml"],
        modules,
        invariant_ids,
        smoke_ids,
        workspace,
        validation,
    )
    validate_central_markdown(docs_root, workspace, validation)
    validate_module_packs(
        modules,
        config_ids,
        invariant_ids,
        smoke_ids,
        hotspot_ids,
        workspace,
        validation,
        args.module,
        args.require_pack,
    )
    if args.changed_file:
        report_changed_files(triggers, args.changed_file, args.enforce_diff, validation)
    return validation.finish()


if __name__ == "__main__":
    raise SystemExit(main())
