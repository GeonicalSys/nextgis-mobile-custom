package com.nextgis.mobile.reliability;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.CheckBox;
import android.widget.Spinner;
import android.widget.TextView;
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
import com.nextgis.maplibui.mapui.VectorLayerUI;
import com.nextgis.maplibui.util.ConstantsUI;
import com.nextgis.maplibui.util.FeatureFormDraftStore;
import com.nextgis.maplibui.util.FormFieldLayout;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import static org.junit.Assert.*;

/** Real native rendering with synthetic offline values; do not run on a working phone. */
@RunWith(AndroidJUnit4.class)
public class FormAppearanceTest {
    @SuppressWarnings("unchecked") private static Map<String,IControl> controls(ModifyAttributesActivity activity) {
        try {
            java.lang.reflect.Field field = ModifyAttributesActivity.class.getDeclaredField("mFields");
            field.setAccessible(true); return (Map<String,IControl>) field.get(activity);
        } catch (Exception error) { throw new AssertionError(error); }
    }
    private static final class Fixture implements AutoCloseable {
        final GISApplication app = (GISApplication) InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
        final MapContentProviderHelper map = (MapContentProviderHelper) app.getMap();
        final VectorLayerUI layer;
        final File form, meta;
        final String theme = PreferenceManager.getDefaultSharedPreferences(app).getString("theme", "light");
        Fixture() throws Exception {
            assertNull("Use an isolated emulator", FeatureFormDraftStore.load(app));
            layer = new VectorLayerUI(app, new File(map.getPath(), "appearance_" + UUID.randomUUID().toString().replace("-", "")));
            layer.setName("Аудит подрядчика"); map.addLayer(layer);
            layer.create(GeoConstants.GTPoint, Arrays.asList(new Field(GeoConstants.FTString,"contractor","Подрядчик",true),
                    new Field(GeoConstants.FTString,"auditor","Аудитор",true), new Field(GeoConstants.FTInteger,"check","Средства защиты"),
                    new Field(GeoConstants.FTString,"comment","Комментарий к замечанию")));
            layer.setIsEditable(true); map.save();
            form = new File(layer.getPath(),"933_form.json"); meta = new File(layer.getPath(),"933_ngfp_meta.json");
            Files.write(form.toPath(), """
                [{"type":"tabs","pages":[{"caption":"Данные","elements":[
                  {"type":"text_label","attributes":{"text":"Участники аудита"}},
                  {"type":"text_label","attributes":{"text":"Подрядчик"}},
                  {"type":"combobox","attributes":{"field":"contractor","values":[
                    {"name":"Компания с длинным названием","alias":"ООО «Северная лесная транспортная компания»","default":true},
                    {"name":"Другая компания","alias":"Другая компания"}]}},
                  {"type":"text_label","attributes":{"text":"Аудитор"}},
                  {"type":"combobox","attributes":{"field":"auditor","values":[
                    {"name":"Нет значения","alias":"Нет значения","default":true},
                    {"name":"Иванов Александр Александрович","alias":"Иванов Александр Александрович"}]}},
                  {"type":"text_label","attributes":{"text":"I. Безопасность работ"}},
                  {"type":"checkbox","attributes":{"field":"check","text":"Работники используют необходимые средства индивидуальной защиты","init_value":true}},
                  {"type":"text_edit","attributes":{"field":"comment","text":"","max_string_count":3,"only_figures":false}}
                ]},{"caption":"Выводы","elements":[{"type":"text_label","attributes":{"text":"Проверьте данные и сохраните аудит"}}]}]}]
                """.getBytes(StandardCharsets.UTF_8));
            Files.write(meta.toPath(), """
                {"lisa_form_rules":{"schema_version":2,
                  "required":[{"field":"comment","when":{"field":"check","op":"eq","value":false}}],
                  "visible":[{"field":"comment","when":{"field":"check","op":"eq","value":false}}]}}
                """.getBytes(StandardCharsets.UTF_8));
        }
        Intent intent() {
            GeoPoint point = new GeoPoint(100,200); point.setCRS(GeoConstants.CRS_WEB_MERCATOR);
            return new Intent(app,FormBuilderModifyAttributesActivity.class).putExtra(ConstantsUI.KEY_LAYER_ID,layer.getId())
                    .putExtra(ConstantsUI.KEY_FEATURE_ID,(long)Constants.NOT_FOUND).putExtra(ConstantsUI.KEY_GEOMETRY,point)
                    .putExtra(ConstantsUI.KEY_GEOMETRY_CHANGED,true).putExtra(ConstantsUI.KEY_FORM_PATH,form).putExtra(ConstantsUI.KEY_META_PATH,meta);
        }
        @Override public void close() {
            FeatureFormDraftStore.clear(app); assertTrue(layer.delete(false)); map.save();
            PreferenceManager.getDefaultSharedPreferences(app).edit().putString("theme",theme).commit();
        }
    }
    private static void screenshot(GISApplication app, String name) throws Exception {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        SystemClock.sleep(300); // Let the emulator present the requested layout, not the preceding frame.
        Bitmap bitmap = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(bitmap);
        File folder = new File(app.getFilesDir(), "form-appearance-qa"); assertTrue(folder.isDirectory() || folder.mkdirs());
        try (FileOutputStream stream = new FileOutputStream(new File(folder, name+".png"))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,stream));
        } finally { bitmap.recycle(); }
    }
    @Test public void clearFieldsHavePermanentCaptionsWrappedValuesAndReadableThemes() throws Exception {
        try (Fixture f = new Fixture()) {
            for (String theme : new String[]{"light", "dark"}) {
                PreferenceManager.getDefaultSharedPreferences(f.app).edit().putString("theme",theme).commit();
                try (ActivityScenario<FormBuilderModifyAttributesActivity> s = ActivityScenario.launch(f.intent())) {
                    s.onActivity(a -> {
                        Spinner company = (Spinner) controls(a).get("contractor");
                        FormFieldLayout.FieldContainer container = FormFieldLayout.container(company);
                        assertNotNull(container);
                        TextView label = (TextView) container.getChildAt(0);
                        TextView value = (TextView) company.getSelectedView();
                        assertEquals("Подрядчик *",label.getText().toString());
                        assertNotEquals("Caption and value have distinct hierarchy", label.getCurrentTextColor(),value.getCurrentTextColor());
                        assertTrue(value.getTextSize() > label.getTextSize());
                        assertTrue("Long names wrap in the closed picker", value.getLineCount() >= 2);
                        assertTrue(company.getHeight() >= FormFieldLayout.dp(company,56));
                        assertEquals(View.GONE, FormFieldLayout.container((View) controls(a).get("comment")).getVisibility());
                        assertTrue(a.findViewById(R.id.form_save).isShown());
                    });
                    screenshot(f.app, "clear-fields-"+theme);
                    s.onActivity(a -> ((CheckBox)controls(a).get("check")).setChecked(false));
                    InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                    s.onActivity(a -> {
                        View comment = (View) controls(a).get("comment");
                        assertEquals(View.VISIBLE, FormFieldLayout.container(comment).getVisibility());
                        assertEquals("Комментарий к замечанию *", ((TextView) FormFieldLayout.container(comment).getChildAt(0)).getText().toString());
                    });
                    screenshot(f.app, "clear-fields-"+theme+"-comment");
                }
                FeatureFormDraftStore.clear(f.app);
            }
        }
    }
    @Test public void standardFieldsKeepValuesThroughRotationAndDurableRecoveryInsidePresentationContainers() throws Exception {
        try (Fixture f = new Fixture()) {
            Intent intent = f.intent().setClass(f.app,ModifyAttributesActivity.class);
            intent.removeExtra(ConstantsUI.KEY_FORM_PATH); intent.removeExtra(ConstantsUI.KEY_META_PATH);
            try (ActivityScenario<ModifyAttributesActivity> s = ActivityScenario.launch(intent)) {
                s.onActivity(a -> {
                    ((android.widget.EditText)controls(a).get("contractor")).setText("Keep contractor");
                    ((android.widget.EditText)controls(a).get("auditor")).setText("Keep auditor");
                });
                s.recreate();
                s.onActivity(a -> {
                    assertEquals("Keep contractor",controls(a).get("contractor").getValue());
                    assertEquals("Keep auditor",controls(a).get("auditor").getValue());
                });
            }
            assertNotNull(FeatureFormDraftStore.load(f.app));
            try (ActivityScenario<ModifyAttributesActivity> s = ActivityScenario.launch(intent
                    .putExtra(FeatureFormDraftStore.KEY_APPLY_FORM_DRAFT,true))) {
                s.onActivity(a -> {
                    assertEquals("Keep contractor",controls(a).get("contractor").getValue());
                    assertEquals("Keep auditor",controls(a).get("auditor").getValue());
                });
            }
            assertEquals(0,f.layer.getSqliteTableRowCount());
        }
    }
    @Test public void footerSaveUsesTheSameRequiredGateAsToolbarAndBack() throws Exception {
        try (Fixture f = new Fixture(); ActivityScenario<FormBuilderModifyAttributesActivity> s = ActivityScenario.launch(f.intent())) {
            s.onActivity(a -> a.findViewById(R.id.form_save).performClick());
            long deadline = SystemClock.uptimeMillis()+20000;
            while (FeatureFormDraftStore.load(f.app) == null && SystemClock.uptimeMillis()<deadline) Thread.sleep(40);
            assertNotNull(FeatureFormDraftStore.load(f.app));
            assertEquals(0,f.layer.getSqliteTableRowCount());
            // Dialog acknowledgement is performed through accessibility, as with the other native form tests.
            android.view.accessibility.AccessibilityNodeInfo root = null;
            while (SystemClock.uptimeMillis()<deadline) {
                root = InstrumentationRegistry.getInstrumentation().getUiAutomation().getRootInActiveWindow();
                if (root != null && !root.findAccessibilityNodeInfosByText(f.app.getString(R.string.form_required_fields_title)).isEmpty()) break;
                Thread.sleep(40);
            }
            assertNotNull(root);
            assertFalse(root.findAccessibilityNodeInfosByText(f.app.getString(R.string.form_required_fields_title)).isEmpty());
            for (android.view.accessibility.AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByText(f.app.getString(android.R.string.ok)))
                if (node.isClickable()) node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK);
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            java.util.concurrent.atomic.AtomicBoolean available = new java.util.concurrent.atomic.AtomicBoolean();
            deadline = SystemClock.uptimeMillis()+20000;
            do {
                s.onActivity(a -> available.set(a.findViewById(R.id.form_save).isEnabled()));
                if (!available.get()) Thread.sleep(40);
            } while (!available.get() && SystemClock.uptimeMillis()<deadline);
            assertTrue("Save is available after the first worker finishes", available.get());
            s.onActivity(a -> {
                ((Spinner)controls(a).get("auditor")).setSelection(1);
                assertEquals("Иванов Александр Александрович",controls(a).get("auditor").getValue());
                assertEquals("Компания с длинным названием",controls(a).get("contractor").getValue());
                a.findViewById(R.id.form_save).performClick();
            });
            deadline = SystemClock.uptimeMillis()+20000;
            while (s.getState()!=androidx.lifecycle.Lifecycle.State.DESTROYED && SystemClock.uptimeMillis()<deadline) Thread.sleep(40);
            if (s.getState()!=androidx.lifecycle.Lifecycle.State.DESTROYED) screenshot(f.app,"footer-save-error");
            assertEquals(androidx.lifecycle.Lifecycle.State.DESTROYED,s.getState());
            assertEquals(1,f.layer.getSqliteTableRowCount());
        }
    }
}
