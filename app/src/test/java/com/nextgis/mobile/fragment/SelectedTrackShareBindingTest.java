package com.nextgis.mobile.fragment;

import android.app.Application;
import android.content.Context;
import android.database.MatrixCursor;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import androidx.fragment.app.FragmentHostCallback;
import com.nextgis.maplib.map.TrackLayer;
import com.nextgis.maplibui.activity.NGActivity;
import com.nextgis.maplibui.util.LayerUtil;
import com.nextgis.maplibui.util.TrackActions;
import com.nextgis.maplibui.view.TrackSelectionPanel;
import com.nextgis.mobile.activity.MainActivity;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.LooperMode;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

/** Actual private MapFragment binding and real panel click; only DB lookup/export boundary is shadowed.
 * No Activity lifecycle, GIS map, track database, GPX file or async exporter is started.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE, application = Application.class, qualifiers = "ru",
        shadows = {SelectedTrackShareBindingTest.TrackLookup.class,
                SelectedTrackShareBindingTest.ShareBoundary.class},
        instrumentedPackages = {"com.nextgis.maplibui.util"})
@LooperMode(LooperMode.Mode.PAUSED)
public class SelectedTrackShareBindingTest {
    @Implements(value = TrackActions.class, isInAndroidSdk = false)
    public static class TrackLookup {
        static TrackLayer.TrackInfo current;
        static long queriedId;
        @Implementation protected static TrackLayer.TrackInfo find(Context context, long id) {
            queriedId = id;
            return current;
        }
    }
    @Implements(value = LayerUtil.class, isInAndroidSdk = false)
    public static class ShareBoundary {
        static int calls;
        static NGActivity owner;
        static String creator;
        static String[] ids;
        @Implementation protected static void shareTrackAsGPX(NGActivity activity, String value,
                String[] selected) {
            calls++;
            owner = activity;
            creator = value;
            ids = selected.clone();
        }
    }

    private MainActivity host;
    private MapFragment fragment;
    private TrackSelectionPanel panel;

    @Before public void prepare() {
        ShareBoundary.calls = 0;
        ShareBoundary.ids = null;
        TrackLookup.current = null;
        host = Robolectric.buildActivity(MainActivity.class).get();
        host.setTheme(com.nextgis.maplibui.R.style.Theme_NextGIS_AppCompat_Light);
        fragment = new MapFragment();
        FragmentHostCallback<MainActivity> callback = new FragmentHostCallback<MainActivity>(host,
                new Handler(Looper.getMainLooper()), 0) {
            @Override public MainActivity onGetHost() { return host; }
        };
        ReflectionHelpers.setField(fragment, "mHost", callback);
        ReflectionHelpers.setField(fragment, "mView", new View(host));
        ReflectionHelpers.setField(fragment, "mActivity", host);
        ReflectionHelpers.setField(fragment, "mMapRef", new WeakReference<>(null));
        panel = new TrackSelectionPanel(host);
        ReflectionHelpers.setField(fragment, "trackSelectionPanel", panel);
    }

    private static TrackLayer.TrackInfo info(int id, boolean visible, boolean finished) {
        try (MatrixCursor cursor = new MatrixCursor(new String[]{TrackLayer.FIELD_ID,
                TrackLayer.FIELD_NAME, TrackLayer.FIELD_COLOR, TrackLayer.FIELD_VISIBLE,
                TrackLayer.FIELD_END})) {
            cursor.addRow(new Object[]{id, "technical track", 0xff008844, visible ? 1 : 0,
                    finished ? "technical end" : null});
            assertTrue(cursor.moveToFirst());
            return TrackLayer.TrackInfo.fromCursor(cursor);
        }
    }

    private void bind(int id, boolean visible, boolean finished) throws Exception {
        TrackLookup.current = info(id, visible, finished);
        ReflectionHelpers.setField(fragment, "selectedTrackId", (long) id);
        refresh();
    }

    private void refresh() throws Exception {
        Method method = MapFragment.class.getDeclaredMethod("refreshSelectedTrack");
        method.setAccessible(true);
        try { method.invoke(fragment); }
        catch (InvocationTargetException error) {
            if (error.getCause() instanceof Error) throw (Error) error.getCause();
            throw (Exception) error.getCause();
        }
    }

    private void share() {
        int id = host.getResources().getIdentifier("track_panel_share", "id", host.getResources().getResourcePackageName(com.nextgis.maplibui.R.id.track_panel_title));
        assertTrue("Share has a dedicated stable resource ID", id != 0);
        View button = panel.findViewById(id);
        assertNotNull("Selected-track panel exposes Share", button);
        assertTrue(button.performClick());
    }

    @Test public void boundSharedTrackIdReachesExistingExporterOnce() throws Exception {
        bind(41, true, true);
        share();
        assertEquals(41L, TrackLookup.queriedId);
        assertEquals(1, ShareBoundary.calls);
        assertSame(host, ShareBoundary.owner);
        assertEquals("NextGIS Mobile", ShareBoundary.creator);
        assertArrayEquals(new String[]{"41"}, ShareBoundary.ids);
    }

    @Test public void hiddenCurrentTrackCanShareWithoutStoppingOrChangingSelection() throws Exception {
        bind(73, false, false);
        share();
        assertEquals(1, ShareBoundary.calls);
        assertArrayEquals(new String[]{"73"}, ShareBoundary.ids);
        assertEquals(73L, (long) ReflectionHelpers.getField(fragment, "selectedTrackId"));
        assertEquals(View.VISIBLE, panel.getVisibility());
    }

    @Test public void aNewBindingReplacesThePreviouslyCapturedTrack() throws Exception {
        bind(41, true, true);
        bind(73, true, true);
        share();
        assertEquals(1, ShareBoundary.calls);
        assertArrayEquals(new String[]{"73"}, ShareBoundary.ids);
    }

    @Test public void aStaleBindingCannotShareAfterSelectionChanges() throws Exception {
        bind(41, true, true);
        ReflectionHelpers.setField(fragment, "selectedTrackId", 73L);
        share();
        assertEquals(0, ShareBoundary.calls);
    }

    @Test public void aDestroyedViewCannotStartAnExporter() throws Exception {
        bind(41, true, true);
        ReflectionHelpers.setField(fragment, "mView", null);
        share();
        assertEquals(0, ShareBoundary.calls);
    }

    @Test public void aDifferentAttachedActivityCannotUseTheOldOwner() throws Exception {
        bind(41, true, true);
        ReflectionHelpers.setField(fragment, "mActivity", Robolectric.buildActivity(MainActivity.class).get());
        share();
        assertEquals(0, ShareBoundary.calls);
    }

    @Test public void aToolModeCannotStartTrackSharingFromAnOldBinding() throws Exception {
        bind(41, true, true);
        fragment.setMode(MapFragment.MODE_EDIT);
        share();
        assertEquals(0, ShareBoundary.calls);
    }

    @Test public void aMissingTrackClearsSelectionBeforeOldShareCanRun() throws Exception {
        bind(41, true, true);
        TrackLookup.current = null;
        refresh();
        assertEquals(View.GONE, panel.getVisibility());
        assertEquals(-1L, (long) ReflectionHelpers.getField(fragment, "selectedTrackId"));
        share();
        assertEquals(0, ShareBoundary.calls);
    }
}
