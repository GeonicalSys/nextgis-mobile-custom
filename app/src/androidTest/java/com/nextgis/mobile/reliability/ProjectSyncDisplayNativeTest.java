package com.nextgis.mobile.reliability;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.nextgis.maplib.map.*;
import com.nextgis.maplib.util.*;
import com.nextgis.maplibui.GISApplication;
import com.nextgis.maplibui.util.ControlHelper;
import com.nextgis.mobile.R;
import com.nextgis.mobile.activity.MainActivity;
import com.nextgis.mobile.fragment.LayersFragment;
import java.io.File;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** A background scope on the main thread must not change the visible project's clock. */
@RunWith(AndroidJUnit4.class)
public class ProjectSyncDisplayNativeTest {
    @Test public void backgroundScopedCallbackStillShowsTheOpenProjectCompletion() throws Exception {
        assertTrue("Isolated emulator only", "ranchu".equals(android.os.Build.HARDWARE) || "goldfish".equals(android.os.Build.HARDWARE));
        GISApplication app = (GISApplication) InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
        MapBase active = MapBase.getActiveInstance();
        SharedPreferences clocks = app.getSharedPreferences("full-project-sync-state", Context.MODE_PRIVATE);
        Map<String, ?> before = clocks.getAll();
        long activeTime = System.currentTimeMillis() - 60_000;
        long backgroundTime = activeTime - 2 * 86_400_000L;
        assertNotEquals(ControlHelper.getSyncTime(app, activeTime), ControlHelper.getSyncTime(app, backgroundTime));
        File directory = new File(app.getCacheDir(), "display-owner-" + UUID.randomUUID());
        assertTrue(directory.mkdirs());
        MapDrawable background = new MapDrawable(Bitmap.createBitmap(1,1, Bitmap.Config.ARGB_8888),
                app, new File(directory, "map.ngm"), app.getLayerFactory(), false);
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            assertTrue(ProjectSyncState.markComplete(app, active.getPath().getAbsolutePath(), activeTime));
            assertTrue(ProjectSyncState.markComplete(app, background.getPath().getAbsolutePath(), backgroundTime));
            Method update = LayersFragment.class.getDeclaredMethod("updateInfo"); update.setAccessible(true);
            scenario.onActivity(activity -> {
                LayersFragment fragment = (LayersFragment) activity.getSupportFragmentManager().findFragmentById(R.id.layers);
                assertNotNull(fragment);
                try (SyncWorkspaceSession session = new SyncWorkspaceSession(background); SyncWorkspaceSession.Scope ignored = session.enter()) {
                    assertSame(background, app.getMap());
                    update.invoke(fragment);
                    TextView info = activity.findViewById(R.id.info);
                    assertEquals(ControlHelper.getSyncTime(activity, activeTime), info.getText().toString());
                    assertSame(active, MapBase.getActiveInstance());
                } catch (Exception error) { throw new AssertionError(error); }
            });
        } finally {
            SharedPreferences.Editor restore = clocks.edit().clear();
            for (Map.Entry<String, ?> entry : before.entrySet()) restore.putLong(entry.getKey(), (Long) entry.getValue());
            assertTrue(restore.commit()); background.closeSyncWorkspace(); FileUtil.deleteRecursive(directory);
        }
    }
}
