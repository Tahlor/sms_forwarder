package com.tahlor.smsforwarder;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/** Durable job deduplication and deferred acknowledgements across worker retries. */
final class RemoteJobLedger {
    private static final String PREFS = "connected_job_ledger";

    static final class Ack {
        final String jobId;
        final String status;
        final String carrierAck;
        final String error;

        Ack(String jobId, String status, String carrierAck, String error) {
            this.jobId = jobId;
            this.status = status;
            this.carrierAck = carrierAck;
            this.error = error;
        }
    }

    private RemoteJobLedger() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String key(String jobId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(jobId.getBytes(StandardCharsets.UTF_8));
            StringBuilder value = new StringBuilder("job_");
            for (byte item : digest) value.append(String.format(java.util.Locale.ROOT, "%02x", item));
            return value.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static JSONObject read(Context context, String jobId) {
        String saved = prefs(context).getString(key(jobId), null);
        if (saved == null) return null;
        try {
            return new JSONObject(saved);
        } catch (JSONException e) {
            return null;
        }
    }

    private static boolean write(Context context, String jobId, JSONObject state) {
        return prefs(context).edit().putString(key(jobId), state.toString()).commit();
    }

    /** True only for the first attempt; sending jobs are never sent again after a retry. */
    static synchronized boolean beginSms(Context context, String jobId, int parts) {
        if (jobId == null || jobId.isEmpty() || parts < 1) return false;
        if (read(context, jobId) != null) return false;
        JSONObject state = new JSONObject();
        JSONArray results = new JSONArray();
        for (int i = 0; i < parts; i++) results.put(0);
        try {
            state.put("job_id", jobId);
            state.put("state", "sending");
            state.put("parts", parts);
            state.put("results", results);
            state.put("ack_pending", false);
            return write(context, jobId, state);
        } catch (JSONException e) {
            return false;
        }
    }

    static synchronized boolean beginNotification(Context context, String jobId) {
        if (jobId == null || jobId.isEmpty() || read(context, jobId) != null) return false;
        setTerminal(context, jobId, "processing", null, null, false);
        return true;
    }

    static synchronized void recordPartResult(Context context, String jobId,
                                              int index, boolean success) {
        JSONObject state = read(context, jobId);
        if (state == null || !"sending".equals(state.optString("state"))) return;
        JSONArray results = state.optJSONArray("results");
        if (results == null || index < 0 || index >= results.length()) return;
        if (results.optInt(index, 0) != 0) return;
        try {
            results.put(index, success ? 1 : -1);
        } catch (JSONException e) {
            return;
        }
        boolean complete = true;
        boolean allSuccess = true;
        for (int i = 0; i < results.length(); i++) {
            int result = results.optInt(i, 0);
            if (result == 0) complete = false;
            if (result != 1) allSuccess = false;
        }
        if (!complete) {
            try {
                state.put("results", results);
                write(context, jobId, state);
            } catch (JSONException ignored) {}
            return;
        }
        setTerminal(context, jobId, allSuccess ? "completed" : "failed",
                allSuccess ? "RESULT_OK" : "RESULT_ERROR",
                allSuccess ? null : "Carrier reported an SMS send failure.", true);
    }

    static synchronized void finish(Context context, String jobId, boolean success,
                                    String carrierAck, String error) {
        setTerminal(context, jobId, success ? "completed" : "failed",
                carrierAck, error, true);
    }

    private static void setTerminal(Context context, String jobId, String status,
                                    String carrierAck, String error, boolean ackPending) {
        JSONObject state = read(context, jobId);
        if (state == null) state = new JSONObject();
        try {
            state.put("job_id", jobId);
            state.put("state", status);
            state.put("carrier_ack", carrierAck == null ? JSONObject.NULL : carrierAck);
            state.put("error", error == null ? JSONObject.NULL : error);
            state.put("ack_pending", ackPending);
            write(context, jobId, state);
        } catch (JSONException ignored) {}
    }

    static synchronized List<Ack> pendingAcks(Context context) {
        List<Ack> result = new ArrayList<>();
        for (Object value : prefs(context).getAll().values()) {
            if (!(value instanceof String)) continue;
            try {
                JSONObject state = new JSONObject((String) value);
                if (!state.optBoolean("ack_pending", false)) continue;
                String id = state.optString("job_id", "");
                if (id.isEmpty()) continue;
                String carrierAck = state.isNull("carrier_ack")
                        ? null : state.optString("carrier_ack", null);
                String error = state.isNull("error") ? null : state.optString("error", null);
                result.add(new Ack(id, state.optString("state", "failed"), carrierAck, error));
            } catch (JSONException ignored) {}
        }
        return result;
    }

    static synchronized void markAckSent(Context context, String jobId) {
        JSONObject state = read(context, jobId);
        if (state == null) return;
        try {
            state.put("ack_pending", false);
            write(context, jobId, state);
        } catch (JSONException ignored) {}
    }

    static synchronized String state(Context context, String jobId) {
        JSONObject state = read(context, jobId);
        return state == null ? "" : state.optString("state", "");
    }

    static synchronized void clear(Context context) {
        prefs(context).edit().clear().commit();
    }
}
