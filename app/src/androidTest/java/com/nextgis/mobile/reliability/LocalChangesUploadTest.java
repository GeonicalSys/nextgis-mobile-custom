package com.nextgis.mobile.reliability;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.*;
import android.os.Bundle;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.work.WorkManager;
import com.nextgis.maplib.datasource.*;
import com.nextgis.maplib.datasource.ngw.Connection;
import com.nextgis.maplib.map.*;
import com.nextgis.maplib.util.*;
import com.nextgis.maplibui.GISApplication;
import com.nextgis.maplibui.mapui.LocalChangesWorker;
import com.nextgis.maplibui.mapui.NGWVectorLayerUI;
import com.nextgis.maplibui.util.CollectorProjectRegistry;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Real saved line, SQLite outbox, disabled account schedule and loopback NGW HTTP only. */
@RunWith(AndroidJUnit4.class)
public class LocalChangesUploadTest {
    private EmulatorNetworkFixture network;
    @Before public void connectedFixture() throws Exception {
        network = new EmulatorNetworkFixture(InstrumentationRegistry.getInstrumentation().getTargetContext()); network.connected(true);
    }
    @After public void restoreConnectivity() throws Exception { if (network != null) network.close(); }
    static class Endpoint implements AutoCloseable {
        final ServerSocket server = new ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"));
        final List<String> mutations = new CopyOnWriteArrayList<>();
        volatile int status = 200;
        volatile Throwable error;
        volatile boolean holdRead = false;
        final CountDownLatch readEntered = new CountDownLatch(1), releaseRead = new CountDownLatch(1);
        final java.util.concurrent.atomic.AtomicInteger remoteId = new java.util.concurrent.atomic.AtomicInteger(42);
        final Thread thread;
        Endpoint() throws Exception {
            thread = new Thread(() -> {
                while (!server.isClosed()) try (Socket socket = server.accept()) {
                    socket.setSoTimeout(10000);
                    BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                    String request = reader.readLine(), line; int length = 0;
                    while ((line = reader.readLine()) != null && !line.isEmpty())
                        if (line.toLowerCase(Locale.ROOT).startsWith("content-length:")) length = Integer.parseInt(line.substring(line.indexOf(':') + 1).trim());
                    char[] body = new char[length]; int read = 0, count;
                    while (read < length && (count = reader.read(body, read, length - read)) > 0) read += count;
                    boolean mutation = !request.startsWith("GET ");
                    if (mutation) mutations.add(request + "\n" + new String(body, 0, read));
                    if (!mutation && holdRead) { readEntered.countDown(); assertTrue(releaseRead.await(30, TimeUnit.SECONDS)); }
                    int code = mutation ? status : 404;
                    String response = request.startsWith("POST ") && code == 200 ? "{\"id\":" + remoteId.getAndIncrement() + "}" : "{}";
                    byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                    socket.getOutputStream().write(("HTTP/1.1 " + code + " Result\r\nContent-Type: application/json\r\nContent-Length: "
                            + bytes.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                    socket.getOutputStream().write(bytes); socket.getOutputStream().flush();
                } catch (SocketException stopped) { if (!server.isClosed()) error = stopped; }
                catch (Throwable failed) { error = failed; }
            }, "isolated-local-ngw"); thread.start();
        }
        String url() { return "http://127.0.0.1:" + server.getLocalPort(); }
        @Override public void close() throws Exception { server.close(); thread.join(10000); assertNull(error); }
    }
    static class Fixture implements AutoCloseable {
        final GISApplication app = (GISApplication) InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
        final MapContentProviderHelper map = (MapContentProviderHelper) app.getMap();
        final Account account = new Account("local-delivery-" + UUID.randomUUID(), app.getAccountsType());
        final NGWVectorLayerUI layer;
        final String path = map.getPath().getCanonicalPath();
        final File registry = new File(app.getExternalFilesDir(SettingsConstants.KEY_PREF_MAP), "collector_projects/collector_projects_registry.json");
        final byte[] registryBefore = registry.isFile() ? Files.readAllBytes(registry.toPath()) : null;
        Fixture(Endpoint endpoint) throws Exception {
            assertTrue("Isolated emulator only", "ranchu".equals(android.os.Build.HARDWARE) || "goldfish".equals(android.os.Build.HARDWARE));
            cancelJobs();
            Bundle data = new Bundle(); data.putString("url", endpoint.url()); data.putString("login", "synthetic");
            assertTrue(AccountManager.get(app).addAccountExplicitly(account, "", data));
            ContentResolver.setIsSyncable(account, app.getAuthority(), 0);
            ContentResolver.setSyncAutomatically(account, app.getAuthority(), false);
            layer = create(map);
        }
        NGWVectorLayerUI create(MapContentProviderHelper owner) throws Exception {
            NGWVectorLayerUI vector = new NGWVectorLayerUI(app, new File(owner.getPath(), "local_delivery_line"));
            owner.addLayer(vector); vector.setName("Saved line"); vector.beginBulkImport();
            vector.create(GeoConstants.GTLineString, Collections.singletonList(new Field(GeoConstants.FTString, "name", "Name")));
            vector.endBulkImport(); vector.setAccountName(account.name); vector.setRemoteId(1329);
            JSONObject config = vector.toJSON(); config.put("ngw_layer_type", Connection.NGWResourceTypePostgisLayer); vector.fromJSON(config);
            assertTrue(vector.save()); assertTrue(owner.save()); return vector;
        }
        long savedLine(NGWVectorLayer vector, boolean automatic) throws Exception {
            vector.setSyncType(Constants.SYNC_ALL);
            vector.setSyncDirection(automatic ? 3 : 2);
            ContentValues row = new ContentValues(); row.put("name", "field-line");
            row.put(Constants.FIELD_GEOM, GeoGeometryFactory.fromWKT("LINESTRING (1 2, 3 4)", GeoConstants.CRS_WEB_MERCATOR).toBlob());
            long id = vector.insertAddChanges(row); assertTrue(id >= 10000000);
            vector.setSyncDirection(3); assertTrue(vector.save()); return id;
        }
        void cancelJobs() throws Exception { WorkManager.getInstance(app).cancelAllWorkByTag("local-changes-upload").getResult().get(10, TimeUnit.SECONDS); }
        long pending(NGWVectorLayer vector) { return FeatureChanges.getChangeCount(DatabaseContext.getDatabaseForLayer(vector, true), vector.getChangeTableName()); }
        boolean send() { return LocalChangesWorker.uploadProject(app, path); }
        void awaitSent(NGWVectorLayer vector) throws Exception {
            long deadline = android.os.SystemClock.elapsedRealtime() + 60000;
            while (pending(vector) > 0 && android.os.SystemClock.elapsedRealtime() < deadline) Thread.sleep(100);
            assertEquals("The committed edit must trigger automatic delivery", 0, pending(vector));
        }
        @Override public void close() throws Exception {
            Thread.sleep(1200); cancelJobs();
            assertTrue(layer.delete(false)); map.save();
            assertTrue(AccountManager.get(app).removeAccountExplicitly(account));
        }
    }
    @Test public void savingLineUploadsImmediatelyEvenWhenFullAutoSyncIsDisabledWithoutChangingTime() throws Exception {
        try (Endpoint endpoint = new Endpoint(); Fixture f = new Fixture(endpoint)) {
            long before = ProjectSyncState.lastComplete(f.app, f.path);
            f.savedLine(f.layer, true); f.awaitSent(f.layer);
            assertFalse(ContentResolver.getSyncAutomatically(f.account, f.app.getAuthority()));
            assertEquals(before, ProjectSyncState.lastComplete(f.app, f.path));
            assertTrue(endpoint.mutations.get(0).startsWith("POST /api/resource/1329/feature/"));
            assertTrue(endpoint.mutations.get(0).contains("LINESTRING"));
            assertEquals(42, f.layer.getFeature(42).getId());
        }
    }
    @Test public void offlineAndHttpFailureKeepLineUntilRetryThenUpdateAndDeleteAreDelivered() throws Exception {
        try (Endpoint endpoint = new Endpoint(); Fixture f = new Fixture(endpoint)) {
            f.savedLine(f.layer, false); long before = ProjectSyncState.lastComplete(f.app, f.path);
            network.connected(false); assertFalse(f.send()); assertEquals(1, f.pending(f.layer)); assertTrue(endpoint.mutations.isEmpty());
            network.connected(true); endpoint.status = 503;
            assertFalse(f.send()); assertEquals(1, f.pending(f.layer));
            endpoint.status = 200; assertTrue(f.send()); assertEquals(0, f.pending(f.layer));
            f.layer.setSyncDirection(2);
            ContentValues update = new ContentValues(); update.put("name", "changed");
            assertEquals(1, f.layer.updateAddChanges(update, 42)); f.layer.setSyncDirection(3);
            assertTrue(f.send()); assertEquals(0, f.pending(f.layer));
            f.layer.setSyncDirection(2); assertEquals(1, f.layer.deleteAddChanges(42)); f.layer.setSyncDirection(3);
            assertTrue(f.send()); assertEquals(0, f.pending(f.layer));
            assertTrue(endpoint.mutations.stream().anyMatch(s -> s.startsWith("PUT ") && s.contains("changed")));
            assertTrue(endpoint.mutations.stream().anyMatch(s -> s.startsWith("DELETE ")));
            assertEquals(before, ProjectSyncState.lastComplete(f.app, f.path));
        }
    }
    @Test public void temporaryDraftDoesNotUploadUntilSaved() throws Exception {
        try (Endpoint endpoint = new Endpoint(); Fixture f = new Fixture(endpoint)) {
            long id = f.savedLine(f.layer, false); f.layer.setFeatureTempFlag(id, true);
            assertTrue(f.send()); assertTrue(endpoint.mutations.isEmpty());
            f.layer.setFeatureTempFlag(id, false); f.awaitSent(f.layer);
            assertEquals(1, endpoint.mutations.stream().filter(s -> s.startsWith("POST ")).count());
        }
    }
    @Test public void commitDuringRunningDeliveryQueuesAnotherPassAndBothLinesReachServer() throws Exception {
        try (Endpoint endpoint = new Endpoint(); Fixture f = new Fixture(endpoint)) {
            long before = ProjectSyncState.lastComplete(f.app, f.path);
            endpoint.holdRead = true;
            try {
                f.savedLine(f.layer, true);
                assertTrue(endpoint.readEntered.await(30, TimeUnit.SECONDS));
                f.savedLine(f.layer, true);
                boolean following = false; long deadline = android.os.SystemClock.elapsedRealtime() + 10000;
                while (!following && android.os.SystemClock.elapsedRealtime() < deadline) {
                    following = WorkManager.getInstance(f.app).getWorkInfosByTag("local-changes-upload")
                            .get(5, TimeUnit.SECONDS).stream().anyMatch(w -> w.getState() == androidx.work.WorkInfo.State.BLOCKED);
                    if (!following) Thread.sleep(100);
                }
                assertTrue("A commit while the real HTTP pass runs must retain a following job", following);
            } finally { endpoint.releaseRead.countDown(); }
            f.awaitSent(f.layer);
            assertEquals(2, endpoint.mutations.stream().filter(s -> s.startsWith("POST ")).count());
            assertNotNull(f.layer.getFeature(42)); assertNotNull(f.layer.getFeature(43));
            assertEquals(before, ProjectSyncState.lastComplete(f.app, f.path));
        }
    }
    @Test public void backgroundProjectWithEqualLayerPathUploadsItsOwnGeometry() throws Exception {
        try (Endpoint endpoint = new Endpoint(); Fixture f = new Fixture(endpoint)) {
            f.savedLine(f.layer, false);
            CollectorProjectRegistry.ProjectInfo project = CollectorProjectRegistry.createLocalProject(f.app, "Delivery background fixture");
            assertNotNull(project);
            MapDrawable background = new MapDrawable(android.graphics.Bitmap.createBitmap(1,1,android.graphics.Bitmap.Config.ARGB_8888),
                    f.app, new File(project.getMapPath(), project.getMapName() + Constants.MAP_EXT), f.app.getLayerFactory(), false);
            try {
                background.setName(project.getName()); NGWVectorLayerUI own = f.create(background);
                try (SyncWorkspaceSession session = new SyncWorkspaceSession(background); SyncWorkspaceSession.Scope ignored = session.enter()) {
                    f.savedLine(own, false); background.save();
                }
                background.closeSyncWorkspace();
                assertTrue(LocalChangesWorker.uploadProject(f.app, project.getMapPath()));
                assertSame(f.map, f.app.getMap()); assertSame(f.map, MapBase.getActiveInstance());
                assertEquals("The open project's identical layer must stay pending", 1, f.pending(f.layer));
                assertEquals(1, endpoint.mutations.stream().filter(s -> s.startsWith("POST ")).count());
                assertEquals(0, ProjectSyncState.lastComplete(f.app, project.getMapPath()));
            } finally {
                background.closeSyncWorkspace();
                FileUtil.deleteRecursive(new File(project.getMapPath()));
                if (f.registryBefore == null) f.registry.delete(); else Files.write(f.registry.toPath(), f.registryBefore);
            }
        }
    }
}
