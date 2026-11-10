package com.tahlor.smsforwarder;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.telephony.SmsManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.text.format.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Connected-edition event upload, job polling, carrier send, and notification processing. */
final class ConnectedSyncEngine {
    private static final String NOTIFICATION_CHANNEL = "sms_bridge_server_notifications";
    private static final int EVENT_BATCH_SIZE = 50;

    private ConnectedSyncEngine() {}

    static boolean sync(Context context) {
        if (Thread.currentThread().isInterrupted()) return false;
        ConnectedStore.Credentials credentials;
        try {
            credentials = ConnectedStore.credentials(context);
        } catch (RuntimeException e) {
            ConnectedOptions.setError(context, "Saved pairing credentials could not be read. Disconnect and pair again.");
            return false;
        }
        if (credentials == null) return true;

        boolean successful = true;
        try {
            drainAcknowledgements(context, credentials);
        } catch (Exception e) {
            ConnectedOptions.setError(context, safeMessage(e));
            successful = false;
        }
        try {
            uploadEvents(context, credentials);
        } catch (Exception e) {
            ConnectedOptions.setError(context, safeMessage(e));
            successful = false;
        }
        try {
            pollJobs(context, credentials);
        } catch (Exception e) {
            ConnectedOptions.setError(context, safeMessage(e));
            successful = false;
        }
        try {
            drainAcknowledgements(context, credentials);
        } catch (Exception e) {
            ConnectedOptions.setError(context, safeMessage(e));
            successful = false;
        }
        if (successful) {
            String now = DateFormat.getMediumDateFormat(context).format(new Date()) + " "
                    + DateFormat.getTimeFormat(context).format(new Date());
            ConnectedOptions.setLastSync(context, now);
        }
        return successful;
    }

    private static void uploadEvents(Context context, ConnectedStore.Credentials credentials)
            throws Exception {
        JSONArray queued = ConnectedStore.pendingEvents(context);
        JSONArray permitted = applyCurrentConsent(context, queued);
        ConnectedStore.replaceEvents(context, permitted);
        if (permitted.length() == 0) return;

        for (int start = 0; start < permitted.length(); start += EVENT_BATCH_SIZE) {
            if (Thread.currentThread().isInterrupted()) return;
            JSONArray batch = new JSONArray();
            List<String> batchIds = new ArrayList<>();
            for (int i = start; i < permitted.length() && i < start + EVENT_BATCH_SIZE; i++) {
                JSONObject event = permitted.optJSONObject(i);
                if (event == null) continue;
                batch.put(event);
                batchIds.add(event.optString("client_fingerprint",
                        event.optString("event_id", "")));
            }
            if (batch.length() == 0) continue;
            JSONObject payload = new JSONObject();
            payload.put("events", batch);
            ConnectedApi.Response response = ConnectedApi.request(context, credentials,
                    "/api/device/events", "POST", payload);
            requireOk(response);

            Set<String> accepted = acceptedEventIds(response, batchIds);
            if (!accepted.isEmpty()) ConnectedStore.removeEvents(context, accepted);
            if (accepted.size() != batchIds.size()) {
                throw new IllegalStateException("The server rejected one or more message events; they remain queued.");
            }
        }
    }

    private static JSONArray applyCurrentConsent(Context context, JSONArray queued)
            throws JSONException {
        boolean bodySync = ConnectedOptions.messageBodySync(context);
        boolean codeSync = ConnectedOptions.securityCodes(context);
        JSONArray permitted = new JSONArray();
        for (int i = 0; i < queued.length(); i++) {
            JSONObject event = queued.optJSONObject(i);
            if (event == null) continue;
            JSONObject payload = event.optJSONObject("payload");
            if (payload == null) continue;
            String body = payload.optString("body", "");
            String code = payload.isNull("extracted_code")
                    ? null : payload.optString("extracted_code", null);
            if (!bodySync && codeSync) {
                if (code == null || code.isEmpty()) code = MessageFilter.extractCode(body);
                if (code == null || code.isEmpty()) continue;
                payload.put("body", code);
                payload.put("extracted_code", code);
                payload.put("is_security_code", true);
            } else if (!bodySync) {
                continue;
            } else if (!codeSync) {
                payload.put("extracted_code", JSONObject.NULL);
                payload.put("is_security_code", false);
            }
            String sharedBody = payload.optString("body", "");
            String fingerprint = ConnectedEventFingerprint.create(
                    payload.optString("sender", ""), payload.optLong("timestamp"),
                    sharedBody, bodySync);
            payload.put("client_fingerprint", fingerprint);
            event.put("payload", payload);
            event.put("event_id", fingerprint);
            event.put("client_fingerprint", fingerprint);
            permitted.put(event);
        }
        return permitted;
    }

    private static Set<String> acceptedEventIds(ConnectedApi.Response response,
                                                List<String> ids) {
        Set<String> accepted = new HashSet<>();
        JSONArray outcomes = response.json == null ? null : response.json.optJSONArray("events");
        if (outcomes == null) {
            accepted.addAll(ids);
            return accepted;
        }
        for (int i = 0; i < outcomes.length() && i < ids.size(); i++) {
            JSONObject outcome = outcomes.optJSONObject(i);
            if (outcome == null) continue;
            String status = outcome.optString("status", "").toLowerCase(java.util.Locale.ROOT);
            if (status.equals("created") || status.equals("duplicate") || status.equals("accepted")) {
                accepted.add(ids.get(i));
            }
        }
        return accepted;
    }

    private static void pollJobs(Context context, ConnectedStore.Credentials credentials)
            throws Exception {
        if (Thread.currentThread().isInterrupted()) return;
        ConnectedApi.Response response = ConnectedApi.request(context, credentials,
                "/api/device/jobs?status=pending", "GET", null);
        requireOk(response);
        JSONArray jobs = response.json == null ? null : response.json.optJSONArray("jobs");
        if (jobs == null) return;
        for (int i = 0; i < jobs.length(); i++) {
            if (Thread.currentThread().isInterrupted()) return;
            JSONObject job = jobs.optJSONObject(i);
            if (job != null) processJob(context, job);
        }
    }

    private static void processJob(Context context, JSONObject job) {
        String jobId = firstString(job, "job_id", "id", "notification_id");
        if (jobId.isEmpty()) return;
        String kind = firstString(job, "kind", "type", "event_type").toLowerCase(java.util.Locale.ROOT);
        if (kind.isEmpty()) kind = "sms";
        if (kind.equals("sms") || kind.equals("text") || kind.equals("sms_send")) {
            processSmsJob(context, jobId, job);
        } else if (kind.equals("notification") || kind.equals("notify")
                || kind.equals("server_notification")) {
            processNotificationJob(context, jobId, job);
        } else {
            RemoteJobLedger.finish(context, jobId, false, null,
                    "This phone does not support the requested job type.");
        }
    }

    private static void processSmsJob(Context context, String jobId, JSONObject job) {
        String destination = firstString(job, "recipient", "phone_number", "to", "destination");
        String body = firstString(job, "body", "message", "text");
        boolean hasPermission = context.checkSelfPermission(Manifest.permission.SEND_SMS)
                == PackageManager.PERMISSION_GRANTED;
        String denial = RemoteSmsPolicy.denialReason(ConnectedOptions.remoteSms(context),
                hasPermission, ForwardingPreferences.profiles(context), destination);
        if (denial != null) {
            if (RemoteJobLedger.state(context, jobId).isEmpty()) {
                RemoteJobLedger.finish(context, jobId, false, null, denial);
            }
            return;
        }
        if (body.isEmpty()) {
            RemoteJobLedger.finish(context, jobId, false, null, "The SMS body is empty.");
            return;
        }

        ArrayList<String> parts;
        try {
            parts = SmsManager.getDefault().divideMessage(body);
        } catch (RuntimeException e) {
            RemoteJobLedger.finish(context, jobId, false, null,
                    "Android could not prepare this SMS for sending.");
            return;
        }
        if (parts == null || parts.isEmpty()) {
            RemoteJobLedger.finish(context, jobId, false, null,
                    "Android could not prepare this SMS for sending.");
            return;
        }
        String priorState = RemoteJobLedger.state(context, jobId);
        if (!priorState.isEmpty()) return;
        if (!RemoteJobLedger.beginSms(context, jobId, parts.size())) {
            ConnectedOptions.setError(context,
                    "A remote SMS job was not sent because its duplicate guard could not be saved.");
            return;
        }

        ArrayList<PendingIntent> sentIntents = new ArrayList<>();
        for (int i = 0; i < parts.size(); i++) {
            Intent callback = new Intent(context, SmsResultReceiver.class)
                    .setAction("com.tahlor.smsforwarder.SMS_SENT")
                    .setData(Uri.parse("smsbridge://job/" + Uri.encode(jobId) + "/part/" + i))
                    .putExtra(SmsResultReceiver.EXTRA_JOB_ID, jobId)
                    .putExtra(SmsResultReceiver.EXTRA_PART_INDEX, i);
            int requestCode = (jobId + ":" + i).hashCode();
            sentIntents.add(PendingIntent.getBroadcast(context, requestCode, callback,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        }
        try {
            SmsManager.getDefault().sendMultipartTextMessage(
                    destination, null, parts, sentIntents, null);
        } catch (SecurityException e) {
            RemoteJobLedger.finish(context, jobId, false, null,
                    "Android denied permission to send this SMS.");
            ConnectedSyncScheduler.scheduleImmediate(context);
        } catch (RuntimeException e) {
            RemoteJobLedger.finish(context, jobId, false, null,
                    "Android could not submit this SMS to the carrier.");
            ConnectedSyncScheduler.scheduleImmediate(context);
        }
    }

    private static void processNotificationJob(Context context, String jobId, JSONObject job) {
        if (!ConnectedOptions.serverNotifications(context)) {
            if (RemoteJobLedger.state(context, jobId).isEmpty()) {
                RemoteJobLedger.finish(context, jobId, false, null,
                        "Server notifications are turned off on this phone.");
            }
            return;
        }
        if (Build.VERSION.SDK_INT >= 33
                && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            if (RemoteJobLedger.state(context, jobId).isEmpty()) {
                RemoteJobLedger.finish(context, jobId, false, null,
                        "Android notification permission is not granted.");
            }
            return;
        }
        String title = firstString(job, "title", "name");
        String body = firstString(job, "body", "message", "text");
        if (title.isEmpty()) title = "SMS Bridge";
        if (body.isEmpty()) body = title;
        if (!RemoteJobLedger.beginNotification(context, jobId)) return;

        try {
            NotificationManager manager = (NotificationManager)
                    context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager == null) throw new IllegalStateException("Android notification service is unavailable.");
            if (Build.VERSION.SDK_INT >= 26) {
                manager.createNotificationChannel(new NotificationChannel(
                        NOTIFICATION_CHANNEL, "SMS Bridge", NotificationManager.IMPORTANCE_DEFAULT));
            }
            Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                    ? new Notification.Builder(context, NOTIFICATION_CHANNEL)
                    : new Notification.Builder(context);
            Notification notification = builder
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setStyle(new Notification.BigTextStyle().bigText(body))
                    .setAutoCancel(true)
                    .build();
            manager.notify(jobId.hashCode(), notification);
            RemoteJobLedger.finish(context, jobId, true, "POSTED", null);
        } catch (Exception e) {
            RemoteJobLedger.finish(context, jobId, false, null,
                    "Android could not post this notification.");
        }
        ConnectedSyncScheduler.scheduleImmediate(context);
    }

    private static void drainAcknowledgements(Context context,
                                              ConnectedStore.Credentials credentials)
            throws Exception {
        for (RemoteJobLedger.Ack ack : RemoteJobLedger.pendingAcks(context)) {
            if (Thread.currentThread().isInterrupted()) return;
            JSONObject payload = new JSONObject();
            payload.put("job_id", ack.jobId);
            payload.put("status", ack.status.equals("completed") ? "completed" : "failed");
            if (ack.carrierAck != null) payload.put("carrier_ack", ack.carrierAck);
            if (ack.error != null) payload.put("error", ack.error);
            ConnectedApi.Response response = ConnectedApi.request(context, credentials,
                    "/api/device/ack", "POST", payload);
            requireOk(response);
            RemoteJobLedger.markAckSent(context, ack.jobId);
        }
    }

    private static String firstString(JSONObject json, String... keys) {
        for (String key : keys) {
            String value = json.optString(key, "").trim();
            if (!value.isEmpty() && !value.equals("null")) return value;
        }
        return "";
    }

    private static void requireOk(ConnectedApi.Response response) {
        if (response == null || !response.ok()) {
            throw new IllegalStateException(response == null
                    ? "The server did not respond." : response.message());
        }
    }

    private static String safeMessage(Exception error) {
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) return "Connected sync failed. Try again.";
        return message;
    }
}
