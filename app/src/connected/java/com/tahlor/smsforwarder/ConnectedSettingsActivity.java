package com.tahlor.smsforwarder;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

/** Pairing, opt-in controls, status, sync, and local/server disconnect controls. */
public final class ConnectedSettingsActivity extends Activity {
    private static final int NOTIFICATION_PERMISSION_REQUEST = 4201;

    private EditText pairingUri;
    private EditText deviceLabel;
    private EditText phoneNumber;
    private CheckBox confirmPhone;
    private CheckBox bodySync;
    private CheckBox securityCodes;
    private CheckBox remoteSms;
    private CheckBox serverNotifications;
    private TextView connectionStatus;
    private TextView lastSyncStatus;
    private TextView errorStatus;
    private Button pairButton;
    private Button syncButton;
    private Button disconnectButton;
    private boolean notificationOptInPending;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setTitle("Self-hosted server");
        setContentView(buildContent());
        String uri = getIntent().getStringExtra(ConnectedMainActivity.EXTRA_PAIRING_URI);
        if (uri == null && getIntent().getData() != null) uri = getIntent().getData().toString();
        if (uri != null) pairingUri.setText(uri);
        ConnectedSyncScheduler.schedulePeriodic(this);
        refreshStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (connectionStatus != null) refreshStatus();
    }

    private View buildContent() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(20);
        content.setPadding(padding, dp(18), padding, dp(30));
        scroll.setFillViewport(true);
        scroll.addView(content);

        TextView title = text("Self-hosted server", 26, true);
        content.addView(title);
        TextView intro = text(
                "Pairing is optional. Your existing SMS forwarding profiles keep their current rules. "
                        + "This app does not read SMS history.", 15, false);
        intro.setPadding(0, dp(6), 0, dp(14));
        content.addView(intro);

        connectionStatus = text("", 16, true);
        connectionStatus.setPadding(0, 0, 0, dp(4));
        content.addView(connectionStatus);
        lastSyncStatus = text("", 14, false);
        content.addView(lastSyncStatus);
        errorStatus = text("", 14, false);
        errorStatus.setTextColor(0xFFB3261E);
        errorStatus.setPadding(0, dp(4), 0, dp(12));
        content.addView(errorStatus);

        LinearLayout pairingCard = card();
        TextView pairingTitle = text("Pair this phone", 19, true);
        pairingCard.addView(pairingTitle);
        TextView pairingHint = text(
                "Scan the server QR code or paste its smsbridge://pair URI. "
                        + "Only HTTPS servers are accepted, except localhost for development.", 14, false);
        pairingHint.setPadding(0, dp(5), 0, dp(8));
        pairingCard.addView(pairingHint);

        Button scan = new Button(this);
        scan.setText("Scan pairing QR code");
        scan.setOnClickListener(v -> scanQr());
        pairingCard.addView(scan, fullWidth());

        pairingUri = input("Paste pairing URI", InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        pairingUri.setMinLines(2);
        pairingUri.setSingleLine(false);
        pairingCard.addView(pairingUri, fullWidth());

        deviceLabel = input("Device label", InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        deviceLabel.setText(Build.MODEL == null ? "Android phone" : Build.MODEL);
        pairingCard.addView(deviceLabel, fullWidth());

        phoneNumber = input("This phone's number, including country code", InputType.TYPE_CLASS_PHONE);
        pairingCard.addView(phoneNumber, fullWidth());

        confirmPhone = new CheckBox(this);
        confirmPhone.setText("I confirm this is my phone number and approve pairing to this server.");
        pairingCard.addView(confirmPhone);

        pairButton = new Button(this);
        pairButton.setText("Pair phone");
        pairButton.setOnClickListener(v -> beginPairing());
        pairingCard.addView(pairButton, fullWidth());
        content.addView(pairingCard, cardParams());

        LinearLayout optionsCard = card();
        TextView optionsTitle = text("Local permissions", 19, true);
        optionsCard.addView(optionsTitle);
        TextView optionsHint = text(
                "Each option starts off. Turn on only the data and actions you want this phone to share.",
                14, false);
        optionsHint.setPadding(0, dp(4), 0, dp(6));
        optionsCard.addView(optionsHint);

        bodySync = addOption(optionsCard, "Sync new message bodies",
                "Uploads the full contents of new SMS messages received after pairing.");
        securityCodes = addOption(optionsCard, "Sync detected security codes",
                "Uploads only a detected code when full message body sync is off.");
        remoteSms = addOption(optionsCard, "Allow remote SMS sends",
                "A server job can send only when this is on, Android grants Send SMS, and an existing local outgoing rule allows the recipient.");
        serverNotifications = addOption(optionsCard, "Allow server notifications",
                "Lets this phone display notification jobs from the paired server.");
        content.addView(optionsCard, cardParams());

        LinearLayout syncCard = card();
        TextView syncTitle = text("Sync schedule", 19, true);
        syncCard.addView(syncTitle);
        TextView syncHint = text(
                "Sync now runs in the background. Automatic checks use Android's best-effort "
                        + "15-minute periodic schedule; this is not real-time until proper push infrastructure is available.",
                14, false);
        syncHint.setPadding(0, dp(5), 0, dp(8));
        syncCard.addView(syncHint);

        syncButton = new Button(this);
        syncButton.setText("Sync now");
        syncButton.setOnClickListener(v -> {
            if (ConnectedStore.credentials(this) == null) {
                Toast.makeText(this, "Pair with a server first.", Toast.LENGTH_SHORT).show();
                return;
            }
            ConnectedSyncScheduler.scheduleImmediate(this);
            Toast.makeText(this, "Sync queued. Android will run it when network is available.",
                    Toast.LENGTH_LONG).show();
        });
        syncCard.addView(syncButton, fullWidth());
        content.addView(syncCard, cardParams());

        disconnectButton = new Button(this);
        disconnectButton.setText("Disconnect and revoke device");
        disconnectButton.setOnClickListener(v -> confirmDisconnect());
        LinearLayout.LayoutParams disconnectParams = fullWidth();
        disconnectParams.topMargin = dp(12);
        content.addView(disconnectButton, disconnectParams);

        TextView footer = text(
                "Credentials are encrypted with an Android Keystore key in private app storage. "
                        + "Connected edition backups are disabled.", 13, false);
        footer.setPadding(0, dp(12), 0, 0);
        content.addView(footer);

        initializeOptions();
        return scroll;
    }

    private CheckBox addOption(LinearLayout parent, String label, String description) {
        CheckBox option = new CheckBox(this);
        option.setText(label);
        option.setTextSize(16);
        parent.addView(option);
        TextView hint = text(description, 13, false);
        hint.setPadding(dp(4), 0, 0, dp(8));
        parent.addView(hint);
        return option;
    }

    private void initializeOptions() {
        bodySync.setChecked(ConnectedOptions.messageBodySync(this));
        securityCodes.setChecked(ConnectedOptions.securityCodes(this));
        remoteSms.setChecked(ConnectedOptions.remoteSms(this));
        serverNotifications.setChecked(ConnectedOptions.serverNotifications(this));

        bodySync.setOnCheckedChangeListener((button, checked) -> {
            ConnectedOptions.setMessageBodySync(this, checked);
            clearEventsIfNoConsent();
        });
        securityCodes.setOnCheckedChangeListener((button, checked) -> {
            ConnectedOptions.setSecurityCodes(this, checked);
            clearEventsIfNoConsent();
        });
        remoteSms.setOnCheckedChangeListener((button, checked) ->
                ConnectedOptions.setRemoteSms(this, checked));
        serverNotifications.setOnCheckedChangeListener((button, checked) -> {
            if (checked && Build.VERSION.SDK_INT >= 33
                    && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                notificationOptInPending = true;
                serverNotifications.setOnCheckedChangeListener(null);
                serverNotifications.setChecked(false);
                serverNotifications.setOnCheckedChangeListener((ignored, enabled) -> onNotificationOptInChanged(enabled));
                requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS},
                        NOTIFICATION_PERMISSION_REQUEST);
                return;
            }
            ConnectedOptions.setServerNotifications(this, checked);
        });
    }

    private void onNotificationOptInChanged(boolean enabled) {
        if (enabled && Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            notificationOptInPending = true;
            serverNotifications.setOnCheckedChangeListener(null);
            serverNotifications.setChecked(false);
            serverNotifications.setOnCheckedChangeListener((ignored, checked) -> onNotificationOptInChanged(checked));
            requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS},
                    NOTIFICATION_PERMISSION_REQUEST);
            return;
        }
        ConnectedOptions.setServerNotifications(this, enabled);
    }

    private void clearEventsIfNoConsent() {
        if (!ConnectedOptions.messageBodySync(this) && !ConnectedOptions.securityCodes(this)) {
            ConnectedStore.clearQueuedEvents(this);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != NOTIFICATION_PERMISSION_REQUEST) return;
        boolean granted = grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        if (notificationOptInPending) {
            notificationOptInPending = false;
            ConnectedOptions.setServerNotifications(this, granted);
            serverNotifications.setOnCheckedChangeListener(null);
            serverNotifications.setChecked(granted);
            serverNotifications.setOnCheckedChangeListener(
                    (button, checked) -> onNotificationOptInChanged(checked));
        }
        if (!granted) Toast.makeText(this,
                "Android notification permission is needed for server notifications.",
                Toast.LENGTH_LONG).show();
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result != null) {
            if (result.getContents() != null) {
                pairingUri.setText(result.getContents());
                Toast.makeText(this, "QR scanned. Confirm your phone number before pairing.",
                        Toast.LENGTH_LONG).show();
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void scanQr() {
        new IntentIntegrator(this)
                .setDesiredBarcodeFormats(java.util.Collections.singletonList("QR_CODE"))
                .setPrompt("Scan the server's SMS Bridge pairing QR code")
                .setBeepEnabled(false)
                .initiateScan();
    }

    private void beginPairing() {
        PairingUri parsed;
        String number = normalizePhone(phoneNumber.getText().toString());
        try {
            parsed = PairingUri.parse(pairingUri.getText().toString());
        } catch (IllegalArgumentException e) {
            pairingUri.setError(e.getMessage());
            return;
        }
        if (number == null) {
            phoneNumber.setError("Enter a phone number with country code (7 to 15 digits).");
            return;
        }
        if (!confirmPhone.isChecked()) {
            confirmPhone.setError("Confirm this is your phone and approve pairing.");
            return;
        }
        String label = deviceLabel.getText().toString().trim();
        if (label.isEmpty()) label = "Android phone";
        final String deviceLabelValue = label;
        final String phoneValue = number;
        new AlertDialog.Builder(this)
                .setTitle("Confirm server pairing")
                .setMessage("Pair this phone number " + phoneValue + " to " + parsed.server
                        + "? The server will be able to use only the sync and remote-action options you enable here.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Pair this phone", (dialog, which) ->
                        claim(parsed, deviceLabelValue, phoneValue))
                .show();
    }

    private void claim(PairingUri pairing, String label, String phone) {
        pairButton.setEnabled(false);
        pairButton.setText("Pairing…");
        new Thread(() -> {
            String error = null;
            try {
                ConnectedApi.Response response = ConnectedApi.claim(
                        pairing.server, pairing.token, label, phone);
                if (!response.ok() || response.json == null) {
                    error = response.message();
                } else {
                    String deviceId = response.json.optString("device_id", "").trim();
                    String deviceToken = response.json.optString("device_token", "").trim();
                    if (deviceId.isEmpty() || deviceToken.isEmpty()) {
                        error = "The server response did not include device credentials.";
                    } else {
                        ConnectedStore.saveCredentials(
                                this, pairing.server, deviceId, deviceToken);
                    }
                }
            } catch (Exception e) {
                error = e.getMessage() == null ? "Pairing failed. Check the server and try again." : e.getMessage();
            }
            final String result = error;
            runOnUiThread(() -> {
                pairButton.setEnabled(true);
                pairButton.setText("Pair phone");
                if (result == null) {
                    pairingUri.setText("");
                    confirmPhone.setChecked(false);
                    ConnectedOptions.setError(this, "");
                    ConnectedSyncScheduler.schedulePeriodic(this);
                    ConnectedSyncScheduler.scheduleImmediate(this);
                    Toast.makeText(this, "Phone paired to the server.", Toast.LENGTH_LONG).show();
                } else {
                    ConnectedOptions.setError(this, result);
                }
                refreshStatus();
            });
        }, "sms-bridge-claim").start();
    }

    private void confirmDisconnect() {
        ConnectedStore.Credentials credentials;
        try {
            credentials = ConnectedStore.credentials(this);
        } catch (RuntimeException e) {
            credentials = null;
        }
        if (credentials == null) {
            Toast.makeText(this, "This phone is not paired.", Toast.LENGTH_SHORT).show();
            return;
        }
        ConnectedStore.Credentials selected = credentials;
        new AlertDialog.Builder(this)
                .setTitle("Disconnect this phone?")
                .setMessage("The app will ask the server to revoke this device, then erase its local credentials and queued messages.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Disconnect", (dialog, which) -> disconnect(selected))
                .show();
    }

    private void disconnect(ConnectedStore.Credentials credentials) {
        disconnectButton.setEnabled(false);
        new Thread(() -> {
            String revokeError = null;
            try {
                org.json.JSONObject payload = new org.json.JSONObject();
                payload.put("device_id", credentials.deviceId);
                ConnectedApi.Response response = ConnectedApi.request(this, credentials,
                        "/api/device/revoke", "POST", payload);
                if (!response.ok()) revokeError = response.message();
            } catch (Exception e) {
                revokeError = e.getMessage() == null ? "Server revocation could not be confirmed." : e.getMessage();
            }
            ConnectedStore.clearCredentials(this);
            RemoteJobLedger.clear(this);
            ConnectedSyncScheduler.cancel(this);
            final String result = revokeError;
            runOnUiThread(() -> {
                disconnectButton.setEnabled(true);
                ConnectedOptions.setError(this, result == null ? "" :
                        "Local disconnect is complete, but server revocation could not be confirmed: " + result);
                refreshStatus();
            });
        }, "sms-bridge-revoke").start();
    }

    private void refreshStatus() {
        ConnectedStore.Credentials credentials = null;
        String credentialError = null;
        try {
            credentials = ConnectedStore.credentials(this);
        } catch (RuntimeException e) {
            credentialError = "Saved credentials could not be read. Disconnect and pair again.";
        }
        boolean paired = credentials != null;
        if (paired) {
            connectionStatus.setText("Paired to " + credentials.server + "\nDevice: " + credentials.deviceId);
            connectionStatus.setTextColor(0xFF168243);
        } else {
            connectionStatus.setText("Not paired");
            connectionStatus.setTextColor(0xFF17171B);
        }
        String lastSync = ConnectedOptions.lastSync(this);
        lastSyncStatus.setText("Last successful sync: " + (lastSync.isEmpty() ? "Never" : lastSync));
        String error = credentialError == null ? ConnectedOptions.lastError(this) : credentialError;
        errorStatus.setText(error.isEmpty() ? "" : "Last error: " + error);
        pairButton.setVisibility(paired ? View.GONE : View.VISIBLE);
        pairingUri.setVisibility(paired ? View.GONE : View.VISIBLE);
        deviceLabel.setVisibility(paired ? View.GONE : View.VISIBLE);
        phoneNumber.setVisibility(paired ? View.GONE : View.VISIBLE);
        confirmPhone.setVisibility(paired ? View.GONE : View.VISIBLE);
        View scan = ((ViewGroup) pairButton.getParent()).getChildAt(2);
        scan.setVisibility(paired ? View.GONE : View.VISIBLE);
        syncButton.setEnabled(paired);
        disconnectButton.setVisibility(paired ? View.VISIBLE : View.GONE);
    }

    private String normalizePhone(String input) {
        String number = input == null ? "" : input.trim().replaceAll("[\\s().-]", "");
        return number.matches("\\+?[1-9][0-9]{6,14}") ? number : null;
    }

    private LinearLayout card() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        int p = dp(15);
        view.setPadding(p, p, p, p);
        view.setBackgroundColor(0xFFF1F2F5);
        return view;
    }

    private TextView text(String value, int size, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(0xFF17171B);
        if (bold) view.setTypeface(null, android.graphics.Typeface.BOLD);
        return view;
    }

    private EditText input(String hint, int type) {
        EditText view = new EditText(this);
        view.setHint(hint);
        view.setInputType(type);
        view.setTextSize(15);
        return view;
    }

    private LinearLayout.LayoutParams fullWidth() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams params = fullWidth();
        params.topMargin = dp(12);
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
