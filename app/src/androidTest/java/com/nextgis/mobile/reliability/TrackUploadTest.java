package com.nextgis.mobile.reliability;

import android.content.*;
import android.database.DatabaseUtils;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.view.View;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.work.WorkManager;
import com.nextgis.maplib.map.*;
import com.nextgis.maplib.util.*;
import com.nextgis.maplibui.GISApplication;
import com.nextgis.maplibui.mapui.TrackLayerUI;
import com.nextgis.maplibui.mapui.TrackUploader;
import com.nextgis.maplibui.mapui.TrackWorker;
import com.nextgis.maplibui.mapui.TrackRegistrationState;
import com.nextgis.maplibui.util.CollectorProjectRegistry;
import com.nextgis.mobile.R;
import com.nextgis.mobile.activity.MainActivity;
import com.nextgis.mobile.util.AppSettingsConstants;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONArray;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class TrackUploadTest {
    private static class Endpoint implements AutoCloseable {
        final ServerSocket server = new ServerSocket(0);
        final CompletableFuture<List<Integer>> packets = new CompletableFuture<>();
        volatile boolean registered = true;
        volatile int registrationCode = 200;
        final java.util.concurrent.atomic.AtomicInteger registrationChecks = new java.util.concurrent.atomic.AtomicInteger();
        Endpoint(int... codes) throws Exception {
            server.setSoTimeout(90000);
            new Thread(() -> {
                List<Integer> counts = new ArrayList<>();
                try {
                    int index = 0;
                    while (index < codes.length) try (Socket socket = server.accept()) {
                        socket.setSoTimeout(10000);
                        BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                        String request = reader.readLine();
                        int length = 0; String line;
                        while ((line = reader.readLine()) != null && !line.isEmpty())
                            if (line.toLowerCase(Locale.ROOT).startsWith("content-length:")) length = Integer.parseInt(line.substring(line.indexOf(':') + 1).trim());
                        char[] body = new char[length]; int read = 0, next;
                        while (read < length && (next = reader.read(body, read, length - read)) > 0) read += next;
                        String response = ""; int code = 200;
                        if (request.contains("/registered")) {
                            response = "{\"registered\":" + registered + "}";
                            code = registrationCode;
                            registrationChecks.incrementAndGet();
                        }
                        else { counts.add(new JSONArray(new String(body, 0, read)).length()); code = codes[index++]; }
                        socket.getOutputStream().write(("HTTP/1.1 " + code + " Result\r\nContent-Length: " + response.length()
                                + "\r\nConnection: close\r\n\r\n" + response).getBytes(StandardCharsets.US_ASCII));
                        socket.getOutputStream().flush();
                    }
                    packets.complete(counts);
                } catch (Exception error) { packets.completeExceptionally(error); }
            }, "isolated-track-hub").start();
        }
        String url() { return "http://127.0.0.1:" + server.getLocalPort(); }
        @Override public void close() throws Exception { server.close(); }
    }

    private static class Fixture implements AutoCloseable {
        final GISApplication app = (GISApplication) InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(app);
        final boolean oldSend = TrackSendSettings.isEnabled(prefs), hadSend = prefs.contains(SettingsConstants.KEY_PREF_TRACK_SEND);
        final boolean oldIntro = prefs.getBoolean(AppSettingsConstants.KEY_PREF_INTRO, false), hadIntro = prefs.contains(AppSettingsConstants.KEY_PREF_INTRO);
        final String oldHub = prefs.getString("tracker_hub_url", null);
        final String oldRegistration = prefs.getString(TrackRegistrationState.PREF_REGISTERED_DEVICE, null);
        final ActivityScenario<MainActivity> scenario;
        final MapContentProviderHelper map;
        final TrackLayer layer;
        final long trackId;
        Fixture(int count) throws Exception {
            assertTrue("Isolated emulator only", "ranchu".equals(android.os.Build.HARDWARE) || "goldfish".equals(android.os.Build.HARDWARE));
            WorkManager.getInstance(app).cancelAllWorkByTag(TrackWorker.WORK_TAG).getResult().get(10, TimeUnit.SECONDS);
            prefs.edit().putBoolean(AppSettingsConstants.KEY_PREF_INTRO, true).putBoolean(SettingsConstants.KEY_PREF_TRACK_SEND, false)
                    .remove(TrackRegistrationState.PREF_REGISTERED_DEVICE).commit();
            scenario = ActivityScenario.launch(MainActivity.class);
            long deadline = android.os.SystemClock.uptimeMillis() + 30000;
            while (app.getMap() == null && android.os.SystemClock.uptimeMillis() < deadline) Thread.sleep(50);
            map = (MapContentProviderHelper) app.getMap();
            layer = (TrackLayer) MapContentProviderHelper.getVectorLayerByPath(map, TrackLayer.TABLE_TRACKS);
            assertNotNull(layer);
            assertFalse("Fixture requires no existing unsent tracks", TrackUploader.hasPending(app, layer));
            trackId = insert(layer, count, app);
        }
        static long insert(TrackLayer layer, int count, GISApplication app) {
            Uri tracks = Uri.parse("content://" + app.getAuthority() + "/tracks");
            ContentValues session = new ContentValues();
            session.put(TrackLayer.FIELD_NAME, "Isolated upload fixture"); session.put(TrackLayer.FIELD_START, 1000L);
            session.put(TrackLayer.FIELD_END, 5000L); session.put(TrackLayer.FIELD_VISIBLE, 0);
            long id = ContentUris.parseId(layer.insert(tracks, session));
            Uri points = Uri.parse("content://" + app.getAuthority() + "/trackpoints");
            for (int i = 0; i < count; i++) {
                ContentValues point = new ContentValues();
                point.put(TrackLayer.FIELD_SESSION, id); point.put(TrackLayer.FIELD_TIMESTAMP, 1234000L);
                point.put(TrackLayer.FIELD_LON, 10 + i); point.put(TrackLayer.FIELD_LAT, 20 + i);
                point.put(TrackLayer.FIELD_FIX, "3d"); point.put(TrackLayer.FIELD_SENT, 0);
                assertNotNull(layer.insert(points, point));
            }
            return id;
        }
        long sent() { return DatabaseUtils.longForQuery(map.getDatabase(true),
                "SELECT count(*) FROM trackpoints WHERE session = ? AND sent = 1", new String[]{Long.toString(trackId)}); }
        void badge(boolean expected) throws Exception {
            AtomicBoolean matches = new AtomicBoolean(); long deadline = android.os.SystemClock.uptimeMillis() + 10000;
            do {
                scenario.onActivity(activity -> {
                    View badge = activity.findViewById(R.id.sync_pending_badge);
                    matches.set(badge != null && (badge.getVisibility() == View.VISIBLE) == expected);
                });
                if (!matches.get()) Thread.sleep(50);
            } while (!matches.get() && android.os.SystemClock.uptimeMillis() < deadline);
            assertTrue("Track upload badge was not reconciled", matches.get());
        }
        void hub(Endpoint endpoint) { prefs.edit().putString("tracker_hub_url", endpoint.url()).putBoolean(SettingsConstants.KEY_PREF_TRACK_SEND, true).commit(); }
        @Override public void close() throws Exception {
            prefs.edit().putBoolean(SettingsConstants.KEY_PREF_TRACK_SEND, false).commit();
            WorkManager.getInstance(app).cancelAllWorkByTag(TrackWorker.WORK_TAG).getResult().get(10, TimeUnit.SECONDS);
            layer.delete(Uri.parse("content://" + app.getAuthority() + "/tracks"), "_id = ?", new String[]{Long.toString(trackId)});
            scenario.close();
            SharedPreferences.Editor restore = prefs.edit();
            if (oldHub == null) restore.remove("tracker_hub_url"); else restore.putString("tracker_hub_url", oldHub);
            if (oldRegistration == null) restore.remove(TrackRegistrationState.PREF_REGISTERED_DEVICE);
            else restore.putString(TrackRegistrationState.PREF_REGISTERED_DEVICE, oldRegistration);
            if (hadSend) restore.putBoolean(SettingsConstants.KEY_PREF_TRACK_SEND, oldSend); else restore.remove(SettingsConstants.KEY_PREF_TRACK_SEND);
            if (hadIntro) restore.putBoolean(AppSettingsConstants.KEY_PREF_INTRO, oldIntro); else restore.remove(AppSettingsConstants.KEY_PREF_INTRO);
            assertTrue(restore.commit());
        }
    }

    @Test public void registrationLaterUploadsWithoutChangingTheSettingAndClearsTheBadge() throws Exception {
        try (Fixture f = new Fixture(2); Endpoint hub = new Endpoint(404, 200)) {
            hub.registered = false;
            f.hub(hub); assertFalse(TrackRegistrationState.canShowPending(f.app)); f.badge(false);
            assertFalse(TrackWorker.uploadProject(f.app, f.map.getPath().getCanonicalPath()));
            assertEquals(0, f.sent()); assertTrue(TrackSendSettings.isEnabled(f.prefs)); f.badge(false);
            hub.registered = true;
            assertFalse(TrackWorker.uploadProject(f.app, f.map.getPath().getCanonicalPath()));
            assertEquals(0, f.sent()); assertTrue(TrackRegistrationState.canShowPending(f.app)); f.badge(true);
            assertTrue(TrackWorker.uploadProject(f.app, f.map.getPath().getCanonicalPath()));
            assertEquals(2, f.sent()); f.badge(false);
            assertEquals(Arrays.asList(2, 2), hub.packets.get(5, TimeUnit.SECONDS));
        }
    }

    @Test public void failedSecondPacketDoesNotAcknowledgeRowsWithTheSameTimestamp() throws Exception {
        try (Fixture f = new Fixture(101); Endpoint hub = new Endpoint(200, 404, 200)) {
            f.hub(hub);
            assertFalse(TrackWorker.uploadProject(f.app, f.map.getPath().getCanonicalPath()));
            assertEquals(100, f.sent()); assertTrue(TrackUploader.hasPending(f.app, f.layer));
            f.badge(true);
            assertTrue(TrackWorker.uploadProject(f.app, f.map.getPath().getCanonicalPath()));
            assertEquals(101, f.sent());
            f.badge(false);
            assertEquals(Arrays.asList(100, 1, 1), hub.packets.get(5, TimeUnit.SECONDS));
        }
    }

    @Test public void scheduledDeliveryRetriesAfterRegistrationWithoutOpeningSettings() throws Exception {
        try (Fixture f = new Fixture(2); Endpoint hub = new Endpoint(200)) {
            hub.registered = false; f.hub(hub);
            android.app.UiAutomation automation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
            try {
                automation.executeShellCommand("svc wifi enable").close();
                TrackWorker.schedule(f.app, f.map);
                long deadline = android.os.SystemClock.uptimeMillis() + 30000;
                while (hub.registrationChecks.get() == 0 && android.os.SystemClock.uptimeMillis() < deadline) Thread.sleep(100);
                assertTrue("WorkManager did not run the persisted upload", hub.registrationChecks.get() > 0);
                assertEquals(0, f.sent()); assertTrue(TrackSendSettings.isEnabled(f.prefs));
                assertFalse(TrackRegistrationState.canShowPending(f.app)); f.badge(false);
                hub.registered = true;
                deadline = android.os.SystemClock.uptimeMillis() + 60000;
                while (f.sent() != 2 && android.os.SystemClock.uptimeMillis() < deadline) Thread.sleep(100);
                assertEquals("Queued work must retry after registration without a preference change", 2, f.sent());
                f.badge(false);
                assertEquals(Collections.singletonList(2), hub.packets.get(5, TimeUnit.SECONDS));
            } finally { automation.executeShellCommand("svc wifi disable").close(); }
        }
    }

    @Test public void optOutHidesPendingTracksAndDoesNotSendThem() throws Exception {
        try (Fixture f = new Fixture(2); Endpoint hub = new Endpoint(404)) {
            f.badge(false);
            assertTrue(TrackWorker.uploadProject(f.app, f.map.getPath().getCanonicalPath()));
            assertEquals(0, f.sent());
            f.hub(hub);
            assertFalse(TrackWorker.uploadProject(f.app, f.map.getPath().getCanonicalPath())); f.badge(true);
            f.prefs.edit().putBoolean(SettingsConstants.KEY_PREF_TRACK_SEND, false).commit(); f.badge(false);
            assertTrue(TrackWorker.uploadProject(f.app, f.map.getPath().getCanonicalPath()));
            assertEquals(0, f.sent()); assertEquals(Collections.singletonList(2), hub.packets.get(5, TimeUnit.SECONDS));
        }
    }

    @Test public void registrationRevocationHidesPendingButTemporaryServerFailureKeepsTheBadge() throws Exception {
        try (Fixture f = new Fixture(2); Endpoint hub = new Endpoint(404, 200)) {
            f.hub(hub);
            assertFalse(TrackWorker.uploadProject(f.app, f.map.getPath().getCanonicalPath())); f.badge(true);
            f.scenario.recreate(); f.badge(true);
            hub.registrationCode = 503;
            assertFalse(TrackWorker.uploadProject(f.app, f.map.getPath().getCanonicalPath()));
            assertTrue(TrackRegistrationState.canShowPending(f.app)); f.badge(true);
            hub.registrationCode = 200; hub.registered = false;
            assertFalse(TrackWorker.uploadProject(f.app, f.map.getPath().getCanonicalPath()));
            assertFalse(TrackRegistrationState.canShowPending(f.app)); f.badge(false);
            assertEquals(0, f.sent()); assertTrue(TrackSendSettings.isEnabled(f.prefs));
            hub.registered = true;
            assertTrue(TrackWorker.uploadProject(f.app, f.map.getPath().getCanonicalPath())); f.badge(false);
            assertEquals(Arrays.asList(2, 2), hub.packets.get(5, TimeUnit.SECONDS));
        }
    }

    @Test public void changingTrackerHubDoesNotReuseAnotherServersRegistration() throws Exception {
        try (Fixture f = new Fixture(2); Endpoint first = new Endpoint(404); Endpoint second = new Endpoint(404, 200)) {
            f.hub(first);
            assertFalse(TrackWorker.uploadProject(f.app, f.map.getPath().getCanonicalPath())); f.badge(true);
            second.registered = false; f.hub(second);
            assertFalse(TrackRegistrationState.canShowPending(f.app)); f.badge(false);
            assertFalse(TrackWorker.uploadProject(f.app, f.map.getPath().getCanonicalPath())); f.badge(false);
            assertEquals(0, f.sent());
            second.registered = true;
            assertFalse(TrackWorker.uploadProject(f.app, f.map.getPath().getCanonicalPath())); f.badge(true);
            assertTrue(TrackWorker.uploadProject(f.app, f.map.getPath().getCanonicalPath())); f.badge(false);
            assertEquals(Collections.singletonList(2), first.packets.get(5, TimeUnit.SECONDS));
            assertEquals(Arrays.asList(2, 2), second.packets.get(5, TimeUnit.SECONDS));
        }
    }

    @Test public void queuedProjectUploadsItsOwnTrackWhileAnotherMapIsActive() throws Exception {
        try (Fixture f = new Fixture(1); Endpoint hub = new Endpoint(200)) {
            File registry = new File(f.app.getExternalFilesDir(SettingsConstants.KEY_PREF_MAP), "collector_projects/collector_projects_registry.json");
            byte[] before = registry.isFile() ? Files.readAllBytes(registry.toPath()) : null;
            CollectorProjectRegistry.ProjectInfo project = CollectorProjectRegistry.createLocalProject(f.app, "Isolated track " + UUID.randomUUID());
            assertNotNull(project);
            File file = new File(project.getMapPath(), project.getMapName() + Constants.MAP_EXT);
            MapDrawable other = new MapDrawable(android.graphics.Bitmap.createBitmap(1,1,android.graphics.Bitmap.Config.ARGB_8888),
                    f.app, file, f.app.getLayerFactory(), false);
            other.setName(project.getName());
            try {
                try (SyncWorkspaceSession owner = new SyncWorkspaceSession(other); SyncWorkspaceSession.Scope scope = owner.enter()) {
                    TrackLayerUI tracks = new TrackLayerUI(f.app, new File(other.getPath(), TrackLayer.TABLE_TRACKS));
                    tracks.setName("Isolated tracks"); other.addLayer(tracks); Fixture.insert(tracks, 1, f.app);
                    assertTrue(tracks.save()); assertTrue(other.save());
                }
                other.closeSyncWorkspace(); other = null;
                f.hub(hub);
                assertTrue(TrackWorker.uploadProject(f.app, new File(project.getMapPath()).getCanonicalPath()));
                assertSame(f.map, f.app.getMap()); assertSame(f.map, MapBase.getActiveInstance()); assertEquals(0, f.sent());
                assertTrue(TrackUploader.hasPending(f.app, f.layer));
                assertEquals(Collections.singletonList(1), hub.packets.get(5, TimeUnit.SECONDS));
            } finally {
                if (other != null) other.closeSyncWorkspace();
                FileUtil.deleteRecursive(new File(project.getMapPath()));
                if (before == null) registry.delete(); else Files.write(registry.toPath(), before);
            }
        }
    }
}
