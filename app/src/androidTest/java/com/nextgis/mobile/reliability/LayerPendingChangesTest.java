package com.nextgis.mobile.reliability;

import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.FrameLayout;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.nextgis.maplib.datasource.Field;
import com.nextgis.maplib.datasource.GeoPoint;
import com.nextgis.maplib.datasource.ngw.SyncAdapter;
import com.nextgis.maplib.map.MapDrawable;
import com.nextgis.maplib.util.Constants;
import com.nextgis.maplib.util.DatabaseContext;
import com.nextgis.maplib.util.FeatureChanges;
import com.nextgis.maplib.util.GeoConstants;
import com.nextgis.maplibui.GISApplication;
import com.nextgis.maplibui.fragment.LayersListAdapter;
import com.nextgis.maplibui.mapui.NGWVectorLayerUI;
import com.nextgis.maplibui.util.ProjectOperationCoordinator;
import com.nextgis.maplibui.util.SharedUnderlayProjects;
import com.nextgis.mobile.R;
import com.nextgis.mobile.activity.MainActivity;
import com.nextgis.mobile.fragment.LayersFragment;
import com.nextgis.mobile.util.AppSettingsConstants;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Real drawer, worker and synthetic SQLite outbox; no account or network writes. */
@RunWith(AndroidJUnit4.class)
public class LayerPendingChangesTest {
    @org.junit.Before public void requireVerificationWorkspace() {
        com.nextgis.mobile.util.VerificationEnvironment.requireIsolated(
                androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getTargetContext());
    }

    private static LayersListAdapter adapter(MainActivity activity) {
        try {
            LayersFragment fragment=(LayersFragment)activity.getSupportFragmentManager().findFragmentById(R.id.layers);
            java.lang.reflect.Field field=LayersFragment.class.getDeclaredField("mListAdapter");
            field.setAccessible(true);return (LayersListAdapter)field.get(fragment);
        } catch (Exception error) {throw new AssertionError(error);}
    }
    private static int position(LayersListAdapter adapter,int id) {
        for(int i=0;i<adapter.getCount();i++) if(adapter.getItemId(i)==id)return i;
        throw new AssertionError("Layer missing from drawer");
    }
    private static boolean row(MainActivity activity,int id) {
        LayersListAdapter adapter=adapter(activity);
        View view=adapter.getView(position(adapter,id),null,new FrameLayout(activity));
        return view.findViewById(com.nextgis.maplibui.R.id.layer_pending_badge).getVisibility()==View.VISIBLE;
    }
    private static void await(ActivityScenario<MainActivity> scenario,java.util.function.Predicate<MainActivity> predicate) throws Exception {
        AtomicBoolean ready=new AtomicBoolean();long deadline=SystemClock.uptimeMillis()+30000;
        while(!ready.get()&&SystemClock.uptimeMillis()<deadline) {
            scenario.onActivity(a -> ready.set(predicate.test(a)));
            if(!ready.get())Thread.sleep(80);
        }
        assertTrue("Layer list did not reach expected state",ready.get());
    }
    private static NGWVectorLayerUI layer(GISApplication app,MapDrawable map) throws Exception {
        NGWVectorLayerUI layer=new NGWVectorLayerUI(app,new File(map.getPath(),"pending_"+UUID.randomUUID().toString().replace("-","")));
        layer.setName("Pending changes test");layer.setAccountName("isolated-pending-test");
        layer.setRemoteId(999803);layer.setSyncType(Constants.SYNC_ALL);map.addLayer(layer);
        layer.create(GeoConstants.GTPoint,Collections.singletonList(new Field(GeoConstants.FTString,"name","Name")));
        return layer;
    }
    @Test public void owningLayerAndSyncButtonTrackOutboxAndClearRecycledRows() throws Exception {
        GISApplication app=(GISApplication)InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
        SharedPreferences prefs=PreferenceManager.getDefaultSharedPreferences(app);
        boolean hadIntro=prefs.contains(AppSettingsConstants.KEY_PREF_INTRO),oldIntro=prefs.getBoolean(AppSettingsConstants.KEY_PREF_INTRO,false);
        assertTrue(prefs.edit().putBoolean(AppSettingsConstants.KEY_PREF_INTRO,true).commit());
        MapDrawable map=(MapDrawable)app.getMap();
        assertFalse("Test requires isolated emulator without real pending edits",map.isChanges());
        NGWVectorLayerUI changed=layer(app,map),clean=layer(app,map);map.save();
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            await(scenario,a -> adapter(a)!=null);
            scenario.onActivity(a -> a.findViewById(R.id.sync).setVisibility(View.VISIBLE));
            await(scenario,a -> !row(a,changed.getId())&&!row(a,clean.getId()));
            ContentValues values=new ContentValues();values.put(Constants.FIELD_GEOM,new GeoPoint(10,20).toBlob());values.put("name","Test");
            android.net.Uri uri=android.net.Uri.parse("content://"+app.getAuthority()+"/"+changed.getPath().getName());
            android.net.Uri saved=app.getContentResolver().insert(uri,values);
            assertNotNull(saved);long id=android.content.ContentUris.parseId(saved);assertTrue(changed.isChanges());
            await(scenario,a -> row(a,changed.getId())&&!row(a,clean.getId())
                    &&a.findViewById(R.id.sync_pending_badge).getVisibility()==View.VISIBLE);
            scenario.onActivity(a -> {
                LayersListAdapter adapter=adapter(a);
                View recycled=adapter.getView(position(adapter,changed.getId()),null,new FrameLayout(a));
                recycled=adapter.getView(position(adapter,clean.getId()),recycled,new FrameLayout(a));
                assertEquals(View.GONE,recycled.findViewById(com.nextgis.maplibui.R.id.layer_pending_badge).getVisibility());
            });
            scenario.recreate();
            await(scenario,a -> row(a,changed.getId())&&!row(a,clean.getId()));
            // Simulate acknowledgment, then deletion: the empty layer still has an outbox entry.
            FeatureChanges.removeAllChanges(DatabaseContext.getDatabaseForLayer(changed,false),changed.getChangeTableName());
            assertEquals(1,app.getContentResolver().delete(android.content.ContentUris.withAppendedId(uri,id),null,null));
            assertEquals(0,changed.getSqliteTableRowCount());assertTrue(changed.isChanges());
            await(scenario,a -> row(a,changed.getId()));
            FeatureChanges.removeAllChanges(DatabaseContext.getDatabaseForLayer(changed,false),changed.getChangeTableName());
            app.sendBroadcast(new Intent(SyncAdapter.SYNC_FINISH).setPackage(app.getPackageName()));
            await(scenario,a -> !row(a,changed.getId())&&!row(a,clean.getId())
                    &&a.findViewById(R.id.sync_pending_badge).getVisibility()==View.GONE);
        } finally {
            assertTrue(changed.delete(false));assertTrue(clean.delete(false));map.save();
            SharedPreferences.Editor edit=prefs.edit();
            if(hadIntro)edit.putBoolean(AppSettingsConstants.KEY_PREF_INTRO,oldIntro);else edit.remove(AppSettingsConstants.KEY_PREF_INTRO);
            assertTrue(edit.commit());
        }
    }

    private static List<Long> layerOrder(LayersListAdapter adapter) {
        List<Long> ids = new ArrayList<>();
        for (int position = 0; position < adapter.getCount(); position++) {
            ids.add(adapter.getItemId(position));
        }
        return ids;
    }

    private static void changeMapInWorker(Runnable mutation) {
        FutureTask<Void> work = new FutureTask<>(mutation, null);
        new Thread(work, "layer-list-test-worker").start();
        try {
            work.get(10, TimeUnit.SECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new AssertionError(error);
        } catch (ExecutionException | TimeoutException error) {
            throw new AssertionError("Background layer mutation did not complete", error);
        } finally {
            work.cancel(true);
        }
    }

    @Test public void backgroundChangesPublishOneOrderAndDraggingTargetsTheDisplayedLayer() throws Exception {
        GISApplication app = (GISApplication) InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getApplicationContext();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(app);
        boolean hadIntro = prefs.contains(AppSettingsConstants.KEY_PREF_INTRO);
        boolean oldIntro = prefs.getBoolean(AppSettingsConstants.KEY_PREF_INTRO, false);
        assertTrue(prefs.edit().putBoolean(AppSettingsConstants.KEY_PREF_INTRO, true).commit());

        MapDrawable map = (MapDrawable) app.getMap();
        NGWVectorLayerUI first = layer(app, map);
        NGWVectorLayerUI second = layer(app, map);
        NGWVectorLayerUI incoming = layer(app, map);
        map.removeLayer(incoming);
        map.save();
        AtomicReference<List<Long>> expectedFinal = new AtomicReference<>();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            await(scenario, activity -> adapter(activity) != null
                    && !SharedUnderlayProjects.isMigrationPending()
                    && !ProjectOperationCoordinator.isBusy());
            scenario.onActivity(activity -> {
                LayersListAdapter rows = adapter(activity);
                List<Long> original = layerOrder(rows);
                int source = position(rows, second.getId());
                int target = position(rows, first.getId());

                changeMapInWorker(() -> map.insertLayer(0, incoming));
                assertEquals(original.size() + 1, map.getLayerCount());
                assertEquals("The adapter must keep the last notified rows", original, layerOrder(rows));

                List<Long> reordered = new ArrayList<>(original);
                reordered.add(target, reordered.remove(source));
                expectedFinal.set(reordered);
                List<Long> withIncoming = new ArrayList<>(reordered);
                withIncoming.add((long) incoming.getId());
                rows.swapElements(source, target);
                assertEquals("Drag targets the same displayed layer after a model insert",
                        withIncoming, layerOrder(rows));

                changeMapInWorker(() -> map.removeLayer(incoming));
                assertEquals("The adapter keeps rows until the removal notification",
                        withIncoming, layerOrder(rows));
            });
            await(scenario, activity -> expectedFinal.get().equals(layerOrder(adapter(activity))));
        } finally {
            if (map.getLayerById(incoming.getId()) != null) map.removeLayer(incoming);
            assertTrue(incoming.delete(false));
            assertTrue(second.delete(false));
            assertTrue(first.delete(false));
            map.save();
            SharedPreferences.Editor edit = prefs.edit();
            if (hadIntro) edit.putBoolean(AppSettingsConstants.KEY_PREF_INTRO, oldIntro);
            else edit.remove(AppSettingsConstants.KEY_PREF_INTRO);
            assertTrue(edit.commit());
        }
    }
}
