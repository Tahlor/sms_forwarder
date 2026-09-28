package com.tahlor.smsforwarder;

import android.app.backup.BackupManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.format.DateFormat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

final class ForwardingPreferences {
    private static final String PREFS = "sms_forwarder";
    private static final String RUNTIME_PREFS = "sms_forwarder_runtime";
    private static final String KEY_PROFILES = "phone_profiles_v1";
    private static final String KEY_USER_DELETED = "user_deleted_setup";
    private static final String KEY_STATUS = "status";
    private static final String KEY_ACTIVITY = "activity_log_v1";
    private static final int MAX_ACTIVITY_ENTRIES = 12;

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
                result.add(readProfile(object, number));
            }
        } catch (JSONException ignored) {
            setStatus(context, "Saved phone profiles could not be read; open the app and save them again.");
        }
        return result;
    }

    private static PhoneProfile readProfile(JSONObject object, String number) {
        if (object.has("incomingAuthorization") || object.has("incomingPreference")) {
            return new PhoneProfile(
                    number,
                    PhoneProfile.IncomingAuthorization.fromStored(
                            nullableString(object, "incomingAuthorization")),
                    readStringList(object.optJSONArray("incomingAuthorizedSenders")),
                    readStringList(object.optJSONArray("incomingBlockedSenders")),
                    PhoneProfile.IncomingPreference.fromStored(
                            nullableString(object, "incomingPreference")),
                    readStringList(object.optJSONArray("incomingPreferredSenders")),
                    object.optBoolean("codeCopyFollowup", true),
                    PhoneProfile.OutgoingMode.fromStored(
                            nullableString(object, "outgoingMode"),
                            object.optBoolean("relayEnabled", false)),
                    readStringList(object.optJSONArray("outgoingAllowList")),
                    readStringList(object.optJSONArray("outgoingBlockList")),
                    object.optBoolean("allowRemoteCommands", false));
        }

        String oldMode = nullableString(object, "incomingMode");
        if (oldMode == null) {
            boolean enabled = object.optBoolean("forwardEnabled", true);
            oldMode = enabled
                    ? (object.optBoolean("codeOnly", true) ? "SECURITY_CODES" : "ALL")
                    : "OFF";
        }

        List<String> oldAllow = readStringList(object.optJSONArray("incomingAllowList"));
        List<String> oldBlock = readStringList(object.optJSONArray("incomingBlockList"));
        PhoneProfile.IncomingAuthorization authorization;
        PhoneProfile.IncomingPreference preference;
        switch (oldMode) {
            case "ALL":
                authorization = PhoneProfile.IncomingAuthorization.ANY;
                preference = PhoneProfile.IncomingPreference.ALL_AUTHORIZED;
                break;
            case "SECURITY_CODES":
                authorization = PhoneProfile.IncomingAuthorization.ANY;
                preference = PhoneProfile.IncomingPreference.SECURITY_CODES;
                break;
            case "SELECTED":
                authorization = PhoneProfile.IncomingAuthorization.SELECTED;
                preference = PhoneProfile.IncomingPreference.ALL_AUTHORIZED;
                break;
            default:
                authorization = PhoneProfile.IncomingAuthorization.NONE;
                preference = PhoneProfile.IncomingPreference.OFF;
        }

        return new PhoneProfile(
                number,
                authorization,
                oldAllow,
                oldBlock,
                preference,
                Collections.emptyList(),
                object.optBoolean("codeCopyFollowup", true),
                PhoneProfile.OutgoingMode.fromStored(
                        nullableString(object, "outgoingMode"),
                        object.optBoolean("relayEnabled", true)),
                readStringList(object.optJSONArray("outgoingAllowList")),
                readStringList(object.optJSONArray("outgoingBlockList")),
                object.optBoolean("allowRemoteCommands", false));
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

    static synchronized void setStatus(Context context, String status) {
        String safeStatus = status == null ? "" : status.trim();
        runtimePrefs(context).edit().putString(KEY_STATUS, safeStatus).apply();
        appendActivity(context, safeStatus);
    }

    static synchronized void logActivity(Context context, String activity) {
        appendActivity(context, activity == null ? "" : activity.trim());
    }

    private static void appendActivity(Context context, String activity) {
        if (activity.isEmpty()) return;
        SharedPreferences preferences = runtimePrefs(context);
        JSONArray next = new JSONArray();
        String timestamp = DateFormat.getMediumDateFormat(context).format(new Date())
                + " " + DateFormat.getTimeFormat(context).format(new Date());
        next.put(timestamp + " — " + activity);

        String encoded = preferences.getString(KEY_ACTIVITY, "[]");
        try {
            JSONArray current = new JSONArray(encoded == null ? "[]" : encoded);
            for (int i = 0; i < current.length() && next.length() < MAX_ACTIVITY_ENTRIES; i++) {
                String item = current.optString(i, "");
                if (!item.isEmpty()) next.put(item);
            }
        } catch (JSONException ignored) {}
        preferences.edit().putString(KEY_ACTIVITY, next.toString()).apply();
    }

    static List<String> recentActivity(Context context) {
        String encoded = runtimePrefs(context).getString(KEY_ACTIVITY, "[]");
        ArrayList<String> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(encoded == null ? "[]" : encoded);
            for (int i = 0; i < array.length(); i++) {
                String value = array.optString(i, "").trim();
                if (!value.isEmpty()) result.add(value);
            }
        } catch (JSONException ignored) {}
        return result;
    }

    static PhoneProfile addIncomingAuthorizedSender(
            Context context, String controllerNumber, String target) {
        String cleanTarget = safe(target);
        if (cleanTarget.isEmpty()) return null;
        return updateProfile(context, controllerNumber, profile -> {
            ArrayList<String> authorized = new ArrayList<>(profile.incomingAuthorizedSenders);
            removeEquivalent(authorized, cleanTarget);
            authorized.add(cleanTarget);

            ArrayList<String> blocked = new ArrayList<>(profile.incomingBlockedSenders);
            removeEquivalent(blocked, cleanTarget);

            PhoneProfile.IncomingAuthorization authorization =
                    profile.incomingAuthorization == PhoneProfile.IncomingAuthorization.NONE
                            ? PhoneProfile.IncomingAuthorization.SELECTED
                            : profile.incomingAuthorization;

            return profile
                    .withIncomingAuthorization(authorization)
                    .withIncomingAuthorizedSenders(authorized)
                    .withIncomingBlockedSenders(blocked);
        });
    }

    static PhoneProfile removeIncomingAuthorizedSender(
            Context context, String controllerNumber, String target) {
        String cleanTarget = safe(target);
        if (cleanTarget.isEmpty()) return null;
        return updateProfile(context, controllerNumber, profile -> {
            ArrayList<String> authorized = new ArrayList<>(profile.incomingAuthorizedSenders);
            removeEquivalent(authorized, cleanTarget);
            ArrayList<String> preferred = new ArrayList<>(profile.incomingPreferredSenders);
            removeEquivalent(preferred, cleanTarget);
            return profile
                    .withIncomingAuthorizedSenders(authorized)
                    .withIncomingPreferredSenders(preferred);
        });
    }

    static PhoneProfile addIncomingBlock(
            Context context, String controllerNumber, String target) {
        String cleanTarget = safe(target);
        if (cleanTarget.isEmpty()) return null;
        return updateProfile(context, controllerNumber, profile -> {
            ArrayList<String> blocked = new ArrayList<>(profile.incomingBlockedSenders);
            removeEquivalent(blocked, cleanTarget);
            blocked.add(cleanTarget);

            ArrayList<String> authorized = new ArrayList<>(profile.incomingAuthorizedSenders);
            removeEquivalent(authorized, cleanTarget);
            ArrayList<String> preferred = new ArrayList<>(profile.incomingPreferredSenders);
            removeEquivalent(preferred, cleanTarget);

            return profile
                    .withIncomingBlockedSenders(blocked)
                    .withIncomingAuthorizedSenders(authorized)
                    .withIncomingPreferredSenders(preferred);
        });
    }

    static PhoneProfile removeIncomingBlock(
            Context context, String controllerNumber, String target) {
        String cleanTarget = safe(target);
        if (cleanTarget.isEmpty()) return null;
        return updateProfile(context, controllerNumber, profile -> {
            ArrayList<String> blocked = new ArrayList<>(profile.incomingBlockedSenders);
            removeEquivalent(blocked, cleanTarget);
            return profile.withIncomingBlockedSenders(blocked);
        });
    }

    static PhoneProfile addIncomingPreferredSender(
            Context context, String controllerNumber, String target) {
        String cleanTarget = safe(target);
        if (cleanTarget.isEmpty()) return null;
        return updateProfile(context, controllerNumber, profile -> {
            if (!profile.permitsIncomingAuthorization(cleanTarget)) return profile;
            ArrayList<String> preferred = new ArrayList<>(profile.incomingPreferredSenders);
            removeEquivalent(preferred, cleanTarget);
            preferred.add(cleanTarget);
            return profile.withIncomingPreferredSenders(preferred);
        });
    }

    static PhoneProfile removeIncomingPreferredSender(
            Context context, String controllerNumber, String target) {
        String cleanTarget = safe(target);
        if (cleanTarget.isEmpty()) return null;
        return updateProfile(context, controllerNumber, profile -> {
            ArrayList<String> preferred = new ArrayList<>(profile.incomingPreferredSenders);
            removeEquivalent(preferred, cleanTarget);
            return profile.withIncomingPreferredSenders(preferred);
        });
    }

    static PhoneProfile setIncomingAuthorization(
            Context context, String controllerNumber,
            PhoneProfile.IncomingAuthorization authorization) {
        if (authorization == null) return null;
        return updateProfile(
                context, controllerNumber,
                profile -> profile.withIncomingAuthorization(authorization));
    }

    static PhoneProfile setIncomingPreference(
            Context context, String controllerNumber,
            PhoneProfile.IncomingPreference preference) {
        if (preference == null) return null;
        return updateProfile(
                context, controllerNumber,
                profile -> profile.withIncomingPreference(preference));
    }

    private interface ProfileUpdater {
        PhoneProfile update(PhoneProfile profile);
    }

    private static PhoneProfile updateProfile(
            Context context, String controllerNumber,
            ProfileUpdater updater) {
        List<PhoneProfile> current = profiles(context);
        List<PhoneProfile> updatedList = new ArrayList<>();
        PhoneProfile updatedProfile = null;
        boolean replaced = false;
        for (PhoneProfile profile : current) {
            if (!replaced && ShortCodeRelay.sameAddress(profile.number, controllerNumber)) {
                updatedProfile = updater.update(profile);
                updatedList.add(updatedProfile);
                replaced = true;
            } else {
                updatedList.add(profile);
            }
        }
        if (updatedProfile != null) saveProfiles(context, updatedList);
        return updatedProfile;
    }

    private static void removeEquivalent(List<String> values, String target) {
        for (int i = values.size() - 1; i >= 0; i--) {
            if (PhoneProfile.addressesMatch(values.get(i), target)) values.remove(i);
        }
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
            boolean forwardEnabled = preferences.getBoolean(KEY_ENABLED, false);
            boolean codeOnly = preferences.getBoolean(KEY_CODE_ONLY, true);
            boolean destinationControlsRelay = relayEnabled
                    && (controller.isEmpty() || ShortCodeRelay.sameAddress(destination, controller));
            migrated.add(new PhoneProfile(
                    destination,
                    forwardEnabled ? PhoneProfile.IncomingAuthorization.ANY
                            : PhoneProfile.IncomingAuthorization.NONE,
                    Collections.emptyList(),
                    Collections.emptyList(),
                    forwardEnabled
                            ? (codeOnly ? PhoneProfile.IncomingPreference.SECURITY_CODES
                                    : PhoneProfile.IncomingPreference.ALL_AUTHORIZED)
                            : PhoneProfile.IncomingPreference.OFF,
                    Collections.emptyList(),
                    preferences.getBoolean(KEY_CODE_COPY_FOLLOWUP, true),
                    destinationControlsRelay ? PhoneProfile.OutgoingMode.SHORT_CODES
                            : PhoneProfile.OutgoingMode.OFF,
                    Collections.emptyList(),
                    Collections.emptyList(),
                    false));
        }

        if (relayEnabled && !controller.isEmpty()
                && (destination.isEmpty() || !ShortCodeRelay.sameAddress(destination, controller))) {
            migrated.add(new PhoneProfile(
                    controller,
                    PhoneProfile.IncomingAuthorization.NONE,
                    Collections.emptyList(),
                    Collections.emptyList(),
                    PhoneProfile.IncomingPreference.OFF,
                    Collections.emptyList(),
                    false,
                    PhoneProfile.OutgoingMode.SHORT_CODES,
                    Collections.emptyList(),
                    Collections.emptyList(),
                    false));
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
                object.put("modelVersion", 3);
                object.put("number", profile.number);
                object.put("incomingAuthorization", profile.incomingAuthorization.name());
                object.put("incomingAuthorizedSenders",
                        toJsonArray(profile.incomingAuthorizedSenders));
                object.put("incomingBlockedSenders",
                        toJsonArray(profile.incomingBlockedSenders));
                object.put("incomingPreference", profile.incomingPreference.name());
                object.put("incomingPreferredSenders",
                        toJsonArray(profile.incomingPreferredSenders));
                object.put("codeCopyFollowup", profile.codeCopyFollowup);
                object.put("outgoingMode", profile.outgoingMode.name());
                object.put("outgoingAllowList", toJsonArray(profile.outgoingAllowList));
                object.put("outgoingBlockList", toJsonArray(profile.outgoingBlockList));
                object.put("allowRemoteCommands", profile.allowRemoteCommands);
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
