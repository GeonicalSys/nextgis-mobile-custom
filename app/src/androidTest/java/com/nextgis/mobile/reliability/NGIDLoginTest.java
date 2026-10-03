package com.nextgis.mobile.reliability;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.view.ViewParent;
import android.widget.EditText;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.android.material.textfield.TextInputLayout;
import com.nextgis.maplibui.R;
import com.nextgis.maplibui.activity.NGIDLoginActivity;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

/** Uses a local rejecting endpoint: no account, real password or remote service is touched. */
@RunWith(AndroidJUnit4.class)
public class NGIDLoginTest {
    @Test public void firstLoginAcceptsUsernameAndNormalizesOnlyTheIdentifier() throws Exception {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(
                InstrumentationRegistry.getInstrumentation().getTargetContext());
        boolean hadUrl = preferences.contains("ngid_url");
        String oldUrl = preferences.getString("ngid_url", null);
        try (ServerSocket server = new ServerSocket(0)) {
            server.setSoTimeout(15000);
            assertTrue(preferences.edit().putString("ngid_url", "http://127.0.0.1:"
                    + server.getLocalPort()).commit());
            CompletableFuture<String> request = new CompletableFuture<>();
            Thread endpoint = new Thread(() -> {
                try (Socket socket = server.accept()) {
                    socket.setSoTimeout(10000);
                    BufferedReader reader = new BufferedReader(new InputStreamReader(
                            socket.getInputStream(), StandardCharsets.UTF_8));
                    int length = 0;
                    String header;
                    while ((header = reader.readLine()) != null && !header.isEmpty()) {
                        if (header.toLowerCase(java.util.Locale.ROOT).startsWith("content-length:"))
                            length = Integer.parseInt(header.substring(header.indexOf(':') + 1).trim());
                    }
                    char[] body = new char[length];
                    int read = 0, count;
                    while (read < length && (count = reader.read(body, read, length - read)) > 0) read += count;
                    socket.getOutputStream().write(("HTTP/1.1 401 Unauthorized\r\n"
                            + "Content-Length: 0\r\nConnection: close\r\n\r\n")
                            .getBytes(StandardCharsets.US_ASCII));
                    socket.getOutputStream().flush();
                    request.complete(new String(body, 0, read));
                } catch (Exception error) { request.completeExceptionally(error); }
            }, "local-ngid-test");
            endpoint.start();
            try (ActivityScenario<NGIDLoginActivity> scenario = ActivityScenario.launch(NGIDLoginActivity.class)) {
                scenario.onActivity(activity -> {
                    EditText login = activity.findViewById(R.id.login);
                    ViewParent parent = login.getParent();
                    while (parent != null && !(parent instanceof TextInputLayout)) parent = parent.getParent();
                    assertNotNull(parent);
                    assertEquals(activity.getString(R.string.ngid_login_hint),
                            ((TextInputLayout) parent).getHint().toString());
                    int capitals = InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                            | InputType.TYPE_TEXT_FLAG_CAP_WORDS | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;
                    assertEquals(0, login.getInputType() & capitals);
                    assertArrayEquals(new String[] { "username" }, login.getAutofillHints());
                    login.setText(" SETNOVO.Master ");
                    ((EditText) activity.findViewById(R.id.password)).setText("MiXeD PaSs I");
                    activity.findViewById(R.id.signin).performClick();
                });
                Map<String, String> submitted = new HashMap<>();
                for (String pair : request.get(15, TimeUnit.SECONDS).split("&")) {
                    String[] parts = pair.split("=", 2);
                    submitted.put(URLDecoder.decode(parts[0], "UTF-8"),
                            URLDecoder.decode(parts.length > 1 ? parts[1] : "", "UTF-8"));
                }
                assertEquals("setnovo.master", submitted.get("username"));
                assertEquals("MiXeD PaSs I", submitted.get("password"));
                AtomicBoolean finished = new AtomicBoolean();
                long deadline = android.os.SystemClock.uptimeMillis() + 10000;
                while (!finished.get() && android.os.SystemClock.uptimeMillis() < deadline) {
                    scenario.onActivity(activity -> finished.set(activity.findViewById(R.id.signin).isEnabled()));
                    if (!finished.get()) Thread.sleep(50);
                }
                assertTrue("Authentication callback did not finish", finished.get());
            }
            endpoint.join(1000);
        } finally {
            SharedPreferences.Editor edit = preferences.edit();
            if (hadUrl) edit.putString("ngid_url", oldUrl); else edit.remove("ngid_url");
            assertTrue(edit.commit());
        }
    }
}
