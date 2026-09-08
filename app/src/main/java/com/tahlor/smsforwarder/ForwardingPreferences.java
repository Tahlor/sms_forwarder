package com.tahlor.smsforwarder;

import android.app.backup.BackupManager;
import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class ForwardingPreferences {
    private static final String PREFS = "sms_forwarder";
    private static final String RUNTIME_PREFS = "sms_forwarder_runtime";
    private static final String KEY_PROFILES = "phone_profiles_v1";
    private static final String KEY_USER_DELETED = "user_deleted_setup";
    private static final String KEY_STATUS = "status";

    private static final String KEY_DESTINATION = "destination";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_CODE_ONLY = "code_only";
    private static final String KEY_CODE_COPY_FOLLOWUP = "code_copy_followup";
    private static final String KEY_SHORT_CODE_RELAY_ENABLED = "short_code_relay_enabled";
    private static final String KEY_RELAY_CONTROLLER = "relay_controller";

    private ForwardingPreferences() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static SharedPreferences runtimePrefs(Context context) {
        return context.getSharedPreferences(RUNTIME_PREFS, Context.MODE_PRIVATE);
    }

    static List<PhoneProfile> profiles(Context context) {
        SharedPreferences preferences = prefs(context);
        if (preferences.getBoolean(KEY_USER_DELETED, false)) return new ArrayList<>();

        String encoded = preferences.getString(KEY_PROFILES, null);
        if (encoded == null) return migrateLegacySettings(context);

        List<PhoneProfile> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(encoded);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object == null) continue;
                String number = object.optString("number", "").trim();
                if (number.isEmpty()) continue;

                PhoneProfile.IncomingMode incomingMode = PhoneProfile.IncomingMode.fromStored(
                        nullableString(object, "incomingMode"),
                        object.optBoolean("forwardEnabled", true),
                        object.optBoolean("codeOnly", true));
                PhoneProfile.OutgoingMode outgoingMode = PhoneProfile.OutgoingMode.fromStored(
                        nullableString(object, "outgoingMode"),
                        object.optBoolean("relayEnabled", true));

                result.add(new PhoneProfile(
                        number,
                        incomingMode,
                        readStringList(object.optJSONArray("incomingAllowList")),
                        readStringList(object.optJSONArray("incomingBlockList")),
                        object.optBoolean("codeCopyFollowup", true),
                        outgoingMode,
                        readStringList(object.optJSONArray("outgoingAllowList")),
                        readStringList(object.optJSONArray("outgoingBlockList"))));
            }
        } catch (JSONException ignored) {
            setStatus(context, "Saved phone profiles could not be read; open the app and save them again.");
        }
        return result;
    }

    static void saveProfile(Context context, PhoneProfile profile) {
        List<PhoneProfile> current = profiles(context);
        List<PhoneProfile> updated = new ArrayList<>();
        boolean replaced = false;
        for (PhoneProfile existing : current) {
            if (ShortCodeRelay.sameAddress(existing.number, profile.number)) {
                if (!replaced) updated.add(profile);
                replaced = true;
            } else {
                updated.add(existing);
            }
        }
        if (!replaced) updated.add(profile);
        saveProfiles(context, updated);
    }

    static void removeProfile(Context context, String number) {
        List<PhoneProfile> updated = new ArrayList<>();
        for (PhoneProfile profile : profiles(context)) {
            if (!ShortCodeRelay.sameAddress(profile.number, number)) updated.add(profile);
        }
        saveProfiles(context, updated);
        clearReplyRelay(context, number);
    }

    static void saveProfiles(Context context, List<PhoneProfile> profiles) {
        prefs(context).edit()
                .putString(KEY_PROFILES, encodeProfiles(profiles).toString())
                .putBoolean(KEY_USER_DELETED, false)
                .apply();
        new BackupManager(context).dataChanged();
    }

    static void deleteSavedSetup(Context context) {
        prefs(context).edit()
                .clear()
                .putBoolean(KEY_USER_DELETED, true)
                .apply();
        runtimePrefs(context).edit().clear().apply();
        new BackupManager(context).dataChanged();
    }

    static String status(Context context) {
        return runtimePrefs(context).getString(KEY_STATUS, "Not configured yet.");
    }

    static void setStatus(Context context, String status) {
        runtimePrefs(context).edit().putString(KEY_STATUS, status).apply();
    }

    static void startReplyRelay(Context context, String controllerNumber, String destination,
                                long nowMillis) {
        String key = runtimeKey(controllerNumber);
        runtimePrefs(context).edit()
                .putString("active_reply_destination_" + key, destination)
                .putLong("active_reply_expires_at_" + key, nowMillis + ShortCodeRelay.WINDOW_MS)
                .apply();
    }

    static String activeReplyDestination(Context context, String controllerNumber, long nowMillis) {
        String key = runtimeKey(controllerNumber);
        SharedPreferences preferences = runtimePrefs(context);
        String destinationKey = "active_reply_destination_" + key;
        String expiresKey = "active_reply_expires_at_" + key;
        long expiresAt = preferences.getLong(expiresKey, 0L);
        String destination = preferences.getString(destinationKey, "");
        if (destination == null || destination.isEmpty() || nowMillis > expiresAt) {
            if ((destination != null && !destination.isEmpty()) || expiresAt != 0L) {
                preferences.edit().remove(destinationKey).remove(expiresKey).apply();
            }
            return "";
        }
        return destination;
    }

    private static void clearReplyRelay(Context context, String controllerNumber) {
        String key = runtimeKey(controllerNumber);
        runtimePrefs(context).edit()
                .remove("active_reply_destination_" + key)
                .remove("active_reply_expires_at_" + key)
                .remove("active_short_code_" + key)
                .remove("active_short_code_expires_at_" + key)
                .apply();
    }

    private static String runtimeKey(String number) {
        String digits = number == null ? "" : number.replaceAll("[^0-9]", "");
        if (digits.length() == 11 && digits.startsWith("1")) digits = digits.substring(1);
        return digits.isEmpty() ? "unknown" : digits;
    }

    private static List<PhoneProfile> migrateLegacySettings(Context context) {
        SharedPreferences preferences = prefs(context);
        List<PhoneProfile> migrated = new ArrayList<>();

        String destination = safe(preferences.getString(KEY_DESTINATION, ""));
        String controller = safe(preferences.getString(KEY_RELAY_CONTROLLER, ""));
        boolean relayEnabled = preferences.getBoolean(KEY_SHORT_CODE_RELAY_ENABLED, true);

        if (!destination.isEmpty()) {
            boolean destinationControlsRelay = relayEnabled
                    && (controller.isEmpty() || ShortCodeRelay.sameAddress(destination, controller));
            migrated.add(new PhoneProfile(
                    destination,
                    preferences.getBoolean(KEY_ENABLED, false),
                    preferences.getBoolean(KEY_CODE_ONLY, true),
                    preferences.getBoolean(KEY_CODE_COPY_FOLLOWUP, true),
                    destinationControlsRelay));
        }

        if (relayEnabled && !controller.isEmpty()
                && (destination.isEmpty() || !ShortCodeRelay.sameAddress(destination, controller))) {
            migrated.add(new PhoneProfile(controller, false, true, false, true));
        }

        preferences.edit()
                .putString(KEY_PROFILES, encodeProfiles(migrated).toString())
                .remove(KEY_DESTINATION)
                .remove(KEY_ENABLED)
                .remove(KEY_CODE_ONLY)
                .remove(KEY_CODE_COPY_FOLLOWUP)
                .remove(KEY_SHORT_CODE_RELAY_ENABLED)
                .remove(KEY_RELAY_CONTROLLER)
                .apply();
        new BackupManager(context).dataChanged();
        return migrated;
    }

    private static JSONArray encodeProfiles(List<PhoneProfile> profiles) {
        JSONArray array = new JSONArray();
        if (profiles == null) return array;
        for (PhoneProfile profile : profiles) {
            if (profile == null || profile.number.isEmpty()) continue;
            JSONObject object = new JSONObject();
            try {
                object.put("number", profile.number);
                object.put("incomingMode", profile.incomingMode.name());
                object.put("incomingAllowList", toJsonArray(profile.incomingAllowList));
                object.put("incomingBlockList", toJsonArray(profile.incomingBlockList));
                object.put("codeCopyFollowup", profile.codeCopyFollowup);
                object.put("outgoingMode", profile.outgoingMode.name());
                object.put("outgoingAllowList", toJsonArray(profile.outgoingAllowList));
                object.put("outgoingBlockList", toJsonArray(profile.outgoingBlockList));
                array.put(object);
            } catch (JSONException ignored) {}
        }
        return array;
    }

    private static JSONArray toJsonArray(List<String> values) {
        JSONArray array = new JSONArray();
        if (values != null) for (String value : values) array.put(value);
        return array;
    }

    private static List<String> readStringList(JSONArray array) {
        if (array == null || array.length() == 0) return Collections.emptyList();
        ArrayList<String> result = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            String value = array.optString(i, "").trim();
            if (!value.isEmpty()) result.add(value);
        }
        return result;
    }

    private static String nullableString(JSONObject object, String key) {
        return object.has(key) ? object.optString(key, null) : null;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
