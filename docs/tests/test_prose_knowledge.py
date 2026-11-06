"""Check the ordinary validator and standalone Markdown entry routes."""

import json
import runpy
import shutil
import tempfile
import unittest
from pathlib import Path, PurePosixPath


APP = Path(__file__).resolve().parents[2]
PROJECTOR = runpy.run_path(str(APP / "docs/scripts/prose_knowledge.py"))
VALIDATOR = runpy.run_path(str(APP / "docs/scripts/validate_docs.py"))
MANIFEST_SCRIPT = Path("docs/scripts/module_manifest_knowledge.py")
MANIFEST_PROJECTOR = runpy.run_path(str(APP / MANIFEST_SCRIPT))
STRUCTURED_PROOF = runpy.run_path(str(APP / "docs/scripts/structured_view_proof.py"))
EASYPICKER_MODULE = "easypicker-module"
COMPARISON = PurePosixPath("docs/reference/official-differences.md")


class ProseKnowledgeTest(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        self.owner = Path(temp.name)
        for key in ("MANIFEST", "DIRECTORY"):
            source = APP / PROJECTOR[key]
            target = self.owner / PROJECTOR[key]
            target.parent.mkdir(parents=True, exist_ok=True)
            if source.is_dir():
                shutil.copytree(source, target)
            else:
                shutil.copyfile(source, target)
        self.document = self.owner / "docs/START-HERE.md"
        self.document.parent.mkdir()
        self.document.write_text(
            "---\ntitle: Standalone\ntype: guide\nlast_verified: 2026-11-04\n"
            "related_code: []\n---\n# Standalone\n",
            encoding="utf-8",
        )

    def errors(self):
        validation = VALIDATOR["Validation"]()
        VALIDATOR["validate_central_markdown"](self.owner / "docs", self.owner, validation)
        return validation.errors

    def test_edited_view_fails_and_links_are_checked_from_source(self):
        record_path = self.owner / PROJECTOR["DIRECTORY"] / "start-here.json"
        record = json.loads(record_path.read_bytes())
        record["facets"]["body"]["value"].append("[test](missing-prose-fixture.md)")
        record_path.write_text(json.dumps(record), encoding="utf-8")
        errors = self.errors()
        self.assertTrue(any("Generated Markdown is stale" in error for error in errors))
        self.assertTrue(any("broken local link: 'missing-prose-fixture.md'" in error for error in errors))

    def test_missing_record_cannot_fall_back_to_view(self):
        (self.owner / PROJECTOR["DIRECTORY"] / "start-here.json").unlink()
        self.assertTrue(any("prose projection failed" in error for error in self.errors()))

    def test_pull_request_template_projects_plain_markdown_from_the_record(self):
        view = PurePosixPath(".github/PULL_REQUEST_TEMPLATE.md")
        original = (APP / view).read_bytes().replace(b"\r\n", b"\n")
        before = PROJECTOR["project_documents"](self.owner)
        self.assertEqual(before[view].encode("utf-8"), original)
        target = self.owner / view
        target.parent.mkdir()
        target.write_bytes(original)

        record_path = self.owner / PROJECTOR["DIRECTORY"] / "pull-request-template.json"
        record = json.loads(record_path.read_bytes())
        record["facets"]["body"]["value"][-1:] = ["- Extra test field", ""]
        record_path.write_text(json.dumps(record), encoding="utf-8")
        after = PROJECTOR["project_documents"](self.owner)
        self.assertEqual(after, {**before, view: before[view] + "- Extra test field\n"})
        with self.assertRaisesRegex(ValueError, "Generated Markdown is stale"):
            PROJECTOR["check_generated"](target, after[view])

    def test_repository_readme_validates_the_record_and_its_local_links(self):
        view = PurePosixPath("README.md")
        target = self.owner / view
        target.write_bytes((APP / view).read_bytes())
        before = PROJECTOR["project_documents"](self.owner)
        PROJECTOR["check_generated"](target, before[view])

        record_path = self.owner / PROJECTOR["DIRECTORY"] / "repository-readme.json"
        record = json.loads(record_path.read_bytes())
        edit = "[Example](missing-readme-fixture.md)\n"
        record["facets"]["body"]["value"][-1:] = edit.split("\n")
        record_path.write_text(json.dumps(record), encoding="utf-8")
        after = PROJECTOR["project_documents"](self.owner)
        self.assertEqual(after, {**before, view: before[view] + edit})
        errors = self.errors()
        self.assertTrue(any("README.md" in error and "Generated Markdown is stale" in error for error in errors))
        self.assertTrue(any("broken local link: 'missing-readme-fixture.md'" in error for error in errors))

    def test_owner_entrances_project_at_their_established_addresses(self):
        paths = [PurePosixPath(path) for path in (
            "CONTEXT_INSTRUCTION.md", "CUSTOMIZATIONS.md", "agent-system/README.md",
        )]
        before = PROJECTOR["project_documents"](self.owner)
        for path in paths:
            with self.subTest(path=path):
                expected = (APP / path).read_bytes().replace(b"\r\n", b"\n")
                self.assertEqual(before[path].encode("utf-8"), expected)

        view = PurePosixPath("agent-system/README.md")
        target = self.owner / view
        target.write_text(before[view], encoding="utf-8")
        record_path = self.owner / PROJECTOR["DIRECTORY"] / "app-knowledge-home.json"
        record = json.loads(record_path.read_bytes())
        link = "[Fixture](missing-knowledge-entrance-fixture.md)"
        record["facets"]["body"]["value"].insert(2, link)
        record_path.write_text(json.dumps(record), encoding="utf-8")
        after = PROJECTOR["project_documents"](self.owner)
        expected = before[view].splitlines(keepends=True)
        expected.insert(2, link + "\n")
        self.assertEqual(after, {**before, view: "".join(expected)})
        with self.assertRaisesRegex(ValueError, "Generated Markdown is stale"):
            PROJECTOR["check_generated"](target, after[view])
        self.assertTrue(any(
            "agent-system/README.md" in error
            and "broken local link: 'missing-knowledge-entrance-fixture.md'" in error
            for error in self.errors()
        ))
        target.write_text(after[view], encoding="utf-8")
        PROJECTOR["check_generated"](target, after[view])

    def test_standalone_documentation_uses_markdown(self):
        standalone = self.owner / "standalone"
        docs = standalone / "docs"
        docs.mkdir(parents=True)
        shutil.copyfile(self.document, docs / self.document.name)
        validation = VALIDATOR["Validation"]()
        VALIDATOR["validate_central_markdown"](docs, standalone, validation)
        self.assertEqual([], validation.errors)

    def test_easypicker_module_preserves_and_counts_empty_lists(self):
        owner = self.owner / "easypicker"
        profile = MANIFEST_PROJECTOR["PROFILES"][EASYPICKER_MODULE]
        source = MANIFEST_PROJECTOR["DIRECTORY"]
        directory = owner / source.parent / profile.topic
        directory.mkdir(parents=True)
        provenance = f"agent-system/provenance/{profile.topic}.json"
        empty_fields = {"settings_refs", "invariant_refs", "upstream_hotspots"}

        def library_identity(identity):
            return identity.replace("lisa:mobile:", "lisa:easypicker:").replace(
                "app-module-manifest", profile.topic
            )

        def write_record(record):
            name = record["id"].rsplit(":", 1)[-1] + ".json"
            (directory / name).write_text(json.dumps(record, sort_keys=True), encoding="utf-8")

        for path in (APP / source).glob("*.json"):
            record = json.loads(path.read_bytes())
            record["id"] = library_identity(record["id"])
            record["owner"], record["topic"] = profile.owner, profile.topic
            record["provenance"] = {"manifest": provenance, "scopes": [record["id"]]}
            for edge in record["relations"]:
                edge["target"] = library_identity(edge["target"])
            facets = record["facets"]
            if record["kind"] == "metadata":
                layout = record
                facets["view-path"]["value"] = "docs/manifest.yaml"
                facets["manifest-metadata"]["value"]["module_id"] = EASYPICKER_MODULE
            elif facets["manifest-field"]["value"] in empty_fields:
                facets["definition"]["value"] = []
                empty_record = record
            write_record(record)

        manifest = MANIFEST_PROJECTOR["project_manifest"](owner, module_id=EASYPICKER_MODULE)
        self.assertEqual(EASYPICKER_MODULE, manifest["module_id"])
        self.assertEqual([[], [], []], [manifest[field] for field in sorted(empty_fields)])
        self.assertEqual(layout["facets"]["manifest-field-order"]["value"], list(manifest)[5:])

        duplicate_id = empty_record["id"] + "-duplicate"
        empty_record["id"] = duplicate_id
        empty_record["provenance"]["scopes"] = [duplicate_id]
        layout["relations"].append({"type": "contains", "target": duplicate_id, "status": "source_backed"})
        write_record(empty_record)
        write_record(layout)
        with self.assertRaisesRegex(ValueError, "Invalid module manifest reference list"):
            MANIFEST_PROJECTOR["project_manifest"](owner, module_id=EASYPICKER_MODULE)

    def test_generated_library_manifests_require_proof(self):
        script = self.owner / MANIFEST_SCRIPT
        script.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(APP / MANIFEST_SCRIPT, script)
        for module_id in (EASYPICKER_MODULE, "maplib", "maplibui"):
            with self.subTest(module=module_id):
                profile = MANIFEST_PROJECTOR["PROFILES"][module_id]
                owner = self.owner / Path(profile.owner).name
                view = owner / profile.view
                view.parent.mkdir(parents=True)
                view.write_text("schema_version: 1\n", encoding="utf-8")
                self.assertFalse(STRUCTURED_PROOF["library_view_declared"](owner, module_id=module_id))
                if module_id == EASYPICKER_MODULE:
                    self.assertFalse(STRUCTURED_PROOF["library_view_declared"](owner))
                view.write_text(
                    MANIFEST_PROJECTOR["generated_heading"](module_id) + "\nschema_version: 1\n",
                    encoding="utf-8",
                )
                self.assertTrue(STRUCTURED_PROOF["library_view_declared"](owner, module_id=module_id))
                with self.assertRaises(FileNotFoundError):
                    STRUCTURED_PROOF["check_proof"](owner, module_id=module_id)

    def comparison_layout(self):
        layout = self.owner / PROJECTOR["DIRECTORY"] / "official-differences.json"
        return layout, json.loads(layout.read_bytes())

    def test_comparison_topic_edit_changes_only_that_span(self):
        _, layout = self.comparison_layout()
        target = layout["relations"][0]["target"]
        section = self.owner / PROJECTOR["DIRECTORY"] / f"{target.rsplit(':', 1)[1]}.json"
        record = json.loads(section.read_bytes())
        body = record["facets"]["body"]["value"]
        original_span = "\n".join(body)
        original = PROJECTOR["project_documents"](self.owner)[COMPARISON]
        self.assertEqual(1, original.count(original_span))
        body[0] += " (test edit)"
        section.write_text(json.dumps(record), encoding="utf-8")
        projected = PROJECTOR["project_documents"](self.owner)[COMPARISON]
        self.assertEqual(original.replace(original_span, "\n".join(body)), projected)

    def test_comparison_layout_orders_sections_and_rejects_duplicates(self):
        path, layout = self.comparison_layout()
        first, second = layout["relations"][:2]
        spans = []
        for edge in (first, second):
            section = path.with_name(edge["target"].rsplit(":", 1)[1] + ".json")
            body = json.loads(section.read_bytes())["facets"]["body"]["value"]
            spans.append("\n".join(body))
        original = PROJECTOR["project_documents"](self.owner)[COMPARISON]
        layout["relations"][:2] = [second, first]
        path.write_text(json.dumps(layout), encoding="utf-8")
        reordered = PROJECTOR["project_documents"](self.owner)[COMPARISON]
        self.assertNotEqual(original, reordered)
        self.assertEqual(original.replace("\n".join(spans), "\n".join(reversed(spans))), reordered)
        layout["relations"][0] = first
        path.write_text(json.dumps(layout), encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "duplicate documentation record"):
            PROJECTOR["project_documents"](self.owner)

    def test_comparison_missing_section_fails_closed(self):
        path, layout = self.comparison_layout()
        target = layout["relations"][0]["target"]
        path.with_name(target.rsplit(":", 1)[1] + ".json").unlink()
        with self.assertRaises(FileNotFoundError):
            PROJECTOR["project_documents"](self.owner)

    def test_sync_ack_boundary_is_an_independent_author_edit(self):
        view = PurePosixPath("docs/architecture/ngw-sync-and-storage.md")
        directory = self.owner / PROJECTOR["DIRECTORY"]
        section = directory / "ngw-sync-and-storage-account-ack-and-project-clock.json"
        evidence = directory / "ngw-sync-and-storage-failed-clock-commit-f61.json"
        record = json.loads(section.read_bytes())
        historical = json.loads(evidence.read_bytes())
        self.assertEqual("resource", record["kind"])
        self.assertEqual("historical", historical["status"])
        body = record["facets"]["body"]["value"]
        original = PROJECTOR["project_documents"](self.owner)[view]
        old_span = "\n".join(body)
        self.assertEqual(1, original.count(old_span))
        body[2] += " (test edit)"
        section.write_text(json.dumps(record, ensure_ascii=False), encoding="utf-8")
        projected = PROJECTOR["project_documents"](self.owner)[view]
        self.assertEqual(original.replace(old_span, "\n".join(body)), projected)

    def test_roadmap_legacy_alias_does_not_join_tasks_or_evidence(self):
        directory = self.owner / PROJECTOR["DIRECTORY"]
        incident_path = directory / "mobile-roadmap-process-exit-224.json"
        discovery_path = directory / "mobile-roadmap-dependency-discovery.json"
        incident = json.loads(incident_path.read_bytes())
        discovery = json.loads(discovery_path.read_bytes())
        self.assertEqual(["task", "task"], [incident["kind"], discovery["kind"]])
        self.assertEqual(["MOB-02", "MOB-02"], [
            row["facets"]["legacy-id"]["value"] for row in (incident, discovery)
        ])
        self.assertNotEqual(incident["id"], discovery["id"])
        self.assertEqual("merged", discovery["lifecycle"]["merge"])
        self.assertEqual("NOT_APPLICABLE", discovery["lifecycle"]["runtime_verification"])
        self.assertEqual("UNKNOWN", incident["lifecycle"]["runtime_verification"])
        journal = "lisa:mobile:evidence:mobile-roadmap-discovery-journal-confirmation-20261030"
        self.assertIn(journal, [edge["target"] for edge in discovery["relations"]])
        self.assertNotIn(journal, [edge["target"] for edge in incident["relations"]])

        view = PurePosixPath("docs/roadmap/mobile.md")
        original = PROJECTOR["project_documents"](self.owner)[view]
        body = discovery["facets"]["body"]["value"]
        span = "\n".join(body)
        self.assertEqual(1, original.count(span))
        body[2] += " (test edit)"
        discovery_path.write_text(json.dumps(discovery, ensure_ascii=False), encoding="utf-8")
        projected = PROJECTOR["project_documents"](self.owner)[view]
        self.assertEqual(original.replace(span, "\n".join(body)), projected)
        self.assertEqual(incident, json.loads(incident_path.read_bytes()))

    def test_rendering_clause_twelve_keeps_two_independent_subjects(self):
        view = PurePosixPath("docs/architecture/map-rendering.md")
        directory = self.owner / PROJECTOR["DIRECTORY"]
        rule_path = directory / "map-rendering-rule-style-defaults-and-zoom.json"
        identify_path = directory / "map-rendering-identify-refinement-and-label.json"
        rule = json.loads(rule_path.read_bytes())
        identify = json.loads(identify_path.read_bytes())
        evidence = json.loads((directory / "map-rendering-sqlite-priority-t40-results.json").read_bytes())
        self.assertNotEqual(rule["id"], identify["id"])
        self.assertEqual("historical", evidence["status"])
        self.assertFalse(evidence["execution_permission"])

        original = PROJECTOR["project_documents"](self.owner)[view]
        clauses = original.split("## Контракты\n", 1)[1].split("\nIDs:", 1)[0]
        numbers = [int(line.split(".", 1)[0]) for line in clauses.splitlines()
                   if line.split(".", 1)[0].isdigit()]
        self.assertEqual([*range(1, 13), *range(12, 29)], numbers)
        body = rule["facets"]["body"]["value"]
        old_span = "\n".join(body)
        self.assertEqual(1, original.count(old_span))
        body[0] += " (test edit)"
        rule_path.write_text(json.dumps(rule, ensure_ascii=False), encoding="utf-8")
        projected = PROJECTOR["project_documents"](self.owner)[view]
        self.assertEqual(original.replace(old_span, "\n".join(body)), projected)
        self.assertEqual(identify, json.loads(identify_path.read_bytes()))
