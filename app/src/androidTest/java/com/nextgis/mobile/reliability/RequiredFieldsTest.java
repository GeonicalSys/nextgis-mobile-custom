package com.nextgis.mobile.reliability;

import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.PopupMenu;
import android.widget.Spinner;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.nextgis.maplib.datasource.Field;
import com.nextgis.maplib.datasource.GeoPoint;
import com.nextgis.maplib.map.MapContentProviderHelper;
import com.nextgis.maplib.util.Constants;
import com.nextgis.maplib.util.GeoConstants;
import com.nextgis.maplibui.GISApplication;
import com.nextgis.maplibui.R;
import com.nextgis.maplibui.activity.FormBuilderModifyAttributesActivity;
import com.nextgis.maplibui.activity.ModifyAttributesActivity;
import com.nextgis.maplibui.api.IControl;
import com.nextgis.maplibui.control.PhotoGallery;
import com.nextgis.maplibui.formcontrol.Tabs;
import com.nextgis.maplibui.mapui.VectorLayerUI;
import com.nextgis.maplibui.util.ConstantsUI;
import com.nextgis.maplibui.util.FeatureFormDraftStore;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

/** Real standard/NGFP controls, SQLite and lifecycle on an isolated emulator; no NGW connection. */
@RunWith(AndroidJUnit4.class)
public class RequiredFieldsTest {
    @SuppressWarnings("unchecked")
    private static Map<String, IControl> controls(ModifyAttributesActivity activity) {
        try {
            java.lang.reflect.Field field = ModifyAttributesActivity.class.getDeclaredField("mFields");
            field.setAccessible(true);
            return (Map<String, IControl>) field.get(activity);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    private static void save(ModifyAttributesActivity activity) {
        PopupMenu menu = new PopupMenu(activity, new View(activity));
        activity.onOptionsItemSelected(menu.getMenu().add(0, R.id.menu_apply, 0, "Save"));
    }

    private static void await(java.util.function.BooleanSupplier ready) throws Exception {
        long deadline = SystemClock.uptimeMillis() + 20000;
        while (!ready.getAsBoolean() && SystemClock.uptimeMillis() < deadline) Thread.sleep(50);
        assertTrue("Timed out waiting for form", ready.getAsBoolean());
    }

    private static void dismissDialog(GISApplication app, String message) throws Exception {
        await(() -> {
            AccessibilityNodeInfo root = InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation().getRootInActiveWindow();
            return root != null && !root.findAccessibilityNodeInfosByText(message).isEmpty();
        });
        clickDialogButton(app.getString(android.R.string.ok));
    }

    private static void clickDialogButton(String caption) throws Exception {
        await(() -> {
            AccessibilityNodeInfo root = InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation().getRootInActiveWindow();
            return root != null && root.findAccessibilityNodeInfosByText(caption).stream()
                    .anyMatch(AccessibilityNodeInfo::isClickable);
        });
        AccessibilityNodeInfo root = InstrumentationRegistry.getInstrumentation()
                .getUiAutomation().getRootInActiveWindow();
        for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByText(caption)) {
            if (node.isClickable()) {
                assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_CLICK));
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                return;
            }
        }
        fail("Dialog has no button: " + caption);
    }

    private static JSONArray choice(String field, String caption) throws Exception {
        JSONObject attrs = new JSONObject().put("field", field).put("last", false)
                .put("input_search", false).put("allow_adding_values", false)
                .put("values", new JSONArray()
                        .put(new JSONObject().put("name", "Нет значения").put("alias", "Нет значения").put("default", true))
                        .put(new JSONObject().put("name", "Chosen").put("alias", "Выбрано"))
                        .put(new JSONObject().put("name", "не применимо").put("alias", "не применимо")));
        return new JSONArray().put(new JSONObject().put("type", "text_label")
                .put("attributes", new JSONObject().put("text", caption)))
                .put(new JSONObject().put("type", "combobox").put("attributes", attrs));
    }

    private static class Fixture implements AutoCloseable {
        final GISApplication app = (GISApplication) InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getApplicationContext();
        final MapContentProviderHelper map = (MapContentProviderHelper) app.getMap();
        final File form = new File(app.getCacheDir(), "required-" + UUID.randomUUID() + ".json");
        final File photo = new File(app.getCacheDir(), "required-" + UUID.randomUUID() + ".jpg");
        final VectorLayerUI layer;

        Fixture(boolean required) throws Exception {
            assertNull("Use an isolated emulator without a user's draft", FeatureFormDraftStore.load(app));
            assertNotNull(map);
            layer = new VectorLayerUI(app, new File(map.getPath(), "required_"
                    + UUID.randomUUID().toString().replace("-", "")));
            layer.setName("Required fields test");
            map.addLayer(layer);
            layer.create(GeoConstants.GTPoint, Arrays.asList(
                    new Field(GeoConstants.FTString, "auditor", "Аудитор", required),
                    new Field(GeoConstants.FTString, "contractor", "Подрядчик", required)));
            layer.setIsEditable(true);
            JSONArray first = choice("auditor", "Аудитор");
            first.put(new JSONObject().put("type", "photo").put("attributes", new JSONObject()));
            write(new JSONArray().put(new JSONObject().put("type", "tabs")
                    .put("attributes", new JSONObject()).put("pages", new JSONArray()
                            .put(new JSONObject().put("caption", "Аудит").put("default", true).put("elements", first))
                            .put(new JSONObject().put("caption", "Организация")
                                    .put("elements", choice("contractor", "Подрядчик"))))));
        }

        void write(JSONArray value) throws Exception {
            Files.write(form.toPath(), value.toString().getBytes(StandardCharsets.UTF_8));
        }

        Intent intent(long featureId) {
            GeoPoint point = new GeoPoint(10, 20);
            point.setCRS(GeoConstants.CRS_WEB_MERCATOR);
            return new Intent(app, FormBuilderModifyAttributesActivity.class)
                    .putExtra(ConstantsUI.KEY_LAYER_ID, layer.getId())
                    .putExtra(ConstantsUI.KEY_FEATURE_ID, featureId)
                    .putExtra(ConstantsUI.KEY_GEOMETRY, point)
                    .putExtra(ConstantsUI.KEY_GEOMETRY_CHANGED, true)
                    .putExtra(ConstantsUI.KEY_FORM_PATH, form);
        }

        @Override public void close() {
            FeatureFormDraftStore.clear(app);
            assertTrue(layer.delete(false));
            map.save();
            form.delete();
            photo.delete();
        }
    }

    @Test public void placeholderBlocksInsertAndInactiveTabKeepsDraftGeometryAndPhotos() throws Exception {
        try (Fixture f = new Fixture(true)) {
            Bitmap bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888);
            try (FileOutputStream out = new FileOutputStream(f.photo)) {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out));
            } finally { bitmap.recycle(); }
            try (ActivityScenario<FormBuilderModifyAttributesActivity> scenario =
                         ActivityScenario.launch(f.intent(Constants.NOT_FOUND))) {
                scenario.onActivity(activity -> {
                    Map<String, IControl> fields = controls(activity);
                    assertEquals("Нет значения", fields.get("auditor").getValue());
                    for (IControl control : fields.values()) if (control instanceof PhotoGallery)
                        ((PhotoGallery) control).restorePendingPhotoPaths(
                                Collections.singletonList(f.photo.getAbsolutePath()));
                    assertTrue(((View) fields.get("auditor")).getContentDescription().toString().contains("Аудитор"));
                    save(activity);
                });
                dismissDialog(f.app, f.app.getString(R.string.form_required_fields_title));
                assertEquals(0, f.layer.getSqliteTableRowCount());
                FeatureFormDraftStore.Snapshot draft = FeatureFormDraftStore.load(f.app);
                assertNotNull(draft);
                assertEquals(Constants.NOT_FOUND, draft.featureId);
                assertNotNull(draft.geometryWkt);
                assertEquals(Collections.singletonList(f.photo.getAbsolutePath()), draft.photoPaths);
                scenario.recreate();
                scenario.onActivity(activity -> {
                    assertEquals("Нет значения", controls(activity).get("auditor").getValue());
                    ((Spinner) controls(activity).get("auditor")).setSelection(1);
                    save(activity);
                });
                dismissDialog(f.app, f.app.getString(R.string.form_required_fields_title));
                assertEquals(0, f.layer.getSqliteTableRowCount());
                AtomicBoolean secondTab = new AtomicBoolean();
                await(() -> {
                    scenario.onActivity(activity -> {
                        android.view.ViewGroup root = activity.findViewById(R.id.controls_list);
                        for (int i = 0; i < root.getChildCount(); i++)
                            if (root.getChildAt(i) instanceof Tabs)
                                secondTab.set(Integer.valueOf(1).equals(((Tabs) root.getChildAt(i)).getValue()));
                    });
                    return secondTab.get();
                });
                scenario.onActivity(activity -> {
                    assertFalse(activity.isFinishing());
                    ((Spinner) controls(activity).get("contractor")).setSelection(2);
                    save(activity);
                });
                await(() -> scenario.getState() == androidx.lifecycle.Lifecycle.State.DESTROYED
                        && FeatureFormDraftStore.load(f.app) == null);
                assertEquals(1, f.layer.getSqliteTableRowCount());
                android.database.Cursor row = f.layer.query(null, null, null, null, null);
                try {
                    assertTrue(row.moveToFirst());
                    assertEquals("Chosen", row.getString(row.getColumnIndexOrThrow("auditor")));
                    assertEquals("не применимо", row.getString(row.getColumnIndexOrThrow("contractor")));
                    assertEquals(1, f.layer.getAttachMap(Long.toString(
                            row.getLong(row.getColumnIndexOrThrow(Constants.FIELD_ID)))).size());
                } finally { row.close(); }
            }
        }
    }

    @Test public void optionalPlaceholderStillSavesWithoutChangingItsStoredValue() throws Exception {
        try (Fixture f = new Fixture(false);
             ActivityScenario<FormBuilderModifyAttributesActivity> scenario =
                     ActivityScenario.launch(f.intent(Constants.NOT_FOUND))) {
            scenario.onActivity(RequiredFieldsTest::save);
            await(() -> scenario.getState() == androidx.lifecycle.Lifecycle.State.DESTROYED
                    && f.layer.getSqliteTableRowCount() == 1 && FeatureFormDraftStore.load(f.app) == null);
            assertEquals("Нет значения", android.database.DatabaseUtils.stringForQuery(
                    f.map.getDatabase(false), "SELECT auditor FROM " + f.layer.getPath().getName(), null));
        }
    }

    @Test public void existingRequiredValueOmittedFromFormIsPreservedButNewFeatureIsBlocked() throws Exception {
        try (Fixture f = new Fixture(true)) {
            f.write(choice("auditor", "Аудитор"));
            ContentValues values = new ContentValues();
            values.put(Constants.FIELD_GEOM, new GeoPoint(10, 20).toBlob());
            values.put("auditor", "Chosen");
            values.put("contractor", "Historical contractor");
            long id = f.layer.insertAddChanges(values);
            try (ActivityScenario<FormBuilderModifyAttributesActivity> scenario =
                         ActivityScenario.launch(f.intent(id))) {
                scenario.onActivity(RequiredFieldsTest::save);
                await(() -> scenario.getState() == androidx.lifecycle.Lifecycle.State.DESTROYED
                        && FeatureFormDraftStore.load(f.app) == null);
                assertEquals("Historical contractor", android.database.DatabaseUtils.stringForQuery(
                        f.map.getDatabase(false), "SELECT contractor FROM " + f.layer.getPath().getName(), null));
            }
            try (ActivityScenario<FormBuilderModifyAttributesActivity> scenario =
                         ActivityScenario.launch(f.intent(Constants.NOT_FOUND))) {
                scenario.onActivity(activity -> {
                    ((Spinner) controls(activity).get("auditor")).setSelection(1);
                    save(activity);
                });
                dismissDialog(f.app, f.app.getString(R.string.form_required_field_unavailable));
                assertEquals(1, f.layer.getSqliteTableRowCount());
                scenario.onActivity(activity -> assertFalse(activity.isFinishing()));
            }
        }
    }

    @Test public void backSaveRejectsClearedExistingChoiceWithoutUpdatingTheRecord() throws Exception {
        try (Fixture f = new Fixture(true)) {
            ContentValues values = new ContentValues();
            values.put(Constants.FIELD_GEOM, new GeoPoint(10, 20).toBlob());
            values.put("auditor", "Chosen");
            values.put("contractor", "Chosen");
            long id = f.layer.insertAddChanges(values);
            try (ActivityScenario<FormBuilderModifyAttributesActivity> scenario =
                         ActivityScenario.launch(f.intent(id))) {
                scenario.onActivity(activity -> {
                    ((Spinner) controls(activity).get("auditor")).setSelection(0);
                    activity.getOnBackPressedDispatcher().onBackPressed();
                });
                clickDialogButton(f.app.getString(R.string.save));
                dismissDialog(f.app, f.app.getString(R.string.form_required_fields_title));
                assertEquals("Chosen", android.database.DatabaseUtils.stringForQuery(
                        f.map.getDatabase(false), "SELECT auditor FROM " + f.layer.getPath().getName(), null));
                scenario.onActivity(activity -> {
                    assertFalse(activity.isFinishing());
                    ((Spinner) controls(activity).get("auditor")).setSelection(1);
                    save(activity);
                });
                await(() -> scenario.getState() == androidx.lifecycle.Lifecycle.State.DESTROYED
                        && FeatureFormDraftStore.load(f.app) == null);
                assertEquals(1, f.layer.getSqliteTableRowCount());
            }
        }
    }

    @Test public void standardFormRejectsWhitespaceAndPlaceholderButAcceptsNotApplicable() throws Exception {
        try (Fixture f = new Fixture(true)) {
            Intent intent = f.intent(Constants.NOT_FOUND).setClass(f.app, ModifyAttributesActivity.class);
            intent.removeExtra(ConstantsUI.KEY_FORM_PATH);
            try (ActivityScenario<ModifyAttributesActivity> scenario = ActivityScenario.launch(intent)) {
                for (String empty : new String[]{" \t\u00a0\u2003", " \u00a0нЕт зНаЧеНиЯ\t"}) {
                    scenario.onActivity(activity -> {
                        ((android.widget.EditText) controls(activity).get("auditor")).setText(empty);
                        ((android.widget.EditText) controls(activity).get("contractor")).setText("не применимо");
                        save(activity);
                    });
                    dismissDialog(f.app, f.app.getString(R.string.form_required_fields_title));
                    assertEquals(0, f.layer.getSqliteTableRowCount());
                }
                scenario.onActivity(activity -> {
                    ((android.widget.EditText) controls(activity).get("auditor")).setText("Chosen");
                    save(activity);
                });
                await(() -> scenario.getState() == androidx.lifecycle.Lifecycle.State.DESTROYED
                        && FeatureFormDraftStore.load(f.app) == null);
                assertEquals(1, f.layer.getSqliteTableRowCount());
                assertEquals("не применимо", android.database.DatabaseUtils.stringForQuery(
                        f.map.getDatabase(false), "SELECT contractor FROM " + f.layer.getPath().getName(), null));
            }
        }
    }

    @Test public void requiredDependentChoiceIsCheckedByItsOwnField() throws Exception {
        try (Fixture f = new Fixture(true)) {
            JSONObject option = new JSONObject().put("name", "Chosen").put("alias", "Роль").put("default", true)
                    .put("values", new JSONArray()
                            .put(new JSONObject().put("name", "Нет значения").put("alias", "Нет значения").put("default", true))
                            .put(new JSONObject().put("name", "Chosen").put("alias", "Сотрудник")));
            f.write(new JSONArray().put(new JSONObject().put("type", "double_combobox")
                    .put("attributes", new JSONObject().put("field_level1", "auditor")
                            .put("field_level2", "contractor").put("last", false)
                            .put("values", new JSONArray().put(option)))));
            try (ActivityScenario<FormBuilderModifyAttributesActivity> scenario =
                         ActivityScenario.launch(f.intent(Constants.NOT_FOUND))) {
                scenario.onActivity(RequiredFieldsTest::save);
                dismissDialog(f.app, f.app.getString(R.string.form_required_fields_title));
                assertEquals(0, f.layer.getSqliteTableRowCount());
                scenario.onActivity(activity -> {
                    com.nextgis.maplibui.formcontrol.DoubleCombobox control =
                            (com.nextgis.maplibui.formcontrol.DoubleCombobox) controls(activity).get("auditor");
                    ((Spinner) control.getFieldView("contractor")).setSelection(1);
                    save(activity);
                });
                await(() -> scenario.getState() == androidx.lifecycle.Lifecycle.State.DESTROYED
                        && FeatureFormDraftStore.load(f.app) == null);
                assertEquals(1, f.layer.getSqliteTableRowCount());
            }
        }
    }
}
