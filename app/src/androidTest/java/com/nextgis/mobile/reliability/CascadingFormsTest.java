package com.nextgis.mobile.reliability;

import android.content.ContentValues;
import android.content.Intent;
import android.os.SystemClock;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.Spinner;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.nextgis.maplib.datasource.Field;
import com.nextgis.maplib.datasource.GeoPoint;
import com.nextgis.maplib.forms.CascadingLists;
import com.nextgis.maplib.map.MapContentProviderHelper;
import com.nextgis.maplib.util.Constants;
import com.nextgis.maplib.util.GeoConstants;
import com.nextgis.maplibui.GISApplication;
import com.nextgis.maplibui.R;
import com.nextgis.maplibui.activity.FormBuilderModifyAttributesActivity;
import com.nextgis.maplibui.activity.ModifyAttributesActivity;
import com.nextgis.maplibui.api.IControl;
import com.nextgis.maplibui.mapui.VectorLayerUI;
import com.nextgis.maplibui.util.CascadingFormController;
import com.nextgis.maplibui.util.ConstantsUI;
import com.nextgis.maplibui.util.ControlHelper;
import com.nextgis.maplibui.util.FeatureFormDraftStore;
import com.nextgis.maplibui.util.RequiredFieldUi;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import static org.junit.Assert.*;

/** Actual NGFP controls, durable draft, SQLite and recreation on an isolated offline emulator. */
@RunWith(AndroidJUnit4.class)
public class CascadingFormsTest {
    private static void write(File file,String text) throws Exception {
        Files.write(file.toPath(),text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    @SuppressWarnings("unchecked") private static Map<String, IControl> controls(ModifyAttributesActivity activity) {
        try {
            java.lang.reflect.Field field = ModifyAttributesActivity.class.getDeclaredField("mFields");
            field.setAccessible(true); return (Map<String,IControl>)field.get(activity);
        } catch (Exception error) { throw new AssertionError(error); }
    }
    private static Spinner spinner(FormBuilderModifyAttributesActivity activity, String name) {
        return (Spinner) RequiredFieldUi.fieldView(new Field(GeoConstants.FTString, name, name), controls(activity));
    }
    private static void save(ModifyAttributesActivity activity) {
        PopupMenu menu = new PopupMenu(activity, new View(activity));
        activity.onOptionsItemSelected(menu.getMenu().add(0, R.id.menu_apply, 0, "Save"));
    }
    private static void await(java.util.function.BooleanSupplier ready) throws Exception {
        long deadline = SystemClock.uptimeMillis()+20000;
        while (!ready.getAsBoolean() && SystemClock.uptimeMillis()<deadline) Thread.sleep(40);
        assertTrue("Form did not reach expected state",ready.getAsBoolean());
    }
    private static void choose(ActivityScenario<FormBuilderModifyAttributesActivity> scenario, String field, int index) {
        scenario.onActivity(a -> spinner(a,field).setSelection(index));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }
    private static JSONObject rule(String field,String table,String key,String value,String... filters) throws Exception {
        JSONArray conditions=new JSONArray();
        for (int i=0;i<filters.length;i+=2)
            conditions.put(new JSONObject().put("column",filters[i]).put("field",filters[i+1]));
        return new JSONObject().put("field",field).put("table",table).put("key",key)
                .put("value",value).put("label",value).put("filters",conditions);
    }
    private static JSONObject definition() throws Exception {
        JSONObject json=new JSONObject("""
            {"schema_version":1,"tables":{
            "contractors":[{"id":"c1","name":"Company A"},{"id":"c2","name":"Company B"}],
            "employees":[
              {"id":"e1","cid":"c1","pid":"p1","role":"Driver","name":"Alex"},
              {"id":"e2","cid":"c2","pid":"p1","role":"Driver","name":"Bob"},
              {"id":"e3","cid":"c1","pid":"p2","role":"Master","name":"Chris"}]},"fields":[]}
            """);
        JSONArray fields=json.getJSONArray("fields");
        fields.put(rule("contractor","contractors","id","name"));
        for (int i=1;i<=2;i++) {
            fields.put(rule("jobtitle"+i,"employees","pid","role","cid","contractor"));
            fields.put(rule(i==1?"eployee1":"employee2","employees","id","name","cid","contractor","pid","jobtitle"+i));
        }
        return json;
    }
    private static final class Fixture implements AutoCloseable {
        final GISApplication app=(GISApplication)InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
        final MapContentProviderHelper map=(MapContentProviderHelper)app.getMap();
        final File form=new File(app.getCacheDir(),"cascade-"+UUID.randomUUID()+".json");
        final File meta=new File(app.getCacheDir(),"cascade-"+UUID.randomUUID()+"-meta.json");
        final VectorLayerUI layer;
        Fixture(boolean required) throws Exception {
            assertNull("Test requires isolated emulator",FeatureFormDraftStore.load(app));
            layer=new VectorLayerUI(app,new File(map.getPath(),"cascade_"+UUID.randomUUID().toString().replace("-","")));
            layer.setName("Cascade test");map.addLayer(layer);
            ArrayList<Field> fields=new ArrayList<>();
            for (String name:new String[]{"contractor","jobtitle1","eployee1","jobtitle2","employee2"})
                fields.add(new Field(GeoConstants.FTString,name,name,required && !name.endsWith("2")));
            layer.create(GeoConstants.GTPoint,fields);layer.setIsEditable(true);map.save();
            JSONArray elements=new JSONArray().put(new JSONObject().put("type","combobox")
                    .put("attributes",new JSONObject().put("field","contractor").put("last",true).put("values",new JSONArray())));
            for (int i=1;i<=2;i++) elements.put(new JSONObject().put("type","double_combobox")
                    .put("attributes",new JSONObject().put("field_level1","jobtitle"+i)
                            .put("field_level2",i==1?"eployee1":"employee2").put("last",true).put("values",new JSONArray())));
            write(form,new JSONArray().put(new JSONObject().put("type","tabs")
                    .put("pages",new JSONArray().put(new JSONObject().put("caption","Lists").put("elements",elements)))).toString());
            write(meta,new JSONObject().put(CascadingLists.META_KEY,definition()).toString());
        }
        Intent intent(long id) {
            GeoPoint point=new GeoPoint(100,200);point.setCRS(GeoConstants.CRS_WEB_MERCATOR);
            return new Intent(app,FormBuilderModifyAttributesActivity.class).putExtra(ConstantsUI.KEY_LAYER_ID,layer.getId())
                    .putExtra(ConstantsUI.KEY_FEATURE_ID,id).putExtra(ConstantsUI.KEY_GEOMETRY,point)
                    .putExtra(ConstantsUI.KEY_GEOMETRY_CHANGED,true).putExtra(ConstantsUI.KEY_FORM_PATH,form)
                    .putExtra(ConstantsUI.KEY_META_PATH,meta);
        }
        long existing() throws Exception {
            ContentValues values=new ContentValues();values.put(Constants.FIELD_GEOM,new GeoPoint(100,200).toBlob());
            values.put("contractor","Company A");values.put("jobtitle1","Driver");values.put("eployee1","Alex");
            values.put("jobtitle2","Driver");values.put("employee2","Alex");return layer.insertAddChanges(values);
        }
        @Override public void close() {
            FeatureFormDraftStore.clear(app);assertTrue(layer.delete(false));map.save();form.delete();meta.delete();
        }
    }
    @Test public void threeLevelListsAndBothBranchesResetWithoutAutoSelecting() throws Exception {
        try (Fixture f=new Fixture(false);ActivityScenario<FormBuilderModifyAttributesActivity> s=ActivityScenario.launch(f.intent(Constants.NOT_FOUND))) {
            s.onActivity(a -> { assertNull(a.getCascadingLists().value("contractor"));assertFalse(spinner(a,"jobtitle1").isEnabled()); });
            choose(s,"contractor",1);choose(s,"jobtitle1",1);choose(s,"eployee1",1);
            choose(s,"jobtitle2",1);choose(s,"employee2",1);
            s.onActivity(a -> assertEquals("Alex",a.getCascadingLists().value("eployee1")));
            choose(s,"contractor",2);
            s.onActivity(a -> { assertNull(a.getCascadingLists().value("jobtitle1"));assertNull(a.getCascadingLists().value("employee2")); });
            choose(s,"jobtitle1",1);
            s.onActivity(a -> {assertNull(a.getCascadingLists().value("eployee1"));assertEquals(2,spinner(a,"eployee1").getCount());});
            choose(s,"eployee1",1);s.onActivity(a -> assertEquals("Bob",a.getCascadingLists().value("eployee1")));
        }
    }
    @Test public void clearingExistingParentWritesSqlNullForAllChildren() throws Exception {
        try (Fixture f=new Fixture(false)) {
            long id=f.existing();
            try (ActivityScenario<FormBuilderModifyAttributesActivity> s=ActivityScenario.launch(f.intent(id))) {
                choose(s,"contractor",2);s.onActivity(CascadingFormsTest::save);
                await(() -> s.getState()==androidx.lifecycle.Lifecycle.State.DESTROYED);
                try (android.database.Cursor c=f.layer.query(null,null,null,null,null)) {
                    assertTrue(c.moveToFirst());assertEquals("Company B",c.getString(c.getColumnIndexOrThrow("contractor")));
                    for (String name:new String[]{"jobtitle1","eployee1","jobtitle2","employee2"}) assertTrue(c.isNull(c.getColumnIndexOrThrow(name)));
                }
            }
        }
    }
    @Test public void pinnedDefinitionAndKeysSurviveRotationAndDurableDraft() throws Exception {
        try (Fixture f=new Fixture(true)) {
            try (ActivityScenario<FormBuilderModifyAttributesActivity> s=ActivityScenario.launch(f.intent(Constants.NOT_FOUND))) {
                choose(s,"contractor",1);choose(s,"jobtitle1",1);choose(s,"eployee1",1);
                write(f.meta,new JSONObject().put(CascadingLists.META_KEY,definition().put("schema_version",99)).toString());
                s.recreate();s.onActivity(a -> assertEquals("Alex",a.getCascadingLists().value("eployee1")));
            }
            FeatureFormDraftStore.Snapshot draft=FeatureFormDraftStore.load(f.app);assertNotNull(draft);
            assertTrue(FeatureFormDraftStore.controlStateToBundle(draft).containsKey(CascadingFormController.PIN));
            try (ActivityScenario<FormBuilderModifyAttributesActivity> s=ActivityScenario.launch(f.intent(Constants.NOT_FOUND)
                    .putExtra(FeatureFormDraftStore.KEY_APPLY_FORM_DRAFT,true))) {
                s.onActivity(a -> { assertEquals("Alex",a.getCascadingLists().value("eployee1")); save(a); });
                await(() -> s.getState()==androidx.lifecycle.Lifecycle.State.DESTROYED);
                assertEquals(1,f.layer.getSqliteTableRowCount());
            }
        }
    }
    @Test public void removedHistoricalValuesArePreservedOnSave() throws Exception {
        try (Fixture f=new Fixture(false)) {
            long id=f.existing();ContentValues update=new ContentValues();update.put("contractor","Former company");
            update.put("jobtitle1","Former role");update.put("eployee1","Former employee");
            f.layer.updateAddChanges(update,id);
            try (ActivityScenario<FormBuilderModifyAttributesActivity> s=ActivityScenario.launch(f.intent(id))) {
                s.onActivity(a -> { assertEquals("Former employee",a.getCascadingLists().value("eployee1"));save(a); });
                await(() -> s.getState()==androidx.lifecycle.Lifecycle.State.DESTROYED);
                assertEquals("Former employee",f.layer.getFeature(id).getFieldValueAsString("eployee1"));
            }
        }
    }
    @Test public void mismatchedNewDraftIsBlockedBeforeSqliteInsert() throws Exception {
        try (Fixture f=new Fixture(false)) {
            FeatureFormDraftStore.Snapshot draft=new FeatureFormDraftStore.Snapshot();draft.layerId=f.layer.getId();
            draft.featureId=Constants.NOT_FOUND;draft.mapPath=f.map.getPath().getAbsolutePath();
            android.os.Bundle state=new android.os.Bundle();
            state.putString(ControlHelper.getSavedStateKey("contractor"),"Company A");
            state.putString(ControlHelper.getSavedStateKey("jobtitle1"),"Driver");
            state.putString(ControlHelper.getSavedStateKey("eployee1"),"Bob");
            FeatureFormDraftStore.putControlStateFromBundle(draft,state);assertTrue(FeatureFormDraftStore.save(f.app,draft));
            try (ActivityScenario<FormBuilderModifyAttributesActivity> s=ActivityScenario.launch(f.intent(Constants.NOT_FOUND)
                    .putExtra(FeatureFormDraftStore.KEY_APPLY_FORM_DRAFT,true))) {
                s.onActivity(CascadingFormsTest::save);
                await(() -> {
                    android.view.accessibility.AccessibilityNodeInfo root=InstrumentationRegistry.getInstrumentation().getUiAutomation().getRootInActiveWindow();
                    return root!=null&&!root.findAccessibilityNodeInfosByText(f.app.getString(R.string.form_cascade_invalid_title)).isEmpty();
                });
                assertEquals(0,f.layer.getSqliteTableRowCount());assertNotNull(FeatureFormDraftStore.load(f.app));
            }
        }
    }
    @Test public void unsupportedDefinitionBlocksSaveAfterRotation() throws Exception {
        try (Fixture f=new Fixture(false)) {
            write(f.meta,new JSONObject().put(CascadingLists.META_KEY,definition().put("schema_version",99)).toString());
            try (ActivityScenario<FormBuilderModifyAttributesActivity> s=ActivityScenario.launch(f.intent(Constants.NOT_FOUND))) {
                s.recreate();s.onActivity(CascadingFormsTest::save);
                await(() -> {
                    android.view.accessibility.AccessibilityNodeInfo root=InstrumentationRegistry.getInstrumentation().getUiAutomation().getRootInActiveWindow();
                    return root!=null&&!root.findAccessibilityNodeInfosByText(f.app.getString(R.string.form_cascade_unavailable)).isEmpty();
                });
                assertEquals(0,f.layer.getSqliteTableRowCount());assertNotNull(FeatureFormDraftStore.load(f.app));
            }
        }
    }
}
