from __future__ import annotations

import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


DOCS_ROOT = Path(__file__).resolve().parents[1]
WORKSPACE = DOCS_ROOT.parent
VALIDATOR = DOCS_ROOT / "scripts" / "validate_docs.py"
SCAFFOLD = DOCS_ROOT / "scripts" / "scaffold_module_docs.py"


class DocumentationToolsTest(unittest.TestCase):
    def run_tool(self, script: Path, *arguments: str) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            [sys.executable, str(script), *arguments],
            cwd=WORKSPACE,
            capture_output=True,
            text=True,
            encoding="utf-8",
            check=False,
        )

    def test_current_workspace_is_valid(self) -> None:
        result = self.run_tool(VALIDATOR, "--workspace-root", str(WORKSPACE))
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn("OK:", result.stdout)

    def test_changed_file_reports_impact(self) -> None:
        result = self.run_tool(
            VALIDATOR,
            "--workspace-root",
            str(WORKSPACE),
            "--changed-file",
            "app/build.gradle",
        )
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn("BUILD-AND-FLAVORS", result.stdout)

    def test_strict_changed_file_requires_documentation(self) -> None:
        result = self.run_tool(
            VALIDATOR,
            "--workspace-root",
            str(WORKSPACE),
            "--changed-file",
            "app/build.gradle",
            "--enforce-diff",
        )
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("strict trigger matched", result.stdout)

    def test_strict_changed_file_accepts_required_documentation(self) -> None:
        result = self.run_tool(
            VALIDATOR,
            "--workspace-root",
            str(WORKSPACE),
            "--changed-file",
            "app/build.gradle",
            "--changed-file",
            "docs/reference/build-matrix.md",
            "--changed-file",
            "docs/reference/official-differences.md",
            "--enforce-diff",
        )
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def test_product_change_requires_official_differences(self) -> None:
        missing = self.run_tool(
            VALIDATOR,
            "--workspace-root",
            str(WORKSPACE),
            "--changed-file",
            "maplib",
            "--enforce-diff",
        )
        self.assertNotEqual(missing.returncode, 0)
        self.assertIn("OFFICIAL-DIFFERENCES", missing.stdout)

        included = self.run_tool(
            VALIDATOR,
            "--workspace-root",
            str(WORKSPACE),
            "--changed-file",
            "maplib",
            "--changed-file",
            "docs/reference/official-differences.md",
            "--enforce-diff",
        )
        self.assertEqual(included.returncode, 0, included.stdout + included.stderr)

    def test_scaffold_dry_run_and_exclusive_create(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            workspace = Path(temporary)
            (workspace / "app").mkdir()
            dry_run = self.run_tool(
                SCAFFOLD,
                "app",
                "--workspace-root",
                str(workspace),
                "--docs-root",
                str(DOCS_ROOT),
                "--dry-run",
            )
            self.assertEqual(dry_run.returncode, 0, dry_run.stdout + dry_run.stderr)
            self.assertFalse((workspace / "app" / "AGENTS.md").exists())
            self.assertFalse((workspace / "app" / "docs").exists())

            created = self.run_tool(
                SCAFFOLD,
                "app",
                "--workspace-root",
                str(workspace),
                "--docs-root",
                str(DOCS_ROOT),
            )
            self.assertEqual(created.returncode, 0, created.stdout + created.stderr)
            self.assertTrue((workspace / "app" / "AGENTS.md").is_file())
            self.assertTrue((workspace / "app" / "docs" / "README.md").is_file())
            self.assertTrue((workspace / "app" / "docs" / "manifest.yaml").is_file())

            repeated = self.run_tool(
                SCAFFOLD,
                "app",
                "--workspace-root",
                str(workspace),
                "--docs-root",
                str(DOCS_ROOT),
            )
            self.assertNotEqual(repeated.returncode, 0)
            self.assertIn("already exists", repeated.stderr)


if __name__ == "__main__":
    unittest.main()
