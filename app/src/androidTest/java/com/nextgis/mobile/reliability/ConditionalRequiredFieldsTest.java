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
                    new Field(GeoConstants.FTString,"comment","Comment",staticRequired)));
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
}
