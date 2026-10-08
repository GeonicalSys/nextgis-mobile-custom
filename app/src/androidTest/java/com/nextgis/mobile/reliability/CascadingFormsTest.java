package com.nextgis.mobile.reliability;

import android.content.ContentValues;
import android.content.Intent;
import android.app.Instrumentation;
import android.os.SystemClock;
import android.view.View;
import android.view.MotionEvent;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.Spinner;
import com.google.android.material.tabs.TabLayout;
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
import com.nextgis.maplibui.formcontrol.Tabs;
import com.nextgis.maplibui.formcontrol.Sign;
import com.nextgis.maplibui.util.CascadingFormController;
import com.nextgis.maplibui.util.ConstantsUI;
import com.nextgis.maplibui.util.ControlHelper;
import com.nextgis.maplibui.util.FeatureFormDraftStore;
import com.nextgis.maplibui.util.LayerUtil;
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
    private static void checkDefaultFormLaunch(String prefix) throws Exception {
        Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
        try (Fixture f=new Fixture(false)) {
            File syncedForm=new File(f.layer.getPath(),prefix+ConstantsUI.FILE_FORM);
            File syncedMeta=new File(f.layer.getPath(),prefix+"ngfp_meta.json");
            Files.copy(f.form.toPath(),syncedForm.toPath());
            Files.copy(f.meta.toPath(),syncedMeta.toPath());
            try (ActivityScenario<FormBuilderModifyAttributesActivity> host=ActivityScenario.launch(f.intent(Constants.NOT_FOUND))) {
                Instrumentation.ActivityMonitor monitor=instrumentation.addMonitor(
                        FormBuilderModifyAttributesActivity.class.getName(),null,false);
                FormBuilderModifyAttributesActivity opened=null;
                try {
                    host.onActivity(a -> LayerUtil.showEditForm(f.layer,a,Constants.NOT_FOUND,
                            new GeoPoint(100,200),-1));
                    opened=(FormBuilderModifyAttributesActivity)instrumentation.waitForMonitorWithTimeout(monitor,20000);
                    assertNotNull("Default form was not opened",opened);
                    instrumentation.waitForIdleSync();
                    FormBuilderModifyAttributesActivity activity=opened;
                    instrumentation.runOnMainSync(() -> {
                        assertEquals(syncedForm,activity.getIntent().getSerializableExtra(ConstantsUI.KEY_FORM_PATH));
                        assertEquals("The matching metadata must reach the actual form launch",syncedMeta,
                                activity.getIntent().getSerializableExtra(ConstantsUI.KEY_META_PATH));
                        assertNotNull(activity.getCascadingLists());
                        assertNull(activity.getCascadingLists().value("contractor"));
                        assertFalse(spinner(activity,"jobtitle1").isEnabled());
                    });
                } finally {
                    if (opened!=null) {
                        FormBuilderModifyAttributesActivity activity=opened;
                        instrumentation.runOnMainSync(activity::finish);
                        instrumentation.waitForIdleSync();
                    }
                    instrumentation.removeMonitor(monitor);
                }
            }
        }
    }
    @Test public void defaultFormLaunchLoadsSyncedNumberedMetadata() throws Exception {
        checkDefaultFormLaunch("933_");
    }
    @Test public void defaultFormLaunchLoadsUnprefixedMetadata() throws Exception {
        checkDefaultFormLaunch("");
    }
    private static void navigationForm(Fixture fixture) throws Exception {
        JSONArray form=new JSONArray(new String(Files.readAllBytes(fixture.form.toPath()),java.nio.charset.StandardCharsets.UTF_8));
        JSONArray pages=form.getJSONObject(0).getJSONArray("pages");
        JSONArray first=pages.getJSONObject(0).getJSONArray("elements");
        for (int i=0;i<40;i++) first.put(new JSONObject().put("type","text_label")
                .put("attributes",new JSONObject().put("text","Audit item "+i)));
        pages.put(new JSONObject().put("caption","Next").put("elements",new JSONArray()
                .put(new JSONObject().put("type","text_label").put("attributes",new JSONObject().put("text","Swipe here")))
                .put(new JSONObject().put("type","signature").put("attributes",new JSONObject()))));
        write(fixture.form,form.toString());
    }
    private static Tabs tabs(FormBuilderModifyAttributesActivity activity) {
        android.view.ViewGroup root=activity.findViewById(R.id.controls_list);
        for (int i=0;i<root.getChildCount();i++) if (root.getChildAt(i) instanceof Tabs) return (Tabs)root.getChildAt(i);
        throw new AssertionError("Missing tabs");
    }
    private static TabLayout header(FormBuilderModifyAttributesActivity activity) {
        return (TabLayout)((android.view.ViewGroup)activity.findViewById(R.id.form_tabs_header)).getChildAt(0);
    }
    private static View signature(View root) {
        if (root instanceof Sign) return root;
        if (root instanceof android.view.ViewGroup) {
            android.view.ViewGroup group=(android.view.ViewGroup)root;
            for (int i=0;i<group.getChildCount();i++) {View found=signature(group.getChildAt(i));if(found!=null)return found;}
        }
        return null;
    }
    private static View caption(View root, String text) {
        if (root instanceof android.widget.TextView && text.contentEquals(((android.widget.TextView)root).getText())) return root;
        if (root instanceof android.view.ViewGroup) {
            android.view.ViewGroup group=(android.view.ViewGroup)root;
            for (int i=0;i<group.getChildCount();i++) { View found=caption(group.getChildAt(i),text); if(found!=null) return found; }
        }
        return null;
    }
    private static void swipe(ActivityScenario<FormBuilderModifyAttributesActivity> scenario, boolean left,
                              java.util.function.Function<FormBuilderModifyAttributesActivity,View> target) {
        float[] points=new float[3];
        scenario.onActivity(a -> {
            View view=target.apply(a);int[] location=new int[2];view.getLocationOnScreen(location);
            assertTrue("Swipe target must have a visible drawing area",view.isShown() && view.getWidth()>0 && view.getHeight()>0);
            points[0]=location[0]+view.getWidth()*(left?.85f:.15f);
            points[1]=location[0]+view.getWidth()*(left?.15f:.85f);
            points[2]=location[1]+view.getHeight()/2f;
        });
        Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
        long down=SystemClock.uptimeMillis();
        for (int i=0;i<=12;i++) {
            int action=i==0?MotionEvent.ACTION_DOWN:i==12?MotionEvent.ACTION_UP:MotionEvent.ACTION_MOVE;
            MotionEvent event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,
                    points[0]+(points[1]-points[0])*i/12,points[2],0);
            event.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
            instrumentation.sendPointerSync(event);event.recycle();SystemClock.sleep(12);
        }
        instrumentation.waitForIdleSync();
    }
    @Test public void pinnedTabsAndSwipesPreserveChoicesAndRecreation() throws Exception {
        try (Fixture f=new Fixture(false)) {
            navigationForm(f);
            try (ActivityScenario<FormBuilderModifyAttributesActivity> s=ActivityScenario.launch(f.intent(Constants.NOT_FOUND))) {
                choose(s,"contractor",1);choose(s,"jobtitle1",1);
                int[] original=new int[2];
                s.onActivity(a -> {header(a).getLocationOnScreen(original);ScrollView scroll=a.findViewById(R.id.form_scroll);scroll.scrollTo(0,scroll.getChildAt(0).getHeight());});
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                s.onActivity(a -> {
                    int[] scrolled=new int[2];header(a).getLocationOnScreen(scrolled);
                    assertEquals(original[1],scrolled[1]);assertTrue(header(a).isShown());
                    assertTrue(((ScrollView)a.findViewById(R.id.form_scroll)).getScrollY()>0);
                });
                swipe(s,true,a -> a.findViewById(R.id.form_scroll));
                s.onActivity(a -> {assertEquals(1,tabs(a).getValue());assertEquals(0,((ScrollView)a.findViewById(R.id.form_scroll)).getScrollY());});
                swipe(s,false,a -> caption(tabs(a).getPageLayouts().get(1),"Swipe here"));
                s.onActivity(a -> {assertEquals(0,tabs(a).getValue());assertEquals("Driver",a.getCascadingLists().value("jobtitle1"));});
                s.onActivity(a -> {ScrollView scroll=a.findViewById(R.id.form_scroll);scroll.scrollTo(0,scroll.getChildAt(0).getHeight());});
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                swipe(s,false,a -> a.findViewById(R.id.form_scroll));
                s.onActivity(a -> {assertEquals(0,tabs(a).getValue());header(a).getTabAt(1).select();});
                s.recreate();
                s.onActivity(a -> {assertEquals(1,tabs(a).getValue());assertTrue(header(a).isShown());assertEquals("Company A",a.getCascadingLists().value("contractor"));});
            }
        }
    }
    @Test public void signatureStrokeDoesNotSwipeFormTabs() throws Exception {
        try (Fixture f=new Fixture(false)) {
            navigationForm(f);
            try (ActivityScenario<FormBuilderModifyAttributesActivity> s=ActivityScenario.launch(f.intent(Constants.NOT_FOUND))) {
                s.onActivity(a -> header(a).getTabAt(1).select());
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                swipe(s,false,a -> signature(tabs(a).getPageLayouts().get(1)));
                s.onActivity(a -> {
                    assertEquals(1,tabs(a).getValue());android.os.Bundle state=new android.os.Bundle();
                    ((Sign)signature(tabs(a).getPageLayouts().get(1))).saveState(state);
                    assertTrue(state.containsKey("signature_strokes"));
                });
            }
        }
    }
    @Test public void managedLegacyPairsRenderAsSeparateFullWidthFields() throws Exception {
        try (Fixture f=new Fixture(false);ActivityScenario<FormBuilderModifyAttributesActivity> s=ActivityScenario.launch(f.intent(Constants.NOT_FOUND))) {
            choose(s,"contractor",1);
            s.onActivity(a -> {
                for(String name:new String[]{"jobtitle1","eployee1","jobtitle2","employee2"})
                    assertTrue("Each cascade field must be a normal independent combobox: "+name,
                            controls(a).get(name) instanceof com.nextgis.maplibui.formcontrol.Combobox);
                Spinner role=spinner(a,"jobtitle1"),person=spinner(a,"eployee1");
                int[] roleAt=new int[2],personAt=new int[2];role.getLocationOnScreen(roleAt);person.getLocationOnScreen(personAt);
                float density=a.getResources().getDisplayMetrics().density;
                assertTrue(role.getHeight()>=48*density);assertTrue(person.getHeight()>=48*density);
                assertTrue("Adjacent controls need a visible gap",personAt[1]-(roleAt[1]+role.getHeight())>=12*density);
                assertEquals(((View)role.getParent()).getWidth(),role.getWidth());
                assertEquals(role.getWidth(),person.getWidth());
            });
            choose(s,"jobtitle1",1);choose(s,"eployee1",1);s.recreate();
            s.onActivity(a -> assertEquals("Alex",a.getCascadingLists().value("eployee1")));
        }
    }
    @Test public void separatedPairsUseFormAliasesInsteadOfTechnicalLayerNames() throws Exception {
        try (Fixture f = new Fixture(true)) {
            JSONObject meta = new JSONObject(new String(Files.readAllBytes(f.meta.toPath()), java.nio.charset.StandardCharsets.UTF_8));
            meta.put("fields", new JSONArray()
                    .put(new JSONObject().put("keyname", "jobtitle1").put("display_name", "Должность 1"))
                    .put(new JSONObject().put("keyname", "eployee1").put("display_name", "Работник 1")));
            write(f.meta, meta.toString());
            try (ActivityScenario<FormBuilderModifyAttributesActivity> s = ActivityScenario.launch(f.intent(Constants.NOT_FOUND))) {
                s.onActivity(a -> {
                    for (String[] field : new String[][]{{"jobtitle1", "Должность 1"}, {"eployee1", "Работник 1"}}) {
                        View view = spinner(a, field[0]);
                        com.nextgis.maplibui.util.FormFieldLayout.FieldContainer container = com.nextgis.maplibui.util.FormFieldLayout.container(view);
                        assertNotNull(container);
                        android.widget.TextView caption = (android.widget.TextView)container.getChildAt(0);
                        assertEquals(field[1] + " *", caption.getText().toString());
                    }
                });
            }
        }
    }
    @Test public void swipesWorkAcrossSelectorsCheckboxCommentsAndBlankSpace() throws Exception {
        try(Fixture f=new Fixture(false)) {
            navigationForm(f);
            try(ActivityScenario<FormBuilderModifyAttributesActivity> s=ActivityScenario.launch(f.intent(Constants.NOT_FOUND))) {
                s.onActivity(a -> {
                    android.widget.LinearLayout page=(android.widget.LinearLayout)tabs(a).getPageLayouts().get(1);
                    android.widget.CheckBox check=new android.widget.CheckBox(a);check.setText("Check stays unchanged");check.setChecked(true);check.setTag("swipe-check");page.addView(check,1);
                    android.widget.EditText edit=new android.widget.EditText(a);edit.setText("Retained comment");edit.setSingleLine(false);edit.setMinLines(2);edit.setTag("swipe-comment");page.addView(edit,2);
                });
                choose(s,"contractor",1);choose(s,"jobtitle1",1);choose(s,"eployee1",1);
                for(int i=0;i<5;i++) {
                    swipe(s,true,a -> spinner(a,"contractor"));
                    s.onActivity(a -> assertEquals(1,tabs(a).getValue()));
                    swipe(s,false,a -> tabs(a).findViewWithTag("swipe-check"));
                    s.onActivity(a -> assertEquals(0,tabs(a).getValue()));
                    swipe(s,true,a -> spinner(a,"jobtitle1"));
                    swipe(s,false,a -> tabs(a).findViewWithTag("swipe-comment"));
                    s.onActivity(a -> assertEquals(0,tabs(a).getValue()));
                }
                s.onActivity(a -> header(a).getTabAt(1).select());
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                // The ScrollView centre is below the short page, outside Tabs' own bounds.
                s.onActivity(a -> ((android.widget.LinearLayout)tabs(a).getPageLayouts().get(1)).removeView(signature(tabs(a).getPageLayouts().get(1))));
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                swipe(s,false,a -> a.findViewById(R.id.form_scroll));
                s.onActivity(a -> {
                    assertEquals("Empty area belongs to page navigation too",0,tabs(a).getValue());
                    View page=tabs(a).getPageLayouts().get(1);
                    assertTrue(((android.widget.CheckBox)page.findViewWithTag("swipe-check")).isChecked());
                    assertEquals("Retained comment",((android.widget.EditText)page.findViewWithTag("swipe-comment")).getText().toString());
                    assertEquals("Alex",a.getCascadingLists().value("eployee1"));
                });
            }
        }
    }
    @Test public void verticalScrollAndSelectedCommentKeepTheirOwnGestures() throws Exception {
        try(Fixture f=new Fixture(false)) {
            navigationForm(f);
            try(ActivityScenario<FormBuilderModifyAttributesActivity> s=ActivityScenario.launch(f.intent(Constants.NOT_FOUND))) {
                float[] point=new float[2];
                s.onActivity(a -> {
                    View scroll=a.findViewById(R.id.form_scroll);int[] at=new int[2];scroll.getLocationOnScreen(at);
                    point[0]=at[0]+scroll.getWidth()/2f;point[1]=at[1]+scroll.getHeight()*.8f;
                });
                Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();long down=SystemClock.uptimeMillis();
                for(int i=0;i<=12;i++) {
                    MotionEvent event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),i==0?MotionEvent.ACTION_DOWN:i==12?MotionEvent.ACTION_UP:MotionEvent.ACTION_MOVE,point[0]+i,point[1]-i*20,0);
                    event.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);instrumentation.sendPointerSync(event);event.recycle();SystemClock.sleep(12);
                }
                instrumentation.waitForIdleSync();
                s.onActivity(a -> {
                    assertEquals(0,tabs(a).getValue());assertTrue(((ScrollView)a.findViewById(R.id.form_scroll)).getScrollY()>0);
                    android.widget.LinearLayout page=(android.widget.LinearLayout)tabs(a).getPageLayouts().get(1);
                    android.widget.EditText edit=new android.widget.EditText(a);edit.setSingleLine(false);edit.setMinLines(2);edit.setText("Selected comment remains");edit.setTag("selected-comment");page.addView(edit,0);
                    header(a).getTabAt(1).select();
                });
                instrumentation.waitForIdleSync();
                s.onActivity(a -> ((android.widget.EditText)tabs(a).findViewWithTag("selected-comment")).setSelection(0,8));
                swipe(s,false,a -> tabs(a).findViewWithTag("selected-comment"));
                s.onActivity(a -> {
                    assertEquals("Selecting text must not change tabs",1,tabs(a).getValue());
                    assertEquals("Selected comment remains",((android.widget.EditText)tabs(a).findViewWithTag("selected-comment")).getText().toString());
                });
            }
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
    @Test public void largeDictionaryUsesSmallBundleAndPinnedFileAfterRotation() throws Exception {
        try (Fixture f=new Fixture(false)) {
            JSONObject large=definition();
            large.getJSONObject("tables").getJSONArray("contractors").getJSONObject(0)
                    .put("notes",new String(new char[700000]).replace('\0','x'));
            write(f.meta,new JSONObject().put(CascadingLists.META_KEY,large).toString());
            try (ActivityScenario<FormBuilderModifyAttributesActivity> s=ActivityScenario.launch(f.intent(Constants.NOT_FOUND))) {
                choose(s,"contractor",1);
                s.onActivity(a -> {
                    android.os.Bundle state=new android.os.Bundle();a.getCascadingLists().saveState(state);
                    assertTrue(state.getString(CascadingFormController.PIN).matches("sha256:[0-9a-f]{64}"));
                    android.os.Parcel parcel=android.os.Parcel.obtain();
                    try {parcel.writeBundle(state);assertTrue(parcel.dataSize()<16384);} finally {parcel.recycle();}
                });
                write(f.meta,"{}");s.recreate();
                s.onActivity(a -> assertEquals("Company A",a.getCascadingLists().value("contractor")));
            }
        }
    }
    @Test public void corruptPinnedFileBlocksSaveAndRetainsOriginalDraftSelection() throws Exception {
        try (Fixture f=new Fixture(false);
             ActivityScenario<FormBuilderModifyAttributesActivity> s=ActivityScenario.launch(f.intent(Constants.NOT_FOUND))) {
            choose(s,"contractor",1);choose(s,"jobtitle1",1);choose(s,"eployee1",1);
            android.os.Bundle state=new android.os.Bundle();s.onActivity(a -> a.getCascadingLists().saveState(state));
            String pin=state.getString(CascadingFormController.PIN);
            write(new File(new File(f.layer.getPath(),"form_dependencies"),pin.substring(7)+".json"),"{}");
            s.recreate();s.onActivity(CascadingFormsTest::save);
            await(() -> {
                android.view.accessibility.AccessibilityNodeInfo root=InstrumentationRegistry.getInstrumentation().getUiAutomation().getRootInActiveWindow();
                return root!=null&&!root.findAccessibilityNodeInfosByText(f.app.getString(R.string.form_cascade_unavailable)).isEmpty();
            });
            assertEquals(0,f.layer.getSqliteTableRowCount());
            android.os.Bundle draft=FeatureFormDraftStore.controlStateToBundle(FeatureFormDraftStore.load(f.app));
            assertEquals(pin,draft.getString(CascadingFormController.PIN));
            assertEquals("Alex",draft.getString(ControlHelper.getSavedStateKey("eployee1")));
        }
    }
}
