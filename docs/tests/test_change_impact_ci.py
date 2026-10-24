from __future__ import annotations

import importlib.util
import io
import json
import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock
from urllib.error import URLError
from urllib.parse import parse_qs, urlparse

import yaml


DOCS = Path(__file__).resolve().parents[1]


def load_script(name):
    spec = importlib.util.spec_from_file_location(name, DOCS / "scripts" / f"{name}.py")
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
    return module


impact = load_script("check_change_impact")
validator = load_script("validate_docs")
TRIGGERS = yaml.safe_load((DOCS / "registry" / "change-impact.yaml").read_text(encoding="utf-8"))["triggers"]
OFFICIAL = "docs/reference/official-differences.md"
REPOSITORY = "example/mobile"


def pull(number, base, repository=REPOSITORY, branch="feature/docs"):
    return {"number": number, "base": {"sha": base},
            "head": {"ref": branch, "repo": {"full_name": repository}}}


class ChangeImpactHistoryTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="docs-impact-")
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.git("init", "-b", "my-maplibre")
        self.git("config", "user.name", "Documentation test")
        self.git("config", "user.email", "docs-test@example.invalid")
        self.git("config", "core.autocrlf", "false")
        self.git("config", "core.hooksPath", str(self.root / "no-hooks"))
        self.write("maplib", "original library\n")
        self.write(OFFICIAL, "original documentation\n")
        self.base = self.commit("base")
        self.git("update-ref", "refs/remotes/origin/my-maplibre", self.base)
        self.git("switch", "-c", "feature/docs")
        self.event = {"repository": {"full_name": REPOSITORY, "default_branch": "my-maplibre"},
                      "before": self.base}

    def git(self, *arguments):
        return impact.git(self.root, *arguments).strip()

    def write(self, name, text):
        path = self.root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")

    def commit(self, message):
        self.git("add", ".")
        self.git("commit", "-m", message)
        return self.git("rev-parse", "HEAD")

    def change_library(self):
        self.write("maplib", "updated library\n")
        return self.commit("code")

    def ranges(self, pulls, event_name="push", ref="refs/heads/feature/docs"):
        lookup = mock.Mock(return_value=pulls)
        ranges = impact.comparisons(event_name, self.event, ref,
                                    self.git("rev-parse", "HEAD"), lookup)
        return ranges, lookup

    def errors(self, paths):
        result = validator.Validation()
        validator.report_changed_files(TRIGGERS, paths, True, result)
        return result.errors

    def test_push_accepts_docs_from_earlier_pr_commit(self):
        self.write(OFFICIAL, "updated documentation\n")
        self.event["before"] = self.commit("docs first")
        head = self.change_library()
        last_push = impact.changed_files(self.root, impact.Comparison(
            "old push range", self.event["before"], head, merge_base=False))
        self.assertTrue(self.errors(last_push), "The old last-push range must reproduce the failure")

        ranges, lookup = self.ranges([pull(77, self.base)])
        lookup.assert_called_once_with(REPOSITORY, "feature/docs")
        paths = impact.changed_files(self.root, ranges[0])
        self.assertEqual(set(paths), {"maplib", OFFICIAL})
        self.assertEqual(self.errors(paths), [])

    def test_missing_docs_still_fail(self):
        self.change_library()
        ranges, _ = self.ranges([pull(77, self.base)])
        errors = self.errors(impact.changed_files(self.root, ranges[0]))
        self.assertTrue(any("OFFICIAL-DIFFERENCES" in error for error in errors))

    def test_reverted_docs_do_not_satisfy_the_pr(self):
        self.write(OFFICIAL, "temporary update\n")
        self.commit("docs")
        self.write(OFFICIAL, "original documentation\n")
        self.change_library()
        ranges, _ = self.ranges([pull(77, self.base)])
        paths = impact.changed_files(self.root, ranges[0])
        self.assertNotIn(OFFICIAL, paths)
        self.assertTrue(self.errors(paths))

    def test_base_only_docs_do_not_satisfy_the_pr(self):
        self.git("switch", "-c", "target", self.base)
        self.write(OFFICIAL, "documentation changed only in target\n")
        advanced_base = self.commit("unrelated target docs")
        self.git("switch", "feature/docs")
        head = self.change_library()
        ranges, _ = self.ranges([pull(77, advanced_base)])
        paths = impact.changed_files(self.root, ranges[0])
        self.assertNotIn(OFFICIAL, paths)
        self.assertTrue(self.errors(paths))
        self.assertIn(OFFICIAL, impact.changed_files(self.root, impact.Comparison(
            "incorrect two-dot comparison", advanced_base, head, merge_base=False)))

    def test_pull_request_uses_payload_head_instead_of_checkout_merge(self):
        self.write(OFFICIAL, "updated documentation\n")
        self.commit("docs")
        feature_head = self.change_library()
        self.git("switch", "-c", "checkout-merge", self.base)
        self.write("app/build.gradle", "unrelated target build change\n")
        advanced_base = self.commit("target change")
        self.git("merge", "--no-ff", "feature/docs", "-m", "synthetic PR checkout")
        self.event.update(number=77, pull_request={"base": {"sha": advanced_base},
                                                  "head": {"sha": feature_head}})
        ranges, lookup = self.ranges([], event_name="pull_request", ref="refs/pull/77/merge")
        lookup.assert_not_called()
        self.assertEqual(set(impact.changed_files(self.root, ranges[0])), {"maplib", OFFICIAL})

    def test_new_branch_before_pr_open_uses_default_branch(self):
        self.write(OFFICIAL, "updated documentation\n")
        self.commit("docs")
        self.change_library()
        self.event["before"] = "0" * 40
        ranges, _ = self.ranges([])
        paths = impact.changed_files(self.root, ranges[0])
        self.assertEqual(set(paths), {"maplib", OFFICIAL})
        self.assertEqual(self.errors(paths), [])

    def test_each_pr_uses_its_own_base_including_stacked_pr(self):
        self.write(OFFICIAL, "updated documentation\n")
        stack_base = self.commit("parent PR docs")
        self.change_library()
        ranges, _ = self.ranges([pull(77, self.base), pull(78, stack_base)])
        self.assertEqual(len(ranges), 2)
        self.assertEqual(self.errors(impact.changed_files(self.root, ranges[0])), [])
        self.assertTrue(self.errors(impact.changed_files(self.root, ranges[1])))

    def test_default_branch_keeps_push_range(self):
        self.write(OFFICIAL, "docs from previous main push\n")
        self.event["before"] = self.commit("previous push")
        self.change_library()
        ranges, lookup = self.ranges([], ref="refs/heads/my-maplibre")
        lookup.assert_not_called()
        self.assertEqual(impact.changed_files(self.root, ranges[0]), ["maplib"])
        self.assertTrue(self.errors(impact.changed_files(self.root, ranges[0])))

    def test_initial_default_push_checks_entire_tree(self):
        self.event["before"] = "0" * 40
        ranges, lookup = self.ranges([], ref="refs/heads/my-maplibre")
        lookup.assert_not_called()
        self.assertEqual(set(impact.changed_files(self.root, ranges[0])), {"maplib", OFFICIAL})

    def test_manual_branch_run_also_uses_pr_base(self):
        self.write(OFFICIAL, "updated documentation\n")
        self.commit("docs")
        self.change_library()
        ranges, _ = self.ranges([pull(77, self.base)], event_name="workflow_dispatch")
        self.assertEqual(self.errors(impact.changed_files(self.root, ranges[0])), [])

    def test_tag_push_uses_event_range_without_pr_lookup(self):
        self.change_library()
        ranges, lookup = self.ranges([], ref="refs/tags/test-release")
        lookup.assert_not_called()
        self.assertEqual(impact.changed_files(self.root, ranges[0]), ["maplib"])

    def test_paths_with_spaces_and_cyrillic_are_not_split_or_quoted(self):
        name = "notes/проверка диапазона.txt"
        self.write(name, "test\n")
        self.commit("path")
        ranges, _ = self.ranges([pull(77, self.base)])
        self.assertEqual(impact.changed_files(self.root, ranges[0]), [name])

    def test_missing_history_fails_instead_of_skipping_enforcement(self):
        ranges, _ = self.ranges([pull(77, "f" * 40)])
        with self.assertRaises(subprocess.CalledProcessError):
            impact.changed_files(self.root, ranges[0])


class PullRequestLookupTest(unittest.TestCase):
    @mock.patch.dict(os.environ, {"GITHUB_TOKEN": "unit-test-placeholder"})
    @mock.patch.object(impact, "urlopen")
    def test_pagination_and_exact_head_repository_filter(self, urlopen):
        first = [pull(n, "a" * 40, repository="example/another-repository") for n in range(100)]
        second = [pull(101, "b" * 40)]
        urlopen.side_effect = [io.BytesIO(json.dumps(batch).encode("utf-8"))
                               for batch in (first, second)]
        self.assertEqual(impact.open_pull_requests(REPOSITORY, "feature/docs"), second)
        self.assertEqual(urlopen.call_count, 2)
        for page, call in enumerate(urlopen.call_args_list, start=1):
            query = parse_qs(urlparse(call.args[0].full_url).query)
            self.assertEqual(query["page"], [str(page)])
            self.assertEqual(query["head"], ["example:feature/docs"])
            self.assertEqual(query["state"], ["open"])

    @mock.patch.dict(os.environ, {"GITHUB_TOKEN": "unit-test-placeholder"})
    @mock.patch.object(impact, "urlopen", side_effect=URLError("unavailable"))
    def test_api_failure_stops_instead_of_falling_back_to_last_push(self, urlopen):
        with self.assertRaisesRegex(RuntimeError, "impact check stopped"):
            impact.open_pull_requests(REPOSITORY, "feature/docs")


class ChangeImpactCommandTest(unittest.TestCase):
    def test_api_failure_is_a_failing_command_without_validator_fallback(self):
        with tempfile.TemporaryDirectory(prefix="docs-impact-event-") as temporary:
            event_path = Path(temporary) / "event.json"
            event_path.write_text(json.dumps({"repository": {
                "full_name": REPOSITORY, "default_branch": "my-maplibre"},
                "before": "a" * 40}), encoding="utf-8")
            environment = {"GITHUB_EVENT_NAME": "push", "GITHUB_EVENT_PATH": str(event_path),
                           "GITHUB_REF": "refs/heads/feature/docs", "GITHUB_SHA": "b" * 40,
                           "GITHUB_TOKEN": "unit-test-placeholder"}
            with mock.patch.dict(os.environ, environment), \
                    mock.patch.object(sys, "argv", ["check_change_impact.py"]), \
                    mock.patch.object(impact, "urlopen", side_effect=URLError("unavailable")), \
                    mock.patch.object(impact.subprocess, "call") as run_validator, \
                    mock.patch("sys.stderr", new_callable=io.StringIO) as error:
                self.assertEqual(impact.main(), 1)
                run_validator.assert_not_called()
                self.assertIn("impact check stopped", error.getvalue())
                self.assertNotIn(environment["GITHUB_TOKEN"], error.getvalue())

    def test_validator_failure_and_termination_are_not_reported_as_success(self):
        with tempfile.TemporaryDirectory(prefix="docs-impact-event-") as temporary:
            event_path = Path(temporary) / "event.json"
            event_path.write_text(json.dumps({"number": 77, "pull_request": {
                "base": {"sha": "a" * 40}, "head": {"sha": "b" * 40}}}), encoding="utf-8")
            environment = {"GITHUB_EVENT_NAME": "pull_request", "GITHUB_EVENT_PATH": str(event_path),
                           "GITHUB_REF": "refs/pull/77/merge", "GITHUB_SHA": "c" * 40}
            with mock.patch.dict(os.environ, environment), \
                    mock.patch.object(sys, "argv", ["check_change_impact.py"]), \
                    mock.patch.object(impact, "changed_files", return_value=["maplib"]), \
                    mock.patch("sys.stdout", new_callable=io.StringIO):
                for validator_result in (1, -9):
                    with self.subTest(returncode=validator_result), \
                            mock.patch.object(impact.subprocess, "call", return_value=validator_result):
                        self.assertEqual(impact.main(), 1)


if __name__ == "__main__":
    unittest.main()
