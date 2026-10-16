package com.nextgis.mobile.usage;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.nextgis.mobile.util.VerificationEnvironment;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class UsageDeliveryTest {
    private Context context;
    @Before public void isolated() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        VerificationEnvironment.requireIsolated(context);
        assertFalse(UsageAnalytics.enabled());
    }
    private File database() { return new File(context.getCacheDir(), "usage-synthetic-" + java.util.UUID.randomUUID() + ".db"); }
    private JSONObject event() throws Exception { return UsageAnalytics.event("app_open", "synthetic-session", null); }

    @Test public void projectInventoryPreservesAccountIdentityWithoutCredentialsOrDuplicateSnapshots() throws Exception {
        com.nextgis.maplib.api.IGISApplication app =
                (com.nextgis.maplib.api.IGISApplication) context.getApplicationContext();
        android.accounts.AccountManager manager = android.accounts.AccountManager.get(context);
        android.accounts.Account account = new android.accounts.Account(
                "usage-synthetic-" + java.util.UUID.randomUUID(), app.getAccountsType());
        android.os.Bundle data = new android.os.Bundle();
        data.putString("url", "https://synthetic:do-not-send@usage.example/path?token=do-not-send");
        data.putString("login", "usage-engineer");
        assertTrue(manager.addAccountExplicitly(account, "do-not-send", data));
        try (UsageStore store = new UsageStore(context, database())) {
            com.nextgis.maplibui.util.CollectorProjectRegistry.ProjectInfo project =
                    com.nextgis.maplibui.util.CollectorProjectRegistry.ensureProject(
                            context, account.name, 1403L, "Синтетический проект", null);
            assertNotNull(project);
            UsageAnalytics.snapshot(context, store, "synthetic-session");
            UsageAnalytics.snapshot(context, store, "synthetic-session");
            assertEquals(1, store.batch().length());
            JSONObject inventory = store.batch().getJSONObject(0);
            assertEquals("inventory", inventory.getString("type"));
            assertTrue(inventory.isNull("project"));
            JSONObject found = null;
            JSONArray projects = inventory.getJSONArray("projects");
            for (int i = 0; i < projects.length(); i++) {
                JSONObject item = projects.getJSONObject(i);
                if (project.getProjectUid().equals(item.getString("uid"))) found = item;
            }
            assertNotNull(found);
            assertEquals(1403L, found.getLong("remote_id"));
            assertEquals("usage-engineer", found.getJSONObject("account").getString("login"));
            assertEquals("https://usage.example/path", found.getJSONObject("account").getString("server"));
            assertFalse(inventory.toString().contains("do-not-send"));
            assertFalse(inventory.has("duration_ms"));
        } finally { assertTrue(manager.removeAccountExplicitly(account)); }
    }

    @Test public void queueIdentityAndExactAcknowledgmentsSurviveRestart() throws Exception {
        File file = database(); String identity; JSONArray batch;
        try (UsageStore store = new UsageStore(context, file)) {
            identity = store.installationId();
            assertTrue(store.append(event(), null));
            assertTrue(store.append(event(), null));
            batch = store.batch();
        }
        try (UsageStore store = new UsageStore(context, file)) {
            assertEquals(identity, store.installationId());
            assertEquals(batch.toString(), store.batch().toString());
            try { store.acknowledge(batch, new JSONArray().put("unknown-event")); fail(); }
            catch (org.json.JSONException expected) { }
            assertEquals(2, store.batch().length());
            store.acknowledge(batch, new JSONArray().put(batch.getJSONObject(0).getString("id")));
            assertEquals(batch.getJSONObject(1).getString("id"), store.batch().getJSONObject(0).getString("id"));
            store.acknowledge(batch, new JSONArray().put(batch.getJSONObject(1).getString("id")));
            assertEquals(0, store.batch().length());
            store.append(event(), null);
            assertTrue(store.batch().getJSONObject(0).getLong("seq") > batch.getJSONObject(1).getLong("seq"));
        }
    }

    @Test public void temporaryFailureRetainsOriginalIdsAndSuccessfulRetryDrainsQueue() throws Exception {
        File file = database(); JSONObject device = new JSONObject().put("id", "synthetic-device");
        try (UsageStore store = new UsageStore(context, file); ServerSocket server = new ServerSocket(0)) {
            store.append(event(), null);
            String id = store.batch().getJSONObject(0).getString("id");
            server.setSoTimeout(20000);
            CompletableFuture<Void> done = new CompletableFuture<>();
            Thread endpoint = new Thread(() -> {
                try {
                    for (int pass = 0; pass < 2; pass++) {
                        try (Socket socket = server.accept()) {
                            socket.setSoTimeout(15000);
                            InputStream input = socket.getInputStream();
                            ByteArrayOutputStream header = new ByteArrayOutputStream(); int previous = 0, current;
                            while ((current = input.read()) != -1) {
                                header.write(current);
                                if (previous == '\r' && current == '\n' && header.toString("US-ASCII").endsWith("\r\n\r\n")) break;
                                previous = current;
                            }
                            int length = 0;
                            for (String line : header.toString("US-ASCII").split("\r\n")) {
                                if (line.toLowerCase(java.util.Locale.ROOT).startsWith("content-length:")) length = Integer.parseInt(line.substring(15).trim());
                            }
                            byte[] body = new byte[length]; int offset = 0;
                            while (offset < length) { int count = input.read(body, offset, length-offset); if (count < 0) throw new EOFException(); offset += count; }
                            JSONObject payload = new JSONObject(new String(body, StandardCharsets.UTF_8));
                            JSONObject received = payload.getJSONArray("events").getJSONObject(0);
                            assertEquals(id, received.getString("id"));
                            assertFalse(received.has("duration_ms"));
                            String response = pass == 0 ? "{}" : new JSONObject().put("accepted", new JSONArray().put(id)).toString();
                            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                            String status = pass == 0 ? "503 Service Unavailable" : "200 OK";
                            socket.getOutputStream().write(("HTTP/1.1 " + status + "\r\nContent-Length: " + bytes.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                            socket.getOutputStream().write(bytes); socket.getOutputStream().flush();
                        }
                    }
                    done.complete(null);
                } catch (Throwable error) { done.completeExceptionally(error); }
            }, "synthetic-usage-endpoint");
            endpoint.start();
            String url = "http://127.0.0.1:" + server.getLocalPort() + "/api/events";
            assertFalse(UsageDelivery.send(store, device, url, "synthetic-upload", true));
            assertEquals(id, store.batch().getJSONObject(0).getString("id"));
            assertTrue(UsageDelivery.send(store, device, url, "synthetic-upload", true));
            assertEquals(0, store.batch().length());
            done.get(20, TimeUnit.SECONDS); endpoint.join(1000);
        }
    }
}
