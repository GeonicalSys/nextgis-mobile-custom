package com.nextgis.mobile.reliability;

import android.content.ContentValues;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.PopupMenu;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.nextgis.maplib.datasource.Field;
import com.nextgis.maplib.datasource.GeoPoint;
import com.nextgis.maplib.forms.ConditionalRequiredRules;
import com.nextgis.maplib.map.MapContentProviderHelper;
import com.nextgis.maplib.util.Constants;
import com.nextgis.maplib.util.GeoConstants;
import com.nextgis.maplibui.GISApplication;
import com.nextgis.maplibui.R;
import com.nextgis.maplibui.activity.FormBuilderModifyAttributesActivity;
import com.nextgis.maplibui.activity.ModifyAttributesActivity;
import com.nextgis.maplibui.api.IControl;
import com.nextgis.maplibui.mapui.VectorLayerUI;
import com.nextgis.maplibui.util.ConditionalRequiredController;
import com.nextgis.maplibui.util.ConstantsUI;
import com.nextgis.maplibui.util.FeatureFormDraftStore;
import com.nextgis.maplibui.util.FormFieldLayout;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import static org.junit.Assert.*;

/** Real offline NGFP forms/SQLite; never run on a user's device or connected project. */
@RunWith(AndroidJUnit4.class)
public class ConditionalRequiredFieldsTest {
    @SuppressWarnings("unchecked") private static Map<String,IControl> controls(ModifyAttributesActivity activity) {
        try {
            java.lang.reflect.Field field=ModifyAttributesActivity.class.getDeclaredField("mFields");
            field.setAccessible(true);return (Map<String,IControl>)field.get(activity);
        } catch (Exception error) { throw new AssertionError(error); }
    }
    private static void write(File file,String text) throws Exception { Files.write(file.toPath(),text.getBytes(StandardCharsets.UTF_8)); }
    private static void save(ModifyAttributesActivity activity) {
        PopupMenu menu=new PopupMenu(activity,new View(activity));
        activity.onOptionsItemSelected(menu.getMenu().add(0,R.id.menu_apply,0,"Save"));
    }
    private static void await(java.util.function.BooleanSupplier ready) throws Exception {
        long deadline=SystemClock.uptimeMillis()+20000;
        while (!ready.getAsBoolean()&&SystemClock.uptimeMillis()<deadline) Thread.sleep(40);
        assertTrue("Form did not reach expected state",ready.getAsBoolean());
    }
    private static void dialog(String text) throws Exception {
        await(() -> {
            AccessibilityNodeInfo root=InstrumentationRegistry.getInstrumentation().getUiAutomation().getRootInActiveWindow();
            return root!=null&&!root.findAccessibilityNodeInfosByText(text).isEmpty();
        });
    }
    private static void clickButton(String caption) throws Exception {
        await(() -> {
            AccessibilityNodeInfo root=InstrumentationRegistry.getInstrumentation().getUiAutomation().getRootInActiveWindow();
            return root!=null && root.findAccessibilityNodeInfosByText(caption).stream().anyMatch(AccessibilityNodeInfo::isClickable);
        });
        AccessibilityNodeInfo root=InstrumentationRegistry.getInstrumentation().getUiAutomation().getRootInActiveWindow();
        for (AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByText(caption)) if (node.isClickable()) {
            assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_CLICK));
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();return;
        }
        fail("Missing dialog button");
    }
    private static final class Fixture implements AutoCloseable {
        final GISApplication app=(GISApplication)InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
        final MapContentProviderHelper map=(MapContentProviderHelper)app.getMap();
        final VectorLayerUI layer;
        final File form,meta;
        Fixture(boolean staticRequired) throws Exception {
            assertNull("Use an isolated emulator",FeatureFormDraftStore.load(app));
            layer=new VectorLayerUI(app,new File(map.getPath(),"required_rule_"+UUID.randomUUID().toString().replace("-","")));
            layer.setName("Conditional requirement test");map.addLayer(layer);
            layer.create(GeoConstants.GTPoint,Arrays.asList(new Field(GeoConstants.FTInteger,"check","Safety check"),
                    new Field(GeoConstants.FTString,"comment","Comment",staticRequired),
                    new Field(GeoConstants.FTReal,"latitude","Latitude"), new Field(GeoConstants.FTReal,"longitude","Longitude")));
            layer.setIsEditable(true);map.save();form=new File(layer.getPath(),"933_form.json");meta=new File(layer.getPath(),"933_ngfp_meta.json");
            write(form,"""
                [{"type":"tabs","pages":[
                  {"caption":"Check","elements":[{"type":"checkbox","attributes":{"field":"check","text":"Safety check","init_value":true}}]},
                  {"caption":"Comment","elements":[
                    {"type":"text_edit","attributes":{"field":"comment","text":"","max_string_count":3,"only_figures":false}}]}]}]
                """);
            metadata("check",false);
        }
        void metadata(String field,boolean value) throws Exception {
            write(meta,new JSONObject().put(ConditionalRequiredRules.META_KEY,new JSONObject().put("schema_version",1)
                    .put("required",new JSONArray().put(new JSONObject().put("field","comment").put("label","Comment").put("when",
                            new JSONObject().put("field",field).put("op","eq").put("value",value))))).toString());
        }
        void visibility(String target, boolean value) throws Exception {
            JSONObject metadata = new JSONObject(new String(Files.readAllBytes(meta.toPath()), StandardCharsets.UTF_8));
            JSONObject rules = metadata.getJSONObject(ConditionalRequiredRules.META_KEY);
            rules.put("schema_version", 2).put("visible", new JSONArray().put(new JSONObject()
                    .put("field", target).put("when", new JSONObject().put("field", "check").put("op", "eq").put("value", value))));
            write(meta, metadata.toString());
        }
        Intent intent(long id) {
            GeoPoint point=new GeoPoint(100,200);point.setCRS(GeoConstants.CRS_WEB_MERCATOR);
            return new Intent(app,FormBuilderModifyAttributesActivity.class).putExtra(ConstantsUI.KEY_LAYER_ID,layer.getId())
                    .putExtra(ConstantsUI.KEY_FEATURE_ID,id).putExtra(ConstantsUI.KEY_GEOMETRY,point)
                    .putExtra(ConstantsUI.KEY_GEOMETRY_CHANGED,true).putExtra(ConstantsUI.KEY_FORM_PATH,form).putExtra(ConstantsUI.KEY_META_PATH,meta);
        }
        @Override public void close() {FeatureFormDraftStore.clear(app);assertTrue(layer.delete(false));map.save();}
    }
    @Test public void uncheckedItemRequiresInactiveCommentAndRetainsDraftBeforeWrite() throws Exception {
        try (Fixture f=new Fixture(false);ActivityScenario<FormBuilderModifyAttributesActivity> s=ActivityScenario.launch(f.intent(Constants.NOT_FOUND))) {
            s.onActivity(a -> {assertTrue(((CheckBox)controls(a).get("check")).isChecked());((CheckBox)controls(a).get("check")).setChecked(false);});
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            s.onActivity(a -> {
                assertTrue(((View)controls(a).get("comment")).getContentDescription().toString().contains("Comment"));save(a);
            });
            dialog(f.app.getString(R.string.form_required_fields_title));
            clickButton(f.app.getString(android.R.string.ok));
            assertEquals(0,f.layer.getSqliteTableRowCount());
            Bundle draft=FeatureFormDraftStore.controlStateToBundle(FeatureFormDraftStore.load(f.app));
            assertTrue(draft.getString(ConditionalRequiredController.PIN).matches("sha256:[0-9a-f]{64}"));
            s.onActivity(a -> {((EditText)controls(a).get("comment")).setText("Hazard explained");save(a);});
            await(() -> s.getState()==androidx.lifecycle.Lifecycle.State.DESTROYED);
            assertEquals(1,f.layer.getSqliteTableRowCount());
        }
    }
    @Test public void checkedItemIsOptionalButStaticRequiredCannotBeRelaxed() throws Exception {
        for (boolean staticRequired:new boolean[]{false,true}) {
            try (Fixture f=new Fixture(staticRequired);ActivityScenario<FormBuilderModifyAttributesActivity> s=ActivityScenario.launch(f.intent(Constants.NOT_FOUND))) {
                s.onActivity(ConditionalRequiredFieldsTest::save);
                if (staticRequired) {dialog(f.app.getString(R.string.form_required_fields_title));assertEquals(0,f.layer.getSqliteTableRowCount());}
                else {await(() -> s.getState()==androidx.lifecycle.Lifecycle.State.DESTROYED);assertEquals(1,f.layer.getSqliteTableRowCount());}
            }
        }
    }
    @Test public void backSaveUsesCurrentConditionAndChangingItRetainsComment() throws Exception {
        try (Fixture f=new Fixture(false);ActivityScenario<FormBuilderModifyAttributesActivity> s=ActivityScenario.launch(f.intent(Constants.NOT_FOUND))) {
            s.onActivity(a -> {((CheckBox)controls(a).get("check")).setChecked(false);a.getOnBackPressedDispatcher().onBackPressed();});
            clickButton(f.app.getString(R.string.save));
            dialog(f.app.getString(R.string.form_required_fields_title));clickButton(f.app.getString(android.R.string.ok));
            assertEquals(0,f.layer.getSqliteTableRowCount());
            s.onActivity(a -> {((EditText)controls(a).get("comment")).setText("Keep this explanation");((CheckBox)controls(a).get("check")).setChecked(true);});
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            s.onActivity(a -> {
                assertEquals("Keep this explanation",((EditText)controls(a).get("comment")).getText().toString());
                CharSequence description=((View)controls(a).get("comment")).getContentDescription();
                assertFalse(description!=null&&description.toString().contains("*"));
                a.getOnBackPressedDispatcher().onBackPressed();
            });
            clickButton(f.app.getString(R.string.save));
            await(() -> s.getState()==androidx.lifecycle.Lifecycle.State.DESTROYED);
            assertEquals(1,f.layer.getSqliteTableRowCount());
            try (android.database.Cursor cursor=f.layer.query(null,null,null,null,null)) {
                assertTrue(cursor.moveToFirst());assertEquals("Keep this explanation",cursor.getString(cursor.getColumnIndexOrThrow("comment")));
            }
        }
    }
    @Test public void rulePinSurvivesMetadataUpdateRotationAndDurableRecovery() throws Exception {
        try (Fixture f=new Fixture(false)) {
            try (ActivityScenario<FormBuilderModifyAttributesActivity> s=ActivityScenario.launch(f.intent(Constants.NOT_FOUND))) {
                s.onActivity(a -> ((CheckBox)controls(a).get("check")).setChecked(false));
            }
            f.metadata("check",true);
            try (ActivityScenario<FormBuilderModifyAttributesActivity> s=ActivityScenario.launch(f.intent(Constants.NOT_FOUND)
                    .putExtra(FeatureFormDraftStore.KEY_APPLY_FORM_DRAFT,true))) {
                s.recreate();s.onActivity(ConditionalRequiredFieldsTest::save);
                dialog(f.app.getString(R.string.form_required_fields_title));assertEquals(0,f.layer.getSqliteTableRowCount());
            }
        }
    }
    @Test public void corruptPinAndUnknownFieldBlockSaveAndKeepDraft() throws Exception {
        try (Fixture f=new Fixture(false)) {
            try (ActivityScenario<FormBuilderModifyAttributesActivity> s=ActivityScenario.launch(f.intent(Constants.NOT_FOUND))) {
                s.onActivity(a -> ((CheckBox)controls(a).get("check")).setChecked(false));
            }
            Bundle state=FeatureFormDraftStore.controlStateToBundle(FeatureFormDraftStore.load(f.app));
            String pin=state.getString(ConditionalRequiredController.PIN);
            write(new File(new File(f.layer.getPath(),"form_rules"),pin.substring(7)+".json"),"{}");
            try (ActivityScenario<FormBuilderModifyAttributesActivity> s=ActivityScenario.launch(f.intent(Constants.NOT_FOUND)
                    .putExtra(FeatureFormDraftStore.KEY_APPLY_FORM_DRAFT,true))) {
                s.onActivity(ConditionalRequiredFieldsTest::save);dialog(f.app.getString(R.string.form_rules_unavailable));
                assertEquals(0,f.layer.getSqliteTableRowCount());
                assertEquals(pin,FeatureFormDraftStore.controlStateToBundle(FeatureFormDraftStore.load(f.app)).getString(ConditionalRequiredController.PIN));
            }
        }
        try (Fixture f=new Fixture(false)) {
            f.metadata("unknown_field",false);
            try (ActivityScenario<FormBuilderModifyAttributesActivity> s=ActivityScenario.launch(f.intent(Constants.NOT_FOUND))) {
                s.onActivity(ConditionalRequiredFieldsTest::save);dialog(f.app.getString(R.string.form_rules_unavailable));assertEquals(0,f.layer.getSqliteTableRowCount());
            }
        }
    }
    private static View commentContainer(ModifyAttributesActivity activity) {
        View view = (View) controls(activity).get("comment");
        assertNotNull("Each input has a single caption/value container", FormFieldLayout.container(view));
        return FormFieldLayout.container(view);
    }
    @Test public void hiddenStaticRequiredFieldDoesNotBlockSaveButVisibleEmptyFieldDoes() throws Exception {
        for (boolean checked : new boolean[]{true, false}) {
            try (Fixture f = new Fixture(true)) {
                f.visibility("comment", false);
                try (ActivityScenario<FormBuilderModifyAttributesActivity> s = ActivityScenario.launch(f.intent(Constants.NOT_FOUND))) {
                    s.onActivity(a -> { ((CheckBox) controls(a).get("check")).setChecked(checked); save(a); });
                    if (checked) {
                        await(() -> s.getState() == androidx.lifecycle.Lifecycle.State.DESTROYED);
                        assertEquals(1, f.layer.getSqliteTableRowCount());
                    } else {
                        dialog(f.app.getString(R.string.form_required_fields_title));
                        assertEquals(0, f.layer.getSqliteTableRowCount());
                        clickButton(f.app.getString(android.R.string.ok));
                        s.onActivity(a -> {
                            assertEquals(View.VISIBLE, commentContainer(a).getVisibility());
                            assertTrue(((View) controls(a).get("comment")).isShown());
                            java.util.ArrayList<View> errors = new java.util.ArrayList<>();
                            commentContainer(a).findViewsWithText(errors, f.app.getString(R.string.form_fill_required), View.FIND_VIEWS_WITH_TEXT);
                            assertFalse("Inline error accompanies the border", errors.isEmpty());
                        });
                    }
                }
            }
        }
    }
    @Test public void hideShowKeepsTextAcrossRotationPinnedUpdateAndDurableRecovery() throws Exception {
        try (Fixture f = new Fixture(false)) {
            f.visibility("comment", false);
            try (ActivityScenario<FormBuilderModifyAttributesActivity> s = ActivityScenario.launch(f.intent(Constants.NOT_FOUND))) {
                s.onActivity(a -> {
                    assertEquals(View.GONE, commentContainer(a).getVisibility());
                    ((CheckBox) controls(a).get("check")).setChecked(false);
                });
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                s.onActivity(a -> {
                    assertEquals(View.VISIBLE, commentContainer(a).getVisibility());
                    ((EditText) controls(a).get("comment")).setText("Keep hidden text");
                    ((CheckBox) controls(a).get("check")).setChecked(true);
                });
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                s.onActivity(a -> assertEquals(View.GONE, commentContainer(a).getVisibility()));
                f.visibility("comment", true); // The open form and its draft keep the previous pin.
                s.recreate();
                s.onActivity(a -> {
                    assertEquals(View.GONE, commentContainer(a).getVisibility());
                    assertEquals("Keep hidden text", ((EditText) controls(a).get("comment")).getText().toString());
                });
            }
            try (ActivityScenario<FormBuilderModifyAttributesActivity> s = ActivityScenario.launch(f.intent(Constants.NOT_FOUND)
                    .putExtra(FeatureFormDraftStore.KEY_APPLY_FORM_DRAFT, true))) {
                s.onActivity(a -> {
                    assertEquals(View.GONE, commentContainer(a).getVisibility());
                    ((CheckBox) controls(a).get("check")).setChecked(false);
                });
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                s.onActivity(a -> {
                    assertEquals(View.VISIBLE, commentContainer(a).getVisibility());
                    assertEquals("Keep hidden text", ((EditText) controls(a).get("comment")).getText().toString());
                    ((CheckBox) controls(a).get("check")).setChecked(true);
                    a.getOnBackPressedDispatcher().onBackPressed();
                });
                clickButton(f.app.getString(R.string.save));
                await(() -> s.getState() == androidx.lifecycle.Lifecycle.State.DESTROYED);
                try (android.database.Cursor cursor = f.layer.query(null, null, null, null, null)) {
                    assertTrue(cursor.moveToFirst());
                    assertEquals("Keep hidden text", cursor.getString(cursor.getColumnIndexOrThrow("comment")));
                }
            }
        }
    }
    @Test public void visibilityOfNestedTabsAlsoHidesRequiredDescendantsAndPinnedHeader() throws Exception {
        try (Fixture f = new Fixture(true)) {
            write(f.form, """
                [{"type":"checkbox","attributes":{"field":"check","text":"Show details","init_value":true}},
                 {"type":"tabs","lisa_id":"details","pages":[{"caption":"Details","elements":[
                   {"type":"tabs","pages":[{"caption":"Nested","elements":[
                     {"type":"text_label","attributes":{"text":"Explanation"}},
                     {"type":"text_edit","attributes":{"field":"comment","text":"","max_string_count":3,"only_figures":false}}]}]}]}]}]
                """);
            JSONObject metadata = new JSONObject(new String(Files.readAllBytes(f.meta.toPath()), StandardCharsets.UTF_8));
            metadata.getJSONObject(ConditionalRequiredRules.META_KEY).put("schema_version", 2).put("visible", new JSONArray().put(new JSONObject()
                    .put("element", "details").put("when", new JSONObject().put("field", "check").put("op", "eq").put("value", false))));
            write(f.meta, metadata.toString());
            try (ActivityScenario<FormBuilderModifyAttributesActivity> s = ActivityScenario.launch(f.intent(Constants.NOT_FOUND))) {
                s.onActivity(a -> {
                    android.view.ViewGroup header = a.findViewById(R.id.form_tabs_header);
                    assertEquals(View.GONE, header.getChildAt(0).getVisibility());
                    save(a);
                });
                await(() -> s.getState() == androidx.lifecycle.Lifecycle.State.DESTROYED);
                assertEquals(1, f.layer.getSqliteTableRowCount());
            }
        }
    }
    @Test public void visibilityAppliesToReadOnlyFormsAndStandaloneExplanationElements() throws Exception {
        try (Fixture f = new Fixture(false)) {
            write(f.form, """
                [{"type":"text_label","lisa_id":"help","attributes":{"text":"Explain the issue"}},
                 {"type":"checkbox","attributes":{"field":"check","text":"Safety check","init_value":true}},
                 {"type":"text_edit","attributes":{"field":"comment","text":"","max_string_count":3,"only_figures":false}}]
                """);
            f.visibility("comment", false);
            JSONObject metadata = new JSONObject(new String(Files.readAllBytes(f.meta.toPath()), StandardCharsets.UTF_8));
            metadata.getJSONObject(ConditionalRequiredRules.META_KEY).getJSONArray("visible").put(new JSONObject()
                    .put("element", "help").put("when", new JSONObject().put("field", "check").put("op", "eq").put("value", false)));
            write(f.meta, metadata.toString());
            try (ActivityScenario<FormBuilderModifyAttributesActivity> s = ActivityScenario.launch(f.intent(Constants.NOT_FOUND)
                    .putExtra(ConstantsUI.KEY_VIEW_ONLY, true))) {
                s.onActivity(a -> {
                    assertEquals(View.GONE, commentContainer(a).getVisibility());
                    android.view.ViewGroup root = a.findViewById(R.id.controls_list);
                    boolean found = false;
                    for (int i=0; i<root.getChildCount(); i++) if ("help".equals(root.getChildAt(i).getTag(R.id.form_element_id))) {
                        found = true; assertEquals(View.GONE, root.getChildAt(i).getVisibility());
                    }
                    assertTrue(found);
                });
            }
        }
    }
    @Test public void missingOrDuplicateVisibilityElementAndUnknownFieldFailClosed() throws Exception {
        for (String target : new String[]{"missing", "duplicate", "unknown_field"}) {
            try (Fixture f = new Fixture(false)) {
                String extra = target.equals("duplicate") ? """
                    ,{"type":"text_label","lisa_id":"duplicate","attributes":{"text":"One"}},
                     {"type":"text_label","lisa_id":"duplicate","attributes":{"text":"Two"}}
                    """ : "";
                write(f.form, """
                    [{"type":"checkbox","attributes":{"field":"check","text":"Check","init_value":true}},
                     {"type":"text_edit","attributes":{"field":"comment","text":"Retain me","max_string_count":3,"only_figures":false}}
                    """ + extra + "]");
                write(f.meta, new JSONObject().put(ConditionalRequiredRules.META_KEY, new JSONObject().put("schema_version", 2)
                        .put("visible", new JSONArray().put(new JSONObject().put(target.equals("unknown_field") ? "field" : "element", target)
                                .put("when", new JSONObject().put("field", "check").put("op", "eq").put("value", false))))).toString());
                try (ActivityScenario<FormBuilderModifyAttributesActivity> s = ActivityScenario.launch(f.intent(Constants.NOT_FOUND))) {
                    s.onActivity(ConditionalRequiredFieldsTest::save);
                    dialog(f.app.getString(R.string.form_rules_unavailable));
                    assertEquals(0, f.layer.getSqliteTableRowCount());
                    assertNotNull(FeatureFormDraftStore.load(f.app));
                }
            }
        }
    }
    @Test public void coordinatesElementHasOneVisibilityTargetForBothControlsAtRootAndInTabs() throws Exception {
        for (boolean nested : new boolean[]{false,true}) {
            try (Fixture f = new Fixture(false)) {
                String elements = """
                    [{"type":"checkbox","attributes":{"field":"check","text":"Show coordinates","init_value":true}},
                     {"type":"coordinates","lisa_id":"position","attributes":{"field_lat":"latitude","field_long":"longitude"}}]
                    """;
                write(f.form, nested ? "[{\"type\":\"tabs\",\"pages\":[{\"caption\":\"Position\",\"elements\":"+elements+"}]}]" : elements);
                write(f.meta,"""
                    {"lisa_form_rules":{"schema_version":2,"visible":[
                      {"element":"position","when":{"field":"check","op":"eq","value":false}}]}}
                    """);
                try (ActivityScenario<FormBuilderModifyAttributesActivity> s = ActivityScenario.launch(f.intent(Constants.NOT_FOUND))) {
                    s.onActivity(a -> {
                        View latitude = (View) controls(a).get("latitude"), longitude = (View) controls(a).get("longitude");
                        assertNotNull(latitude); assertNotNull(longitude);
                        View group = (View) FormFieldLayout.container(latitude).getParent();
                        assertEquals("position",group.getTag(R.id.form_element_id));
                        assertSame(group,FormFieldLayout.container(longitude).getParent());
                        assertEquals(View.GONE,group.getVisibility());
                        ((CheckBox) controls(a).get("check")).setChecked(false);
                    });
                    InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                    s.onActivity(a -> {
                        assertTrue(((View) controls(a).get("latitude")).isShown());
                        assertTrue(((View) controls(a).get("longitude")).isShown());
                        save(a);
                    });
                    await(() -> s.getState()==androidx.lifecycle.Lifecycle.State.DESTROYED);
                    assertEquals(1,f.layer.getSqliteTableRowCount());
                }
            }
        }
    }
    @Test public void fieldAndElementVisibilityMustBothAllowShowingTheControl() throws Exception {
        try (Fixture f = new Fixture(true)) {
            write(f.form,"""
                [{"type":"checkbox","attributes":{"field":"check","text":"Check","init_value":true}},
                 {"type":"text_edit","lisa_id":"explanation","attributes":{"field":"comment","text":"","max_string_count":3,"only_figures":false}}]
                """);
            write(f.meta,"""
                {"lisa_form_rules":{"schema_version":2,"visible":[
                  {"field":"comment","when":{"field":"check","op":"eq","value":false}},
                  {"element":"explanation","when":{"field":"check","op":"eq","value":true}}]}}
                """);
            try (ActivityScenario<FormBuilderModifyAttributesActivity> s = ActivityScenario.launch(f.intent(Constants.NOT_FOUND))) {
                s.onActivity(a -> {
                    assertEquals(View.GONE,commentContainer(a).getVisibility());
                    ((CheckBox)controls(a).get("check")).setChecked(false);
                });
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                s.onActivity(a -> { assertEquals(View.GONE,commentContainer(a).getVisibility()); save(a); });
                await(() -> s.getState()==androidx.lifecycle.Lifecycle.State.DESTROYED);
                assertEquals(1,f.layer.getSqliteTableRowCount());
            }
        }
    }
}
