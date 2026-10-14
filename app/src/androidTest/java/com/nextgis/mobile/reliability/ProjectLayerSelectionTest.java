package com.nextgis.mobile.reliability;

import android.app.Dialog;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.ListView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.nextgis.maplib.map.LayerOriginMetadata;
import com.nextgis.maplib.map.MapDrawable;
import com.nextgis.maplib.map.NGWVectorLayer;
import com.nextgis.maplib.map.VectorLayer;
import com.nextgis.maplibui.GISApplication;
import com.nextgis.maplibui.dialog.ChooseLayerDialog;
import com.nextgis.maplibui.mapui.NGWVectorLayerUI;
import com.nextgis.maplibui.mapui.VectorLayerUI;
import com.nextgis.mobile.activity.MainActivity;
import com.nextgis.mobile.fragment.MapFragment;
import com.nextgis.mobile.util.AppSettingsConstants;
import com.nextgis.mobile.util.VerificationEnvironment;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Real creation filtering and chooser using the permitted project's read-only policy snapshot. */
@RunWith(AndroidJUnit4.class)
public class ProjectLayerSelectionTest {
    @Test public void authorizedProjectOffersEveryEditableLayerThroughTheCreationAction() throws Exception {
        GISApplication app=(GISApplication)InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
        VerificationEnvironment.requireIsolated(app);
        String source=InstrumentationRegistry.getArguments().getString("testProjectFixture");
        assertNotNull("Select an authorized test project before this integration case",source);
        File fixture=new File(source).getCanonicalFile();
        assertEquals(app.getExternalFilesDir(null).getCanonicalFile(),fixture.getParentFile());
        JSONArray policies=new JSONObject(new String(Files.readAllBytes(fixture.toPath()),StandardCharsets.UTF_8)).getJSONArray("layers");
        PreferenceManager.getDefaultSharedPreferences(app).edit()
                .putBoolean(AppSettingsConstants.KEY_PREF_INTRO,true).putBoolean("battery_dont_show_pref",true)
                .putBoolean(com.nextgis.maplibui.util.SettingsConstantsUI.KEY_PREF_SHOW_GEO_DIALOG,false).commit();
        ArrayList<VectorLayer> created=new ArrayList<>();
        Set<String> expected=new HashSet<>();
        ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class);
        try {
            AtomicBoolean ready=new AtomicBoolean();
            long until=android.os.SystemClock.uptimeMillis()+30_000;
            while(!ready.get() && android.os.SystemClock.uptimeMillis()<until) {
                scenario.onActivity(activity -> ready.set(activity.getWindow().getDecorView().hasWindowFocus()
                        && !activity.getSupportFragmentManager().isStateSaved()
                        && activity.getMapFragment()!=null && activity.getMapFragment().getEditLayerOverlay()!=null));
                if(!ready.get())Thread.sleep(100);
            }
            assertTrue("Project verification map is ready",ready.get());
            scenario.onActivity(activity -> {
                try {
                    MapDrawable map=(MapDrawable)app.getMap();
                    for(int i=0;i<policies.length();i++) {
                        JSONObject policy=policies.getJSONObject(i);
                        File path=new File(map.getPath(),"project_chooser_"+UUID.randomUUID().toString().replace("-",""));
                        VectorLayer layer=policy.getBoolean("ngw") ? new NGWVectorLayerUI(app,path) : new VectorLayerUI(app,path);
                        layer.setName(policy.getString("name"));
                        layer.create(policy.getInt("geometry_type"),new ArrayList<>());
                        layer.setIsEditable(policy.getBoolean("is_editable"));
                        layer.setCollectorEditable(policy.getBoolean("collector_editable"));
                        if(layer instanceof NGWVectorLayer) {
                            NGWVectorLayer ngw=(NGWVectorLayer)layer;
                            ngw.setSyncDirection(policy.getInt("sync_direction"));
                            if(policy.getBoolean("managed"))ngw.setLayerOriginMetadata(LayerOriginMetadata.collectorLayer("verification:permitted",i,0));
                        }
                        assertEquals("Project edit policy: "+layer.getName(),policy.getBoolean("expected_editable"),layer.isEditingAllowed());
                        if(policy.getBoolean("expected_editable"))expected.add(layer.getName());
                        map.addLayer(layer); created.add(layer);
                    }
                    map.save();
                    assertTrue("A real project must exercise multiple choices",expected.size()>1);
                    java.lang.reflect.Method action=MapFragment.class.getDeclaredMethod("addNewGeometry");
                    action.setAccessible(true);action.invoke(activity.getMapFragment());
                    activity.getMapFragment().getChildFragmentManager().executePendingTransactions();
                } catch(Exception error) {throw new AssertionError(error);}
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            AtomicReference<Dialog> dialog=new AtomicReference<>();
            scenario.onActivity(activity -> {
                ChooseLayerDialog chooser=(ChooseLayerDialog)activity.getMapFragment().getChildFragmentManager().findFragmentByTag(ChooseLayerDialog.TAG);
                assertNotNull("The actual creation action opens the project layer chooser",chooser);
                dialog.set(chooser.requireDialog());
                ListView list=dialog.get().findViewById(com.nextgis.maplibui.R.id.listView);
                Set<String> actual=new HashSet<>();
                for(int i=0;i<list.getAdapter().getCount();i++)
                    actual.add(((com.nextgis.maplib.api.ILayer)list.getAdapter().getItem(i)).getName());
                assertEquals("Every editable project layer is offered; read-only layers are excluded",expected,actual);
                assertTrue("The real project chooser has more than a one-row viewport",list.getChildCount()>1);
                assertNotNull(dialog.get().findViewById(android.R.id.button2));
            });
            until=android.os.SystemClock.uptimeMillis()+15_000;
            while(!dialog.get().getWindow().getDecorView().hasWindowFocus() && android.os.SystemClock.uptimeMillis()<until)Thread.sleep(100);
            assertTrue(dialog.get().getWindow().getDecorView().hasWindowFocus());
            android.graphics.Rect bounds=new android.graphics.Rect();
            scenario.onActivity(activity -> {
                View cancel=dialog.get().findViewById(android.R.id.button2);
                int[] screen=new int[2];cancel.getLocationOnScreen(screen);
                bounds.set(screen[0],screen[1],screen[0]+cancel.getWidth(),screen[1]+cancel.getHeight());
            });
            long now=android.os.SystemClock.uptimeMillis();
            android.view.MotionEvent down=android.view.MotionEvent.obtain(now,now,0,bounds.centerX(),bounds.centerY(),0);
            android.view.MotionEvent up=android.view.MotionEvent.obtain(now,now+50,1,bounds.centerX(),bounds.centerY(),0);
            InstrumentationRegistry.getInstrumentation().sendPointerSync(down);InstrumentationRegistry.getInstrumentation().sendPointerSync(up);
            down.recycle();up.recycle();InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            assertFalse("The project chooser's Cancel action receives a physical touch",dialog.get().isShowing());
        } finally {
            scenario.onActivity(activity -> {
                androidx.fragment.app.Fragment fragment=activity.getMapFragment().getChildFragmentManager().findFragmentByTag(ChooseLayerDialog.TAG);
                if(fragment instanceof ChooseLayerDialog)((ChooseLayerDialog)fragment).dismissNow();
                for(VectorLayer layer:created)assertTrue(layer.delete(false));
                app.getMap().save();
            });
            scenario.close();
        }
    }
}
