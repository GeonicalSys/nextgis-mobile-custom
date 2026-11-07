from __future__ import annotations

import json
import runpy
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

import yaml


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

    def test_ecosystem_registry_declares_all_lisa_projects(self) -> None:
        registry = yaml.safe_load(
            (DOCS_ROOT / "registry" / "ecosystem.yaml").read_text(encoding="utf-8")
        )
        self.assertEqual(
            set(registry["systems"]),
            {"mobile-app", "desktop-profiles", "qgis-plugins", "mobile-usage-server"},
        )
        contract_ids = {item["id"] for item in registry["contracts"]}
        boundary_ids = {item["id"] for item in registry["boundaries"]}
        self.assertIn("ECO-NGW-RESOURCE-EXCHANGE", contract_ids)
        self.assertIn("ECO-OFFLINE-BASEMAP-HANDOFF", contract_ids)
        self.assertIn("ECO-NO-DIRECT-FILESYSTEM", boundary_ids)
        self.assertEqual(registry["systems"]["mobile-app"]["repository"], "root")

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
            "docs/reference/flavors-and-versioning.md",
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
            self.assertFalse((workspace / "app" / "AGENTS.md").exists())
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

    def test_scaffold_keeps_the_independent_repository_entrance(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            workspace = Path(temporary)
            owner = workspace / "maplib"
            owner.mkdir()
            arguments = ("maplib", "--workspace-root", str(workspace), "--docs-root", str(DOCS_ROOT))
            policy = owner / "agent-system"
            policy.mkdir()
            marker = policy / "policy-migration.json"
            marker.write_text("{}", encoding="utf-8")
            refused = self.run_tool(SCAFFOLD, *arguments)
            self.assertNotEqual(refused.returncode, 0)
            self.assertFalse((owner / "docs").exists())
            marker.unlink()
            standalone = self.run_tool(SCAFFOLD, *arguments)
            self.assertEqual(standalone.returncode, 0, standalone.stdout + standalone.stderr)
            self.assertTrue((owner / "AGENTS.md").is_file())
            self.assertTrue((owner / "docs" / "manifest.yaml").is_file())

    def test_scaffold_reads_owner_templates_and_refuses_missing_records(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            docs = root / "docs"
            shutil.copytree(DOCS_ROOT / "registry", docs / "registry")
            shutil.copytree(DOCS_ROOT / "templates", docs / "templates")
            knowledge = root / "agent-system/knowledge"
            for name in ("modules", "repositories"):
                shutil.copytree(WORKSPACE / "agent-system/knowledge" / name, knowledge / name)

            provenance = root / "agent-system/provenance/documentation.json"
            provenance.parent.mkdir()
            shutil.copyfile(WORKSPACE / "agent-system/provenance/documentation.json", provenance)
            layout = json.loads(provenance.read_bytes())
            templates = [row for row in layout["sources"] if row["path"].endswith(".tmpl")]
            self.assertEqual(len(templates), 3)
            template_records = knowledge / "documentation"
            template_records.mkdir()
            for row in templates:
                source = WORKSPACE / "agent-system/knowledge/documentation" / row["record"]
                shutil.copyfile(source, template_records / source.name)

            portable = docs / "templates/module-docs/README.md.tmpl"
            portable.write_text("Portable template fixture.\n", encoding="utf-8")
            owner_record = template_records / "scaffold-readme-template.json"
            record = json.loads(owner_record.read_bytes())
            record["facets"]["body"]["value"].insert(-1, "Owner template fixture.")
            owner_record.write_text(json.dumps(record), encoding="utf-8")

            (root / "app").mkdir()
            agents = root / "app/AGENTS.md"
            agents.write_bytes(b"Existing module instructions.\n")
            arguments = ("app", "--workspace-root", str(root), "--docs-root", str(docs))
            dry_run = self.run_tool(SCAFFOLD, *arguments, "--dry-run")
            self.assertEqual(dry_run.returncode, 0, dry_run.stdout + dry_run.stderr)
            self.assertFalse((root / "app/docs").exists())
            created = self.run_tool(SCAFFOLD, *arguments)
            self.assertEqual(created.returncode, 0, created.stdout + created.stderr)
            self.assertIn("Created 2 file(s)", created.stdout)
            self.assertEqual(agents.read_bytes(), b"Existing module instructions.\n")
            readme = (root / "app/docs/README.md").read_text(encoding="utf-8")
            self.assertIn("Owner template fixture.", readme)
            self.assertNotIn("Portable template fixture.", readme)

            provenance.write_text(json.dumps({**layout, "sources": templates}), encoding="utf-8")
            validator = runpy.run_path(str(VALIDATOR))
            validation = validator["Validation"]()
            validator["validate_central_markdown"](docs, root, validation)
            self.assertTrue(any("README.md.tmpl" in error and "Generated template is stale" in error
                                for error in validation.errors))

            owner_record.unlink()
            fresh_workspace = root / "fresh"
            (fresh_workspace / "app").mkdir(parents=True)
            refused = self.run_tool(SCAFFOLD, "app", "--workspace-root", str(fresh_workspace),
                                    "--docs-root", str(docs))
            self.assertNotEqual(refused.returncode, 0)
            self.assertIn("scaffold-readme-template.json", refused.stderr)
            self.assertEqual(list((fresh_workspace / "app").iterdir()), [])

    def test_topology_readers_check_owner_records_and_accept_standalone_yaml(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            docs = root / "docs"
            shutil.copytree(DOCS_ROOT / "registry", docs / "registry")
            shutil.copytree(DOCS_ROOT / "templates", docs / "templates")
            (root / "app").mkdir()
            validator_args = ("--docs-root", str(docs), "--workspace-root", str(WORKSPACE))
            scaffold_args = ("app", "--docs-root", str(docs), "--workspace-root", str(root), "--dry-run")
            for script, arguments in ((VALIDATOR, validator_args), (SCAFFOLD, scaffold_args)):
                result = self.run_tool(script, *arguments)
                self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

            knowledge = root / "agent-system" / "knowledge"
            for name in ("modules", "repositories", "change-impact", "invariants", "upstream-overlaps",
                         "smoke-tests", "config-keys", "dependencies", "ecosystem"):
                shutil.copytree(WORKSPACE / "agent-system" / "knowledge" / name, knowledge / name)
            invariants = docs / "registry" / "invariants.yaml"
            contents = invariants.read_text(encoding="utf-8")
            invariants.write_text(contents + "# stale\n", encoding="utf-8")
            result = self.run_tool(VALIDATOR, *validator_args)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("Generated invariants registry is stale", result.stdout)
            result = self.run_tool(SCAFFOLD, *scaffold_args)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            invariants.write_text(contents, encoding="utf-8")

            view = docs / "registry" / "modules.yaml"
            view.write_text(view.read_text(encoding="utf-8") + "# stale\n", encoding="utf-8")
            for script, arguments in ((VALIDATOR, validator_args), (SCAFFOLD, scaffold_args)):
                result = self.run_tool(script, *arguments)
                self.assertNotEqual(result.returncode, 0)
                self.assertIn("Generated modules registry is stale", result.stdout + result.stderr)

            (knowledge / "modules" / "modules-app.json").unlink()
            for script, arguments in ((VALIDATOR, validator_args), (SCAFFOLD, scaffold_args)):
                result = self.run_tool(script, *arguments)
                self.assertNotEqual(result.returncode, 0)
                self.assertIn("modules registry order, records and relations differ", result.stdout + result.stderr)

    def test_module_manifest_preserves_yaml_order_after_json_key_sorting(self) -> None:
        projector = runpy.run_path(str(DOCS_ROOT / "scripts" / "module_manifest_knowledge.py"))
        directory = projector["DIRECTORY"]
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            records = root / directory
            records.mkdir(parents=True)
            for source in (WORKSPACE / directory).glob("*.json"):
                payload = json.loads(source.read_bytes())
                (records / source.name).write_text(
                    json.dumps(payload, ensure_ascii=False, sort_keys=True), encoding="utf-8"
                )
            manifest = projector["project_manifest"](root)
            expected = (WORKSPACE / projector["VIEW"]).read_text(encoding="utf-8")
            self.assertEqual(projector["render_manifest"](manifest), expected)

    def test_sectioned_registry_preserves_notes_and_checks_list_identity(self) -> None:
        projector = runpy.run_path(str(DOCS_ROOT / "scripts" / "keyed_registry_knowledge.py"))
        with tempfile.TemporaryDirectory() as temporary:
            owner = Path(temporary)
            relative = Path("agent-system/knowledge/ecosystem")
            shutil.copytree(WORKSPACE / relative, owner / relative)
            registry, notes = projector["project_registry"](owner, "ecosystem")
            view = (DOCS_ROOT / "registry/ecosystem.yaml").read_text(encoding="utf-8")
            self.assertEqual(registry, yaml.safe_load(view))
            self.assertIsInstance(registry["contracts"], list)
            self.assertIsInstance(registry["systems"], dict)
            self.assertTrue(notes)
            self.assertTrue(view.endswith("\n".join(notes) + "\n"))

            contract = next((owner / relative).glob("ecosystem-contracts-*.json"))
            record = json.loads(contract.read_text(encoding="utf-8"))
            record["facets"]["definition"]["value"]["id"] = "different-contract"
            contract.write_text(json.dumps(record), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "Invalid ecosystem list key"):
                projector["project_registry"](owner, "ecosystem")


if __name__ == "__main__":
    unittest.main()
