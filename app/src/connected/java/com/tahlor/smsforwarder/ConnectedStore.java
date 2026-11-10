package com.tahlor.smsforwarder;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.security.KeyStore;
import java.util.Set;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

/** Private, encrypted storage for device credentials and queued message content. */
final class ConnectedStore {
    private static final String VAULT = "connected_vault";
    private static final String CREDENTIALS = "credential_blob";
    private static final String EVENTS = "pending_event_blob";
    private static final String KEY_ALIAS = "sms_bridge_connected_vault_v1";

    static final class Credentials {
        final String server;
        final String deviceId;
        final String deviceToken;

        Credentials(String server, String deviceId, String deviceToken) {
            this.server = server;
            this.deviceId = deviceId;
            this.deviceToken = deviceToken;
        }
    }

    private ConnectedStore() {}

    private static SharedPreferences vault(Context context) {
        return context.getSharedPreferences(VAULT, Context.MODE_PRIVATE);
    }

    static synchronized Credentials credentials(Context context) {
        String blob = vault(context).getString(CREDENTIALS, null);
        if (blob == null) return null;
        try {
            JSONObject json = new JSONObject(decrypt(blob));
            String server = json.getString("server");
            String deviceId = json.getString("device_id");
            String token = json.getString("device_token");
            if (server.isEmpty() || deviceId.isEmpty() || token.isEmpty()) {
                throw new IllegalStateException("Saved device credentials are incomplete.");
            }
            return new Credentials(server, deviceId, token);
        } catch (Exception e) {
            throw new IllegalStateException("Saved device credentials could not be opened.", e);
        }
    }

    static synchronized void saveCredentials(Context context, String server,
                                             String deviceId, String deviceToken) throws Exception {
        JSONObject json = new JSONObject();
        json.put("server", PairingUri.validateServer(server));
        json.put("device_id", deviceId);
        json.put("device_token", deviceToken);
        if (!vault(context).edit().putString(CREDENTIALS, encrypt(json.toString())).commit()) {
            throw new IllegalStateException("Device credentials could not be saved.");
        }
    }

    static synchronized void clearCredentials(Context context) {
        vault(context).edit().remove(CREDENTIALS).remove(EVENTS).commit();
        try {
            KeyStore store = KeyStore.getInstance("AndroidKeyStore");
            store.load(null);
            if (store.containsAlias(KEY_ALIAS)) store.deleteEntry(KEY_ALIAS);
        } catch (Exception ignored) {}
    }

    static synchronized boolean enqueueEvent(Context context, JSONObject event) {
        try {
            JSONArray events = readEvents(context);
            String eventId = event.optString("event_id", "");
            if (eventId.isEmpty()) return false;
            for (int i = 0; i < events.length(); i++) {
                JSONObject queued = events.optJSONObject(i);
                if (queued != null && eventId.equals(queued.optString("event_id"))) return false;
            }
            events.put(event);
            return vault(context).edit().putString(EVENTS, encrypt(events.toString())).commit();
        } catch (Exception e) {
            ConnectedOptions.setError(context, "An incoming message could not be queued securely.");
            return false;
        }
    }

    static synchronized JSONArray pendingEvents(Context context) throws Exception {
        return readEvents(context);
    }

    private static JSONArray readEvents(Context context) throws Exception {
        String blob = vault(context).getString(EVENTS, null);
        if (blob == null) return new JSONArray();
        return new JSONArray(decrypt(blob));
    }

    static synchronized void removeEvents(Context context, Set<String> acceptedIds) throws Exception {
        if (acceptedIds == null || acceptedIds.isEmpty()) return;
        JSONArray queued = readEvents(context);
        JSONArray remaining = new JSONArray();
        for (int i = 0; i < queued.length(); i++) {
            JSONObject event = queued.optJSONObject(i);
            if (event == null || !acceptedIds.contains(event.optString("event_id"))) {
                if (event != null) remaining.put(event);
            }
        }
        SharedPreferences.Editor edit = vault(context).edit();
        if (remaining.length() == 0) edit.remove(EVENTS);
        else edit.putString(EVENTS, encrypt(remaining.toString()));
        if (!edit.commit()) throw new IllegalStateException("Queued events could not be updated.");
    }

    static synchronized void replaceEvents(Context context, JSONArray events) throws Exception {
        SharedPreferences.Editor edit = vault(context).edit();
        if (events == null || events.length() == 0) edit.remove(EVENTS);
        else edit.putString(EVENTS, encrypt(events.toString()));
        if (!edit.commit()) throw new IllegalStateException("Queued events could not be updated.");
    }

    static synchronized void clearQueuedEvents(Context context) {
        vault(context).edit().remove(EVENTS).commit();
    }

    static synchronized boolean containsPlaintextCredential(Context context, String candidate) {
        if (candidate == null || candidate.isEmpty()) return false;
        return (vault(context).getString(CREDENTIALS, "") + vault(context).getString(EVENTS, ""))
                .contains(candidate);
    }

    private static String encrypt(String value) throws Exception {
        return Base64.encodeToString(CredentialCipher.encrypt(key(), value), Base64.NO_WRAP);
    }

    private static String decrypt(String value) throws Exception {
        return CredentialCipher.decrypt(key(), Base64.decode(value, Base64.NO_WRAP));
    }

    private static SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (store.containsAlias(KEY_ALIAS)) {
            return ((SecretKey) store.getKey(KEY_ALIAS, null));
        }
        KeyGenerator generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(
                KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build());
        return generator.generateKey();
    }
}
