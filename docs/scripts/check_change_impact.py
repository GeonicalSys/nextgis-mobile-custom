#!/usr/bin/env python3
"""Enforce documentation impact over the complete PR, including on push."""

from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path
from urllib.error import URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen


@dataclass(frozen=True)
class Comparison:
    label: str
    base: str | None
    head: str
    merge_base: bool = True


def open_pull_requests(repository: str, branch: str) -> list[dict]:
    """Read every open PR for this repository's branch, including stacked PRs."""
    api = os.environ.get("GITHUB_API_URL", "https://api.github.com").rstrip("/")
    token = os.environ["GITHUB_TOKEN"]
    pulls = []
    page = 1
    while True:
        query = urlencode({"state": "open", "head": f"{repository.split('/')[0]}:{branch}",
                           "per_page": 100, "page": page})
        request = Request(
            f"{api}/repos/{repository}/pulls?{query}",
            headers={"Authorization": f"Bearer {token}",
                     "Accept": "application/vnd.github+json",
                     "X-GitHub-Api-Version": "2022-11-28",
                     "User-Agent": "documentation-impact-check"},
        )
        try:
            with urlopen(request, timeout=20) as response:
                batch = json.load(response)
        except URLError as exc:
            # Do not print request headers, credentials or a response body.
            raise RuntimeError("Cannot read open PR bases from GitHub; impact check stopped.") from exc
        if not isinstance(batch, list):
            raise ValueError("GitHub did not return a PR list")
        pulls.extend(pr for pr in batch if pr["head"]["ref"] == branch
                     and pr["head"]["repo"]["full_name"].casefold() == repository.casefold())
        if len(batch) < 100:
            return pulls
        page += 1


def comparisons(event_name: str, event: dict, ref: str, sha: str,
                lookup=open_pull_requests) -> list[Comparison]:
    if event_name == "pull_request":
        pr = event["pull_request"]
        return [Comparison(f"PR #{event['number']}", pr["base"]["sha"], pr["head"]["sha"])]
    if event_name not in ("push", "workflow_dispatch"):
        raise ValueError(f"Unsupported event: {event_name}")

    repository = event["repository"]["full_name"]
    default_branch = event["repository"]["default_branch"]
    if event_name == "push" and (ref == f"refs/heads/{default_branch}"
                                 or ref.startswith("refs/tags/")):
        before = event["before"]
        # The first push has an all-zero before SHA: check its entire tree.
        base = None if before and set(before) == {"0"} else before
        return [Comparison("push range", base, sha, merge_base=False)]

    if not ref.startswith("refs/heads/"):
        raise ValueError("Documentation impact requires a branch ref")
    branch = ref.removeprefix("refs/heads/")
    if branch != default_branch:
        pulls = lookup(repository, branch)
        if pulls:
            return [Comparison(f"PR #{pr['number']}", pr["base"]["sha"], sha) for pr in pulls]

    # A first branch push can arrive before the PR is opened. Compare the whole
    # branch against the fetched default branch instead of using event.before.
    return [Comparison(f"branch vs {default_branch}",
                       f"refs/remotes/origin/{default_branch}", sha)]


def git(workspace: Path, *arguments: str, input: str | None = None) -> str:
    return subprocess.run(
        ["git", "-C", str(workspace), *arguments], input=input, check=True,
        capture_output=True, text=True, encoding="utf-8",
    ).stdout


def changed_files(workspace: Path, comparison: Comparison) -> list[str]:
    base = comparison.base
    if base is None:
        base = git(workspace, "hash-object", "-w", "-t", "tree", "--stdin", input="").strip()
    elif comparison.merge_base:
        base = git(workspace, "merge-base", base, comparison.head).strip()
    # NUL separation preserves spaces and non-ASCII names. No shell expansion.
    return list(filter(None, git(workspace, "diff", "--name-only", "--no-renames", "-z",
                                 base, comparison.head, "--").split("\0")))


def local_changed_files(workspace: Path, base: str) -> list[str]:
    """Net local task changes, preserving parent pins but not child checkouts."""
    common = git(workspace, "merge-base", base, "HEAD").strip()
    paths = set(filter(None, git(workspace, "diff", "--name-only", "--no-renames", "-z",
                                 "--ignore-submodules=all", common, "--").split("\0")))
    # The parent index (including committed pins) is independent of the child
    # checkout. A real staged/committed gitlink must still trigger docs review.
    paths.update(filter(None, git(workspace, "diff", "--cached", "--name-only",
                                  "--no-renames", "-z", common, "--").split("\0")))
    paths.update(filter(None, git(workspace, "ls-files", "--others", "--exclude-standard",
                                  "-z").split("\0")))
    return sorted(paths)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--workspace-root", type=Path, default=Path.cwd())
    parser.add_argument("--local-paths", action="store_true",
                        help="Return local task paths as JSON without accessing GitHub")
    parser.add_argument("--base", help="Fetched comparison ref for --local-paths")
    args = parser.parse_args()
    workspace = args.workspace_root.resolve()
    try:
        if args.local_paths:
            if not args.base:
                raise ValueError("Local changed paths require an explicit fetched base")
            # ASCII JSON is decoded by PowerShell without native code-page loss.
            print(json.dumps(local_changed_files(workspace, args.base)))
            return 0
        if args.base:
            raise ValueError("--base is only supported with --local-paths")
        event = json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text(encoding="utf-8"))
        ranges = comparisons(os.environ["GITHUB_EVENT_NAME"], event,
                             os.environ["GITHUB_REF"], os.environ["GITHUB_SHA"])
        result = 0
        for comparison in ranges:
            paths = changed_files(workspace, comparison)
            print(f"Documentation impact: {comparison.label}, {len(paths)} changed path(s)", flush=True)
            if paths:
                command = [sys.executable, str(Path(__file__).with_name("validate_docs.py")),
                           "--workspace-root", str(workspace), "--enforce-diff", "--changed-files-stdin"]
                completed = subprocess.run(command, cwd=workspace, input=json.dumps(paths).encode("ascii"))
                if completed.returncode != 0:
                    result = 1
        return result
    except (OSError, ValueError, KeyError, RuntimeError, subprocess.CalledProcessError) as exc:
        print(f"FAILED: documentation impact check: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
