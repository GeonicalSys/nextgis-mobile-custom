#!/usr/bin/env python3
"""Read-only review tripwires for the bounded, source-backed UI catalogue.

A matching fingerprint proves only that reviewed source has not changed. It is
not execution, a Kotlin/Java type checker, or a proof of complete UI coverage.
There is deliberately no command to regenerate expectations from current code.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import xml.etree.ElementTree as ET
from pathlib import Path, PurePosixPath, PureWindowsPath

REPOSITORIES = {"root": ".", "maplib": "maplib", "maplibui": "maplibui"}
GROUPS = {"map", "location", "navigation", "project", "layers", "identify",
          "editing", "forms", "tracks", "sync", "offline"}
TEXT_FIELDS = ("title", "surface", "meaning", "value_origin", "units", "saved_state")
LIST_FIELDS = ("widgets", "updates", "actions", "conditions", "expectations", "unknowns")


def lexical_views(text: str) -> tuple[str, str]:
    """Preserve offsets; remove comments, and additionally mask literals for braces."""
    clean, code = list(text), list(text)
    i, size = 0, len(text)
    while i < size:
        if text.startswith("//", i):
            end = text.find("\n", i)
            end = size if end < 0 else end
            for j in range(i, end):
                clean[j] = code[j] = " "
            i = end
        elif text.startswith("/*", i):
            end, depth = i + 2, 1
            while end < size and depth:
                if text.startswith("/*", end):
                    depth += 1
                    end += 2
                elif text.startswith("*/", end):
                    depth -= 1
                    end += 2
                else:
                    end += 1
            if depth:
                raise ValueError("unterminated block comment")
            for j in range(i, end):
                if text[j] != "\n":
                    clean[j] = code[j] = " "
            i = end
        elif text.startswith('"""', i) or text[i] in ('"', "'"):
            delimiter = '"""' if text.startswith('"""', i) else text[i]
            end = i + len(delimiter)
            while end < size:
                if delimiter != '"""' and text[end] == "\\":
                    end += 2
                elif text.startswith(delimiter, end):
                    end += len(delimiter)
                    break
                else:
                    end += 1
            else:
                raise ValueError("unterminated literal")
            for j in range(i, min(end, size)):
                if text[j] != "\n":
                    code[j] = " "
            i = end
        else:
            i += 1
    return "".join(clean), "".join(code)


def extract_method(text: str, marker: str) -> str:
    text = text.replace("\r\n", "\n").replace("\r", "\n")
    clean, code = lexical_views(text)
    matches = [m for m in re.finditer(re.escape(marker), clean)
               if code[m.start():m.start() + 1].strip()]
    if len(matches) != 1:
        raise ValueError(f"method marker must occur once, found {len(matches)}")
    start = matches[0].start()
    opening = code.find("{", start + len(marker))
    header = code[start:opening]
    # A partial marker must not scan forward into a following method/lambda.
    if opening < 0 or code[start + len(marker):opening].strip() or ";" in header:
        raise ValueError("marker is not a block method declaration")
    depth = 0
    for char in header:
        if char == "(":
            depth += 1
        elif char == ")":
            depth -= 1
        elif char == "=" and depth == 0:
            raise ValueError("expression-bodied declarations require a separate reviewed extractor")
    if depth != 0 or len(re.findall(r"\bfun\b", header)) > 1:
        raise ValueError("marker is not one complete method header")
    if re.search(r"\b(?:return|throw|new|yield|case|if|while|for|switch)\b", header):
        raise ValueError("statement/call marker is not a method declaration")
    if not re.search(r"\bfun\s+(?:[\w<>?.]+\.)?\w+\s*\(|\b[\w<>\[\]?,]+\s+\w+\s*\(", header):
        raise ValueError("marker must include the method declaration, not a call")
    depth = 1
    for end in range(opening + 1, len(code)):
        if code[end] == "{":
            depth += 1
        elif code[end] == "}":
            depth -= 1
            if depth == 0:
                return text[start:end + 1]
    raise ValueError("unclosed method body")


def fingerprint(method: str) -> str:
    return hashlib.sha256(method.replace("\r\n", "\n").replace("\r", "\n").encode("utf-8")).hexdigest()


def fragment_in_code(method: str, fragment: str) -> bool:
    """Match reviewed fragments including literals, but never wholly inside one.

    A legitimate fragment may start with a format string and continue with
    .format(location). The code after that literal must actually be code.
    """
    clean, code = lexical_views(method)
    chars, flags = [], []
    for index, char in enumerate(clean):
        if char.isspace():
            if chars and chars[-1] != " ":
                chars.append(" ")
                flags.append(False)
        else:
            chars.append(char)
            flags.append(not code[index].isspace())
    normalized = "".join(chars)
    expected = " ".join(fragment.split())
    return any(any(flags[match.start():match.end()])
               for match in re.finditer(re.escape(expected), normalized))


def source_path(workspace: Path, repository: str, relative: str) -> Path:
    if repository not in REPOSITORIES:
        raise ValueError(f"unknown repository {repository!r}")
    if (not isinstance(relative, str) or not relative or "\\" in relative
            or ":" in relative or PureWindowsPath(relative).is_absolute()
            or PurePosixPath(relative).is_absolute()
            or any(part in (".", "..") for part in relative.split("/"))):
        raise ValueError("source path must be a confined repository-relative path")
    base = workspace / REPOSITORIES[repository]
    target = base / relative
    current = target
    while current != workspace.parent:
        if current.is_symlink() or (hasattr(current, "is_junction") and current.is_junction()):
            raise ValueError("reparse source path is not eligible")
        if current == workspace:
            break
        current = current.parent
    if not base.is_dir() or not target.is_file():
        raise ValueError("required source/repository is absent; source coverage is UNKNOWN")
    if not target.resolve().is_relative_to(base.resolve()):
        raise ValueError("source escaped its repository")
    if target.stat().st_size > 2 * 1024 * 1024:
        raise ValueError("source exceeds bounded checker budget")
    return target


def check_catalog(workspace: Path, registry: Path | None = None) -> tuple[list[str], str]:
    registry = registry or workspace / "docs/registry/ui-contracts.json"
    errors: list[str] = []
    try:
        if registry.stat().st_size > 4 * 1024 * 1024:
            raise ValueError("catalogue exceeds checker budget")
        data = json.loads(registry.read_text(encoding="utf-8-sig"))
        if (not isinstance(data, dict) or type(data.get("schema_version")) is not int
                or data["schema_version"] != 1):
            raise ValueError("unsupported UI catalogue schema")
        coverage = data.get("coverage", {})
        if (not isinstance(coverage, dict) or coverage.get("status") != "bounded_source_review"
                or coverage.get("runtime") != "not_run"
                or not isinstance(coverage.get("not_reviewed"), list)
                or not coverage["not_reviewed"]
                or not all(isinstance(x, str) and x.strip() for x in coverage["not_reviewed"])
                or not isinstance(coverage.get("reviewed_groups"), list)
                or not all(isinstance(x, str) for x in coverage["reviewed_groups"])):
            raise ValueError("coverage must explicitly retain bounded review, runtime not_run and gaps")
        sources, contracts = data.get("sources"), data.get("contracts")
        if not isinstance(sources, list) or not sources or not isinstance(contracts, list) or not contracts:
            raise ValueError("nonempty source evidence and contracts are required")
    except (OSError, UnicodeError, ValueError, TypeError, AttributeError) as exc:
        return [f"UI catalogue: {exc}"], "UI coverage UNKNOWN"
    source_ids: set[str] = set()
    widget_ids: set[str] = set()
    widget_keys: set[tuple[str, str, str]] = set()
    widgets = data.get("widget_evidence")
    if not isinstance(widgets, list):
        errors.append("UI widget_evidence must explicitly list reviewed XML declarations")
        widgets = []
    for item in widgets:
        try:
            if not isinstance(item, dict) or not re.fullmatch(r"[a-zA-Z_]\w*", str(item.get("id", ""))):
                raise ValueError("widget must have an actual resource ID")
            key = (item.get("repository"), item.get("path"), item["id"])
            path = source_path(workspace, key[0], key[1])
            if key in widget_keys:
                raise ValueError("duplicate widget evidence")
            widget_keys.add(key)
            document = ET.parse(path)
            ids = {node.get("{http://schemas.android.com/apk/res/android}id")
                   for node in document.iter()}
            if not {"@+id/" + key[2], "@id/" + key[2]} & ids:
                raise ValueError(f"resource ID {key[2]} is absent from its reviewed XML")
            if fingerprint(path.read_text(encoding="utf-8-sig")) != item.get("sha256"):
                raise ValueError("reviewed XML bindings changed; semantic review required")
            widget_ids.add(key[2])
        except (OSError, ValueError, TypeError, ET.ParseError) as exc:
            errors.append(f"UI widget: {exc}")
    cache: dict[Path, str] = {}
    for item in sources:
        label = item.get("id", "?") if isinstance(item, dict) else "?"
        try:
            if not isinstance(item, dict) or not isinstance(label, str) or not label or label in source_ids:
                raise ValueError("missing/duplicate source ID")
            source_ids.add(label)
            path = source_path(workspace, item.get("repository"), item.get("path"))
            marker = item.get("marker")
            if not isinstance(marker, str) or not marker.strip():
                raise ValueError("method marker is required")
            if path not in cache:
                cache[path] = path.read_text(encoding="utf-8-sig")
            method = extract_method(cache[path], marker)
            if not re.fullmatch(r"[0-9a-f]{64}", str(item.get("sha256", ""))):
                raise ValueError("reviewed method fingerprint is required")
            if fingerprint(method) != item["sha256"]:
                raise ValueError("reviewed method changed: inspect contract/consumers; do not auto-repin")
            required, forbidden = item.get("requires"), item.get("forbids", [])
            if (not isinstance(required, list) or not required or not isinstance(forbidden, list)
                    or not all(isinstance(x, str) and x.strip() for x in required + forbidden)):
                raise ValueError("nonempty semantic requires and string forbids are required")
            if any(not fragment_in_code(method, fragment) for fragment in required):
                raise ValueError("required producer/transform/action fragment is missing")
            if any(fragment_in_code(method, fragment) for fragment in forbidden):
                raise ValueError("forbidden producer/action fragment is present")
        except (OSError, UnicodeError, ValueError, TypeError, AttributeError) as exc:
            errors.append(f"UI source {label}: {exc}")
    ids, used, groups = set(), set(), set()
    for contract in contracts:
        label = contract.get("id", "?") if isinstance(contract, dict) else "?"
        if not isinstance(contract, dict):
            errors.append("UI contract must be an object")
            continue
        if not isinstance(label, str) or not re.fullmatch(r"UI-[A-Z0-9-]+", label) or label in ids:
            errors.append(f"UI contract {label}: missing/duplicate/invalid ID")
        ids.add(str(label))
        group = contract.get("group")
        if not isinstance(group, str) or group not in GROUPS:
            errors.append(f"UI contract {label}: unknown group")
        else:
            groups.add(group)
        if any(not isinstance(contract.get(key), str) or not contract[key].strip() for key in TEXT_FIELDS):
            errors.append(f"UI contract {label}: missing meaning/provenance/units/state")
        if any(not isinstance(contract.get(key), list) or
               not all(isinstance(x, str) and x.strip() for x in contract[key]) for key in LIST_FIELDS):
            errors.append(f"UI contract {label}: invalid scenario fields")
        contract_widgets = contract.get("widgets")
        if (isinstance(contract_widgets, list) and all(isinstance(x, str) for x in contract_widgets)
                and not set(contract_widgets) <= widget_ids):
            errors.append(f"UI contract {label}: widget lacks reviewed XML declaration")
        if not contract.get("expectations"):
            errors.append(f"UI contract {label}: independent expectations are required")
        variants = contract.get("variants")
        if (not isinstance(variants, list) or not variants
                or not all(isinstance(x, str) for x in variants)
                or not set(variants) <= {"lisa", "belka"}):
            errors.append(f"UI contract {label}: explicit supported variants are required")
        references = contract.get("source_ids")
        if not isinstance(references, list) or not references or not all(isinstance(x, str) for x in references):
            errors.append(f"UI contract {label}: source evidence is required")
        else:
            used.update(references)
            if not set(references) <= source_ids:
                errors.append(f"UI contract {label}: unknown source references")
    if source_ids - used:
        errors.append("UI catalogue has orphan source evidence")
    files = data.get("non_method_evidence", [])
    if not isinstance(files, list):
        errors.append("UI non_method_evidence must be an explicit list")
        files = []
    file_keys = set()
    for row in files:
        try:
            if not isinstance(row, dict) or not isinstance(row.get("purpose"), str) or not row["purpose"].strip():
                raise ValueError("non-method evidence needs its reviewed purpose")
            key = (row.get("repository"), row.get("path"))
            path = source_path(workspace, *key)
            if key in file_keys:
                raise ValueError("duplicate non-method file evidence")
            file_keys.add(key)
            if not isinstance(row.get("contract_ids"), list) or not row["contract_ids"] or not all(
                    isinstance(x, str) and x in ids for x in row["contract_ids"]):
                raise ValueError("non-method evidence must refer to reviewed contracts")
            if fingerprint(path.read_text(encoding="utf-8-sig")) != row.get("sha256"):
                raise ValueError("reviewed constants/property/resource file changed; semantic review required")
        except (OSError, UnicodeError, ValueError, TypeError) as exc:
            errors.append(f"UI non-method source: {exc}")
    if set(coverage.get("reviewed_groups", [])) != groups:
        errors.append("UI reviewed_groups must match actually registered contracts")
    return errors, (f"UI source review: {len(contracts)} contracts, {len(sources)} method tripwires, "
                    f"{len(files)} non-method file tripwires, {len(groups)} groups; "
                    f"runtime NOT_RUN; remaining coverage UNKNOWN")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--workspace-root", type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument("--registry", type=Path)
    args = parser.parse_args()
    errors, summary = check_catalog(args.workspace_root.resolve(), args.registry)
    print(summary)
    for error in errors:
        print(f"ERROR: {error}")
    return int(bool(errors))


if __name__ == "__main__":
    raise SystemExit(main())
