package com.nextgis.mobile.reliability;

import android.content.ContentValues;
import android.os.Binder;
import android.os.Process;
import android.os.SystemClock;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.nextgis.maplib.datasource.Field;
import com.nextgis.maplib.datasource.GeoPoint;
import com.nextgis.maplib.map.CollectorProjectMetadata;
import com.nextgis.maplib.map.LayerOriginMetadata;
import com.nextgis.maplib.map.LayerGroup;
import com.nextgis.maplib.map.MapContentProviderHelper;
import com.nextgis.maplib.scripts.*;
import com.nextgis.maplib.util.Constants;
import com.nextgis.maplib.util.DatabaseContext;
import com.nextgis.maplib.util.GeoConstants;
import com.nextgis.maplibui.GISApplication;
import com.nextgis.maplibui.mapui.NGWVectorLayerUI;
import com.nextgis.maplibui.util.ProjectScriptClient;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import static org.junit.Assert.*;
import android.app.AlertDialog;
import android.content.Intent;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.Spinner;
import androidx.test.core.app.ActivityScenario;
import com.nextgis.maplibui.activity.ModifyAttributesActivity;
import com.nextgis.maplibui.activity.FormBuilderModifyAttributesActivity;
import com.nextgis.maplibui.api.IControl;
import com.nextgis.maplibui.util.ConstantsUI;
import com.nextgis.maplibui.util.FeatureFormDraftStore;
import com.nextgis.maplibui.R;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/** Real QuickJS/JNI/Binder/SQLite; synthetic local data only, never connected PostGIS writes. */
@RunWith(AndroidJUnit4.class)
public class ProjectScriptsTest {
    private final GISApplication app = (GISApplication) InstrumentationRegistry.getInstrumentation()
            .getTargetContext().getApplicationContext();
    private static final String[] ALIASES = {"audit_vyvozka", "audit_roads", "audit_logging", "audit_forestry",
            "audit_survey", "audit_office", "audit_security", "audit_transport", "audit_fire", "audit_terminal"};
    private byte[] input(String contractor, boolean isNew) throws Exception {
        return new JSONObject().put("event","before_save").put("layer",ALIASES[0]).put("isNew",isNew)
                .put("fields",new JSONObject().put("contractor",contractor)).toString().getBytes(StandardCharsets.UTF_8);
    }
    private byte[] asset(String name) throws Exception {
        try (InputStream in = InstrumentationRegistry.getInstrumentation().getContext().getAssets().open(name)) {
            return ScriptPackageStore.boundedRead(in);
        }
    }
    private byte[] run(String source, byte[] input, IProjectScriptHost host) throws Exception {
        return ProjectScriptClient.execute(app, ScriptProgram.wrap(source), input, host);
    }
    @Test public void isolatedUidUnicodeAndFreshContext() throws Exception {
        AtomicInteger uid = new AtomicInteger();
        IProjectScriptHost host = new IProjectScriptHost.Stub() {
            @Override public byte[] call(byte[] request) {
                uid.set(Binder.getCallingUid());
                return "{\"ok\":true,\"value\":{\"from\":\"2026-09-07\"}}".getBytes(StandardCharsets.UTF_8);
            }
        };
        String source = "function run(ctx) { ctx.time.monthWindow(); ctx.warn('unicode','Подрядчик 🌲');"
                + "if (typeof require !== 'undefined' || typeof fetch !== 'undefined' || typeof std !== 'undefined'"
                + " || typeof os !== 'undefined') throw Error('sandbox'); globalThis.leak = 42; }";
        ScriptRunResult first = new ScriptRunResult(run(source,input("Тест",true),host));
        assertEquals("Подрядчик 🌲",first.notices.get(0).message);
        assertNotEquals(Process.myUid(),uid.get());
        assertTrue(uid.get() > 0);
        assertTrue(new ScriptRunResult(run("function run(ctx) { if (typeof leak !== 'undefined') throw Error('leak'); }",
                input("Тест",true),host)).notices.isEmpty());
        assertNotNull(app.getMap());
    }
    @Test public void infiniteLoopAndMemoryExhaustionCannotHangMainProcess() throws Exception {
        IProjectScriptHost host = new IProjectScriptHost.Stub() { @Override public byte[] call(byte[] request) { return new byte[0]; } };
        for (String source : new String[]{"function run(ctx){ while(true){} }",
                "function run(ctx){const a=[];while(true)a.push(new Array(10000).fill('memory'));}"}) {
            long start = SystemClock.elapsedRealtime();
            byte[] reply = run(source,input("Тест",true),host);
            assertTrue("Sandbox deadline",SystemClock.elapsedRealtime()-start < 8000);
            assertThrows(java.io.IOException.class,() -> new ScriptRunResult(reply));
            assertNotNull(app.getMap());
        }
        assertTrue(new ScriptRunResult(run("function run(ctx){}",input("Тест",true),host)).notices.isEmpty());
    }
    private final class Fixture implements AutoCloseable {
        final MapContentProviderHelper map = (MapContentProviderHelper)app.getMap();
        final LayerGroup group;
        final CollectorProjectMetadata previousMetadata;
        final boolean atRoot;
        final String previousName;
        final List<NGWVectorLayerUI> layers = new ArrayList<>();
        final ScriptReference reference;
        final ScriptPackage pack;
        final String source;
        Fixture() throws Exception { this(false); }
        Fixture(boolean atRoot) throws Exception {
            this.atRoot=atRoot;
            previousMetadata=map.getCollectorProjectMetadata();
            previousName=map.getName();
            group=atRoot ? map : new LayerGroup(app,new java.io.File(map.getPath(),"scripts_"+UUID.randomUUID().toString().replace("-","")),app.getLayerFactory());
            group.setName("Synthetic project scripts test");
            if(!atRoot)map.addLayer(group);
            CollectorProjectMetadata metadata = CollectorProjectMetadata.create("scripts-test",999803,"Test",null);
            group.setCollectorProjectMetadata(metadata);
            JSONObject bindings = new JSONObject();
            for (int i=0;i<ALIASES.length;i++) {
                NGWVectorLayerUI layer = new NGWVectorLayerUI(app,new java.io.File(group.getPath(),"script_audit_"+UUID.randomUUID().toString().replace("-","")+"_"+i));
                layer.setName("Synthetic audit "+i);
                layer.setAccountName("scripts-test");
                layer.setRemoteId(100+i);
                layer.setLayerOriginMetadata(LayerOriginMetadata.collectorLayer(metadata.getProjectUid(),i,0));
                layer.setSyncType(Constants.SYNC_NONE);
                group.addLayer(layer);
                layer.create(GeoConstants.GTPoint,Arrays.asList(new Field(GeoConstants.FTString,"contractor","Подрядчик"),
                        new Field(GeoConstants.FTString,"issue","Замечания"),new Field(GeoConstants.FTDate,"date_audit","Дата")));
                layer.setIsEditable(true);
                layers.add(layer); bindings.put(ALIASES[i],100+i);
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try(ZipOutputStream zip = new ZipOutputStream(bytes)) {
                for(String name:new String[]{"manifest.json","scripts/prior_audits.js"}) {
                    zip.putNextEntry(new ZipEntry(name));zip.write(asset("project-scripts/contractor-audit/"+name));zip.closeEntry();
                }
            }
            reference = new ScriptReference(new JSONObject().put("schema_version",1).put("sha256",ScriptPackage.sha256(bytes.toByteArray()))
                    .put("version","1.0.0").put("resource_id",999804).put("feature_id",1).put("attachment_id",1)
                    .put("failure_policy","open").put("layer_bindings",bindings));
            ScriptPackageStore.install(group,reference,bytes.toByteArray());
            metadata.setScriptsState(reference.toString(),reference.toString(),"ready");
            assertTrue(group.save());assertTrue(map.save());
            pack = ScriptPackageStore.load(group,reference);
            source = new String(asset("project-scripts/contractor-audit/scripts/prior_audits.js"),StandardCharsets.UTF_8);
        }
        void seed(int index,String contractor,String issue,String date) throws Exception {
            ContentValues values = new ContentValues();
            GeoPoint point = new GeoPoint(10,20);point.setCRS(GeoConstants.CRS_WEB_MERCATOR);
            values.put(Constants.FIELD_GEOM,point.toBlob());values.put("contractor",contractor);values.put("issue",issue);
            values.put("date_audit",LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli());
            DatabaseContext.getDatabaseForLayer(layers.get(index),false).insertOrThrow(layers.get(index).getPath().getName(),null,values);
        }
        ProjectScriptHost host() { return new ProjectScriptHost(group,reference,pack,LocalDate.of(2026,10,7)); }
        ScriptRunResult check(String contractor,boolean isNew) throws Exception { return new ScriptRunResult(run(source,input(contractor,isNew),host())); }
        @Override public void close() {
            if(atRoot) { for(NGWVectorLayerUI layer:layers)layer.delete(false);map.setCollectorProjectMetadata(previousMetadata);map.setName(previousName); }
            else group.delete(false);
            map.save();
        }
    }
    @Test public void contractorHistoryIncludesMonthBoundaryAndOtherAuditType() throws Exception {
        try(Fixture f=new Fixture()) {
            f.seed(1,"Тестовый подрядчик","Да","2026-09-07");
            f.seed(0,"Другой подрядчик","Да","2026-10-07");
            assertEquals(1,f.check("Тестовый подрядчик",true).notices.size());
            assertTrue(f.check("Тестовый подрядчик",false).notices.isEmpty());
            assertTrue(f.check("Нет значения",true).notices.isEmpty());
            assertTrue(f.check("Не найден",true).notices.isEmpty());
            assertTrue(f.check("x' OR 1=1--",true).notices.isEmpty());
        }
    }
    @Test public void outsideMonthNoIssueAndFutureDatesDoNotWarn() throws Exception {
        try(Fixture f=new Fixture()) {
            f.seed(0,"Тест","Да","2026-09-06");f.seed(1,"Тест","Нет","2026-10-07");
            f.seed(2,"Тест","Да","2026-10-08");
            assertTrue(f.check("Тест",true).notices.isEmpty());
            f.seed(9,"Тест","Да","2026-10-07");
            assertEquals(1,f.check("Тест",true).notices.size());
        }
    }
    @Test public void brokerRejectsForeignOwnershipAndUnapprovedFields() throws Exception {
        try(Fixture f=new Fixture()) {
            JSONObject args = new JSONObject().put("layer",ALIASES[0]).put("fields",new JSONArray().put("contractor"))
                    .put("limit",1).put("where",new JSONArray().put(new JSONObject().put("field","contractor").put("op","eq").put("value","Тест")));
            JSONObject request = new JSONObject().put("method","gis.query").put("args",args);
            f.layers.get(0).setLayerOriginMetadata(LayerOriginMetadata.collectorLayer("foreign-project",0,0));
            assertFalse(new JSONObject(new String(f.host().call(request.toString().getBytes(StandardCharsets.UTF_8)),StandardCharsets.UTF_8)).getBoolean("ok"));
            args.put("layer",ALIASES[1]).put("fields",new JSONArray().put("secret"));
            assertFalse(new JSONObject(new String(f.host().call(request.toString().getBytes(StandardCharsets.UTF_8)),StandardCharsets.UTF_8)).getBoolean("ok"));
        }
    }
    private static Object member(Object target, Class<?> owner, String name) {
        try {
            java.lang.reflect.Field field = owner.getDeclaredField(name);
            field.setAccessible(true); return field.get(target);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private static AlertDialog notice(ModifyAttributesActivity activity) {
        Object controller = member(activity, ModifyAttributesActivity.class, "mProjectScripts");
        return controller == null ? null : (AlertDialog)member(controller,
                com.nextgis.maplibui.util.ProjectScriptFormController.class, "dialog");
    }
    @SuppressWarnings("unchecked")
    private static Spinner contractor(ModifyAttributesActivity activity) {
        return (Spinner)((Map<String,IControl>)member(activity, ModifyAttributesActivity.class, "mFields")).get("contractor");
    }
    private static void save(ModifyAttributesActivity activity) {
        PopupMenu menu = new PopupMenu(activity,new View(activity));
        activity.onOptionsItemSelected(menu.getMenu().add(0,R.id.menu_apply,0,"Save"));
    }
    private static void awaitDialog(ActivityScenario<FormBuilderModifyAttributesActivity> scenario) throws Exception {
        long until = SystemClock.elapsedRealtime()+15000;
        AtomicReference<Boolean> visible = new AtomicReference<>(false);
        do {
            scenario.onActivity(activity -> visible.set(notice(activity) != null && notice(activity).isShowing()));
            if (visible.get()) return;
            Thread.sleep(50);
        } while(SystemClock.elapsedRealtime()<until);
        fail("Project warning was not shown");
    }
    @Test public void formSelectionDraftPinAndSaveDecisionUseActualControls() throws Exception {
        assertNull("Use an isolated emulator",FeatureFormDraftStore.load(app));
        java.io.File form = java.io.File.createTempFile("scripts-form-",".json",app.getCacheDir());
        try(Fixture f=new Fixture(true)) {
            f.seed(1,"Тест","Да",LocalDate.now().toString());
            JSONArray choices = new JSONArray();
            for(String value:new String[]{"Нет значения","Тест","Другой"})
                choices.put(new JSONObject().put("name",value).put("alias",value).put("default",value.equals("Нет значения")));
            JSONArray elements = new JSONArray().put(new JSONObject().put("type","combobox")
                    .put("attributes",new JSONObject().put("field","contractor").put("last",false)
                            .put("allow_adding_values",false).put("input_search",false).put("values",choices)));
            Files.write(form.toPath(),elements.toString().getBytes(StandardCharsets.UTF_8));
            GeoPoint point = new GeoPoint(10,20);point.setCRS(GeoConstants.CRS_WEB_MERCATOR);
            Intent intent = new Intent(app,FormBuilderModifyAttributesActivity.class)
                    .putExtra(ConstantsUI.KEY_LAYER_ID,f.layers.get(0).getId())
                    .putExtra(ConstantsUI.KEY_FEATURE_ID,(long)Constants.NOT_FOUND)
                    .putExtra(ConstantsUI.KEY_GEOMETRY,point).putExtra(ConstantsUI.KEY_GEOMETRY_CHANGED,true)
                    .putExtra(ConstantsUI.KEY_FORM_PATH,form);
            try(ActivityScenario<FormBuilderModifyAttributesActivity> scenario=ActivityScenario.launch(intent)) {
                // Let listener installation finish, then change twice inside the debounce window.
                Thread.sleep(800);
                scenario.onActivity(activity -> { contractor(activity).setSelection(1);contractor(activity).setSelection(2); });
                Thread.sleep(800);
                scenario.onActivity(activity -> assertTrue(notice(activity)==null || !notice(activity).isShowing()));
                scenario.onActivity(activity -> contractor(activity).setSelection(1));
                awaitDialog(scenario);
                scenario.onActivity(activity -> {
                    String message=((android.widget.TextView)notice(activity).findViewById(android.R.id.message)).getText().toString();
                    assertTrue(message.contains("аудиты с замечаниями"));
                    notice(activity).getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                });
                // Remote rules changed, but an already-open form retains the verified reference.
                f.group.getCollectorProjectMetadata().setScriptsState(null,null,"disabled");
                scenario.recreate();
                awaitDialog(scenario);
                scenario.onActivity(activity -> {
                    assertEquals(f.reference.toString(),member(activity,ModifyAttributesActivity.class,"mScriptReferencePin"));
                    notice(activity).getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                    contractor(activity).setSelection(2);
                });
                Thread.sleep(800);
                f.seed(1,"Другой","Да",LocalDate.now().toString());
                scenario.onActivity(ProjectScriptsTest::save);
                awaitDialog(scenario);
                scenario.onActivity(activity -> notice(activity).getButton(AlertDialog.BUTTON_NEGATIVE).performClick());
                Thread.sleep(250);
                assertEquals(0,f.layers.get(0).getSqliteTableRowCount());
                assertEquals(f.reference.toString(),FeatureFormDraftStore.load(app).scriptReference);
                scenario.onActivity(ProjectScriptsTest::save);
                awaitDialog(scenario);
                scenario.onActivity(activity -> notice(activity).getButton(AlertDialog.BUTTON_POSITIVE).performClick());
                long until=SystemClock.elapsedRealtime()+10000;
                while(f.layers.get(0).getSqliteTableRowCount()==0 && SystemClock.elapsedRealtime()<until)Thread.sleep(50);
                assertEquals(1,f.layers.get(0).getSqliteTableRowCount());
            }
        } finally { FeatureFormDraftStore.clear(app);form.delete(); }
    }
    @Test public void corruptUpdateCannotReplaceVerifiedCache() throws Exception {
        try(Fixture f=new Fixture()) {
            assertThrows(java.io.IOException.class,() -> ScriptPackageStore.install(f.group,f.reference,"broken".getBytes(StandardCharsets.UTF_8)));
            assertEquals("1.0.0",ScriptPackageStore.load(f.group,f.reference).version);
            assertEquals(f.reference.toString(),f.group.getCollectorProjectMetadata().getScriptsActive());
        }
    }
}
