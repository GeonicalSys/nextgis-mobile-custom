package com.nextgis.mobile.usage;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Delivery errors retain the original event IDs and data for a later attempt. */
final class UsageDelivery {
    static boolean send(UsageStore store, JSONObject device, String endpoint, String token,
                        boolean syntheticLoopback) throws Exception {
        URL url = new URL(endpoint);
        boolean loopback = syntheticLoopback && "http".equals(url.getProtocol()) && "127.0.0.1".equals(url.getHost());
        if (!("https".equals(url.getProtocol()) || loopback) || url.getUserInfo() != null) return false;
        for (int pass = 0; pass < 10; pass++) {
            JSONArray events = store.batch();
            if (events.length() == 0) return true;
            JSONObject payload = new JSONObject().put("schema", 1).put("installation_id", store.installationId())
                    .put("device", device).put("events", events);
            byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            try {
                connection.setConnectTimeout(10000); connection.setReadTimeout(15000);
                connection.setInstanceFollowRedirects(false); connection.setRequestMethod("POST");
                connection.setDoOutput(true); connection.setFixedLengthStreamingMode(bytes.length);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                connection.setRequestProperty("Authorization", "Bearer " + token);
                try (java.io.OutputStream output = connection.getOutputStream()) { output.write(bytes); }
                if (connection.getResponseCode() != 200) return false;
                ByteArrayOutputStream reply = new ByteArrayOutputStream();
                try (InputStream input = connection.getInputStream()) {
                    byte[] buffer = new byte[4096]; int count;
                    while ((count = input.read(buffer)) != -1) {
                        if (reply.size() + count > 16384) return false;
                        reply.write(buffer, 0, count);
                    }
                }
                JSONArray accepted = new JSONObject(reply.toString("UTF-8")).getJSONArray("accepted");
                store.acknowledge(events, accepted);
                if (accepted.length() == 0) return false;
            } finally { connection.disconnect(); }
        }
        return store.batch().length() == 0;
    }
}
