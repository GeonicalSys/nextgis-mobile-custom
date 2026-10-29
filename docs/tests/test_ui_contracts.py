"""Technical source/registry mutations, never Android GUI/GPS acceptance."""
from __future__ import annotations

import copy
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[1] / "scripts/check_ui_contracts.py"
SPEC = importlib.util.spec_from_file_location("ui_contract_source_check", SCRIPT)
CHECKER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(CHECKER)

SOURCE = '''class MapScreen {
    private fun fillStatusPanel(location: Location) {
        coordinates.text = format(location.latitude, location.longitude)
    }
}
'''
MARKER = "private fun fillStatusPanel(location: Location)"
XML_SOURCE = ('<TextView xmlns:android="http://schemas.android.com/apk/res/android" '
              'android:id="@+id/coordinates"/>')


def catalogue() -> dict:
    return {
        "schema_version": 1,
        "coverage": {"status": "bounded_source_review", "runtime": "not_run",
                     "reviewed_groups": ["location"], "not_reviewed": ["Device transitions"]},
        "sources": [{"id": "status-location", "repository": "root", "path": "app/Screen.kt",
                     "marker": MARKER, "requires": ["format(location.latitude, location.longitude)"],
                     "forbids": ["getMaplibreCenter()"],
                     "sha256": CHECKER.fingerprint(CHECKER.extract_method(SOURCE, MARKER))}],
        "widget_evidence": [{"repository": "root", "path": "app/panel.xml", "id": "coordinates",
                             "sha256": CHECKER.fingerprint(XML_SOURCE)}],
        "contracts": [{"id": "UI-LOCATION", "group": "location", "title": "Location label",
                       "surface": "Map status panel", "variants": ["lisa", "belka"],
                       "widgets": ["coordinates"], "meaning": "Device position",
                       "value_origin": "Location latitude and longitude", "units": "WGS84 degrees",
                       "updates": ["Display fix"], "actions": [], "conditions": ["Available fix"],
                       "saved_state": "No project center write", "source_ids": ["status-location"],
                       "expectations": ["Moving camera independently must not rewrite the device fix"],
                       "unknowns": ["Device execution not run"]}],
    }


class UiContractCheckTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="ui-contract-unit-")
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        (self.root / "app").mkdir()
        self.source = self.root / "app/Screen.kt"
        self.source.write_text(SOURCE, encoding="utf-8")
        self.widget = self.root / "app/panel.xml"
        self.widget.write_text(XML_SOURCE, encoding="utf-8")
        self.registry = self.root / "ui.json"
        self.data = catalogue()

    def check(self):
        self.registry.write_text(json.dumps(self.data), encoding="utf-8")
        return CHECKER.check_catalog(self.root, self.registry)

    def test_reviewed_contract_passes_with_explicit_unknown_runtime(self):
        errors, summary = self.check()
        self.assertEqual(errors, [])
        self.assertIn("runtime NOT_RUN", summary)

    def test_camera_substitution_invalidates_review(self):
        self.source.write_text(SOURCE.replace("format(location.latitude, location.longitude)",
                                              "format(getMaplibreCenter())"), encoding="utf-8")
        self.assertIn("reviewed method changed", str(self.check()[0]))

    def test_repin_cannot_hide_missing_producer_with_comment(self):
        changed = SOURCE.replace("format(location.latitude, location.longitude)",
                                 "cameraPosition // format(location.latitude, location.longitude)")
        self.source.write_text(changed, encoding="utf-8")
        self.data["sources"][0]["sha256"] = CHECKER.fingerprint(CHECKER.extract_method(changed, MARKER))
        self.assertIn("required producer", str(self.check()[0]))

    def test_forbidden_camera_call_fails_even_after_hash_change(self):
        changed = SOURCE.replace("coordinates.text", "getMaplibreCenter()\n        coordinates.text")
        self.source.write_text(changed, encoding="utf-8")
        self.data["sources"][0]["sha256"] = CHECKER.fingerprint(CHECKER.extract_method(changed, MARKER))
        self.assertIn("forbidden producer", str(self.check()[0]))

    def test_same_signature_changed_action_requires_review(self):
        self.source.write_text(SOURCE.replace("coordinates.text =", "project.center ="), encoding="utf-8")
        self.assertIn("reviewed method changed", str(self.check()[0]))

    def test_missing_method_fails(self):
        self.source.write_text("class Empty {}", encoding="utf-8")
        self.assertIn("found 0", str(self.check()[0]))

    def test_duplicate_method_fails(self):
        self.source.write_text(SOURCE + SOURCE, encoding="utf-8")
        self.assertIn("found 2", str(self.check()[0]))

    def test_callsite_is_not_declaration(self):
        with self.assertRaises(ValueError):
            CHECKER.extract_method("fun outer() { fillStatusPanel(location) { x() } }", "fillStatusPanel(location)")

    def test_literal_and_comment_braces_do_not_end_method(self):
        text = '''private fun value() {
          val raw = """ } // /* { """
          val ordinary = "escaped \\\" }"
          val ch = '}'
          /* } nested /* { */ } */
          if (true) { finish() }
        }
        fun after() { wrong() }
        '''
        method = CHECKER.extract_method(text, "private fun value()")
        self.assertIn("finish()", method)
        self.assertNotIn("wrong()", method)

    def test_method_marker_in_literal_or_comment_is_not_source(self):
        text = f'val example = "{MARKER}"\n/* {MARKER} {{ }} */\n' + SOURCE
        self.assertEqual(CHECKER.extract_method(text, MARKER), CHECKER.extract_method(SOURCE, MARKER))

    def test_crlf_is_portable(self):
        method = CHECKER.extract_method(SOURCE.replace("\n", "\r\n"), MARKER)
        self.assertEqual(CHECKER.fingerprint(method), self.data["sources"][0]["sha256"])

    def test_missing_source_and_library_are_not_pass(self):
        self.data["sources"][0]["repository"] = "maplib"
        self.assertIn("coverage is UNKNOWN", str(self.check()[0]))

    def test_escaped_absolute_and_parent_paths_fail(self):
        for value in ("../outside.kt", "C:/outside.kt", "/outside.kt", "app\\Screen.kt"):
            with self.subTest(path=value):
                self.data["sources"][0]["path"] = value
                self.assertIn("confined", str(self.check()[0]))

    def test_unknown_source_reference_fails(self):
        self.data["contracts"][0]["source_ids"] = ["wrong"]
        self.assertIn("unknown source references", str(self.check()[0]))

    def test_duplicate_ids_and_orphan_sources_fail(self):
        duplicate = copy.deepcopy(self.data["sources"][0])
        self.data["sources"].append(duplicate)
        self.assertIn("duplicate source ID", str(self.check()[0]))
        duplicate["id"] = "orphan"
        self.assertIn("orphan source", str(self.check()[0]))

    def test_empty_catalogue_is_not_coverage(self):
        self.data["contracts"] = []
        self.assertIn("nonempty", str(self.check()[0]))

    def test_missing_independent_expectation_fails(self):
        self.data["contracts"][0]["expectations"] = []
        self.assertIn("independent expectations", str(self.check()[0]))

    def test_unknowns_cannot_be_relabelled_native_pass(self):
        self.data["coverage"]["runtime"] = "PASS"
        self.assertIn("runtime not_run", str(self.check()[0]))

    def test_claimed_group_requires_registered_evidence(self):
        self.data["coverage"]["reviewed_groups"].append("forms")
        self.assertIn("reviewed_groups", str(self.check()[0]))

    def test_check_is_read_only(self):
        self.check()
        original_source = self.source.read_bytes()
        original_registry = self.registry.read_bytes()
        self.assertEqual(CHECKER.check_catalog(self.root, self.registry)[0], [])
        self.assertEqual(self.source.read_bytes(), original_source)
        self.assertEqual(self.registry.read_bytes(), original_registry)

    def test_deleted_widget_id_fails_without_a_code_change(self):
        self.widget.write_text("<TextView/>", encoding="utf-8")
        self.assertIn("absent from its reviewed XML", str(self.check()[0]))

    def test_contract_cannot_reference_an_unreviewed_widget(self):
        self.data["contracts"][0]["widgets"] = ["other_indicator"]
        self.assertIn("widget lacks reviewed XML", str(self.check()[0]))

    def test_empty_widget_list_is_explicit_for_programmatic_surface(self):
        self.data["contracts"][0]["widgets"] = []
        self.data["widget_evidence"] = []
        self.assertEqual(self.check()[0], [])

    def test_producer_in_string_or_raw_string_is_not_code(self):
        for quote in ('"', '"""'):
            with self.subTest(quote=quote):
                changed = SOURCE.replace("format(location.latitude, location.longitude)",
                                         quote + "format(location.latitude, location.longitude)" + quote)
                self.source.write_text(changed, encoding="utf-8")
                self.data["sources"][0]["sha256"] = CHECKER.fingerprint(CHECKER.extract_method(changed, MARKER))
                self.assertIn("required producer", str(self.check()[0]))

    def test_format_string_followed_by_real_producer_is_code(self):
        method = 'private fun panel() { text = "%.1fz".format(map.zoom) }'
        self.assertTrue(CHECKER.fragment_in_code(method, '"%.1fz".format(map.zoom)'))
        self.assertFalse(CHECKER.fragment_in_code(method, '"%.1fz"'))

    def test_expression_body_cannot_borrow_next_method_body(self):
        with self.assertRaises(ValueError):
            CHECKER.extract_method("private fun value() = cameraValue\nfun next() { producer() }", "private fun value()")

    def test_expression_if_body_is_not_a_complete_block_method(self):
        for marker in ("private fun value()", "private fun value() = if (true)"):
            with self.subTest(marker=marker), self.assertRaises(ValueError):
                CHECKER.extract_method("private fun value() = if (true) { first() } else { second() }", marker)

    def test_return_callsite_cannot_imitate_java_declaration(self):
        with self.assertRaises(ValueError):
            CHECKER.extract_method("fun outer() { return render(location) { nested() } }", "return render(location)")

    def test_boolean_is_not_catalogue_schema_version(self):
        self.data["schema_version"] = True
        self.assertIn("unsupported", str(self.check()[0]))

    def test_non_method_constant_change_requires_semantic_review(self):
        path = self.root / "app/Policy.java"
        original = "class Policy { static final long FRESHNESS_MS = 8000; }"
        path.write_text(original, encoding="utf-8")
        self.data["non_method_evidence"] = [{"repository": "root", "path": "app/Policy.java",
            "purpose": "Reviewed freshness threshold", "contract_ids": ["UI-LOCATION"],
            "sha256": CHECKER.fingerprint(original)}]
        self.assertEqual(self.check()[0], [])
        path.write_text(original.replace("8000", "80000"), encoding="utf-8")
        self.assertIn("semantic review required", str(self.check()[0]))

    def test_xml_default_change_requires_review_without_widget_id_change(self):
        original = self.widget.read_text(encoding="utf-8")
        self.data["non_method_evidence"] = [{"repository": "root", "path": "app/panel.xml",
            "purpose": "Reviewed preference default", "contract_ids": ["UI-LOCATION"],
            "sha256": CHECKER.fingerprint(original)}]
        self.widget.write_text(original.replace('/>', ' android:defaultValue="other"/>'), encoding="utf-8")
        self.assertIn("semantic review required", str(self.check()[0]))

    def test_same_id_changed_xml_handler_is_not_reviewed(self):
        self.widget.write_text(XML_SOURCE.replace('/>', ' android:onClick="otherAction"/>'), encoding="utf-8")
        self.assertIn("reviewed XML bindings changed", str(self.check()[0]))

    def test_label_resource_change_requires_review(self):
        path = self.root / "app/strings.xml"
        original = '<resources><string name="stakeout_action">Слежение</string></resources>'
        path.write_text(original, encoding="utf-8")
        self.data["non_method_evidence"] = [{"repository": "root", "path": "app/strings.xml",
            "purpose": "Reviewed UI action label", "contract_ids": ["UI-LOCATION"],
            "sha256": CHECKER.fingerprint(original)}]
        self.assertEqual(self.check()[0], [])
        path.write_text(original.replace('Слежение', 'Другое действие'), encoding="utf-8")
        self.assertIn("semantic review required", str(self.check()[0]))


if __name__ == "__main__":
    unittest.main()
