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

    def test_standalone_documentation_uses_markdown(self):
        standalone = self.owner / "standalone"
        docs = standalone / "docs"
        docs.mkdir(parents=True)
        shutil.copyfile(self.document, docs / self.document.name)
        validation = VALIDATOR["Validation"]()
        VALIDATOR["validate_central_markdown"](docs, standalone, validation)
        self.assertEqual([], validation.errors)

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
