package com.tahlor.smsforwarder;

import android.content.Context;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Small HTTPS-only API client with redirects disabled so validated hosts cannot change in transit. */
final class ConnectedApi {
    static final class Response {
        final int code;
        final JSONObject json;

        Response(int code, JSONObject json) {
            this.code = code;
            this.json = json;
        }

        boolean ok() { return code >= 200 && code < 300; }

        String message() {
            if (json != null) {
                String error = json.optString("error", "").trim();
                if (!error.isEmpty()) return error;
            }
            return "Server returned HTTP " + code + ".";
        }
    }

    private ConnectedApi() {}

    static Response claim(String server, String token, String label, String phoneNumber)
            throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("token", token);
        payload.put("label", label);
        payload.put("phone_number", phoneNumber);
        return request(PairingUri.validateServer(server), "/api/device/claim", "POST",
                payload, null, null);
    }

    static Response request(Context context, ConnectedStore.Credentials credentials,
                            String path, String method, JSONObject payload) throws Exception {
        if (credentials == null) throw new IllegalStateException("This phone is not paired.");
        String server = PairingUri.validateServer(credentials.server);
        return request(server, path, method, payload, credentials.deviceId,
                credentials.deviceToken);
    }

    private static Response request(String server, String path, String method,
                                    JSONObject payload, String deviceId, String deviceToken)
            throws Exception {
        String safeServer = PairingUri.validateServer(server);
        if (path == null || !path.startsWith("/api/device/")) {
            throw new IllegalArgumentException("Unsupported SMS Bridge API path.");
        }
        URL url = new URL(safeServer + path);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(20_000);
        connection.setRequestMethod(method);
        connection.setRequestProperty("Accept", "application/json");
        if (deviceToken != null) connection.setRequestProperty("X-Device-Token", deviceToken);
        if (deviceId != null) connection.setRequestProperty("X-Device-Id", deviceId);
        if (payload != null) {
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            try (OutputStream output = connection.getOutputStream()) {
                output.write(payload.toString().getBytes(StandardCharsets.UTF_8));
            }
        }
        int code = connection.getResponseCode();
        InputStream stream = code >= 200 && code < 400
                ? connection.getInputStream() : connection.getErrorStream();
        String raw = stream == null ? "" : readFully(stream);
        connection.disconnect();
        JSONObject json = null;
        if (!raw.trim().isEmpty()) {
            try { json = new JSONObject(raw); } catch (Exception ignored) {}
        }
        return new Response(code, json);
    }

    private static String readFully(InputStream input) throws Exception {
        try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = stream.read(buffer)) >= 0) output.write(buffer, 0, count);
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
