package com.tahlor.smsforwarder;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public final class MainActivity extends Activity {
    private static final int SMS_PERMISSION_REQUEST = 1001;
    private static final String LATEST_APK_URL =
            "https://taylorarchibald.com/apks/sms-code-forwarder-latest.apk";

    private EditText destinationInput;
    private CheckBox codeOnlyCheck;
    private CheckBox codeCopyFollowupCheck;
    private CheckBox shortCodeRelayCheck;
    private EditText relayControllerInput;
    private CheckBox enabledCheck;
    private TextView permissionStatus;
    private TextView forwardingStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("SMS Code Forwarder");
        setContentView(buildContent());
        loadSettings();
        refreshStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (permissionStatus != null) refreshStatus();
    }

    private View buildContent() {
        int pad = dp(20);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("SMS Code Forwarder");
        title.setTextSize(24);
        content.addView(title);

        TextView description = new TextView(this);
        description.setText("Forwards newly received SMS messages and supports a trusted 5-minute shortcode relay.");
        description.setTextSize(16);
        description.setPadding(0, dp(10), 0, dp(18));
        content.addView(description);

        TextView destinationLabel = new TextView(this);
        destinationLabel.setText("Destination phone number");
        content.addView(destinationLabel);

        destinationInput = new EditText(this);
        destinationInput.setHint("e.g. +18015551234");
        destinationInput.setInputType(InputType.TYPE_CLASS_PHONE);
        content.addView(destinationInput, fullWidth());

        codeOnlyCheck = new CheckBox(this);
        codeOnlyCheck.setText("Only forward messages containing 6+ consecutive digits");
        codeOnlyCheck.setPadding(0, dp(8), 0, 0);
        content.addView(codeOnlyCheck);

        codeCopyFollowupCheck = new CheckBox(this);
        codeCopyFollowupCheck.setText("Also send the extracted code as a second SMS for easy copying");
        content.addView(codeCopyFollowupCheck);

        shortCodeRelayCheck = new CheckBox(this);
        shortCodeRelayCheck.setText("Enable trusted [123-456] shortcode relay");
        shortCodeRelayCheck.setPadding(0, dp(12), 0, 0);
        content.addView(shortCodeRelayCheck);

        TextView controllerLabel = new TextView(this);
        controllerLabel.setText("Trusted relay-controller number (blank = destination number)");
        content.addView(controllerLabel);

        relayControllerInput = new EditText(this);
        relayControllerInput.setHint("Leave blank to use destination number");
        relayControllerInput.setInputType(InputType.TYPE_CLASS_PHONE);
        content.addView(relayControllerInput, fullWidth());

        TextView relayNote = new TextView(this);
        relayNote.setText("Example: text [711-711] Y. The app sends Y to shortcode 711711 and forwards replies back for 5 minutes as [711-711] <reply>. The undashed [711711] form is also accepted for compatibility.");
        relayNote.setPadding(dp(32), 0, 0, dp(8));
        content.addView(relayNote);

        enabledCheck = new CheckBox(this);
        enabledCheck.setText("Enable normal forwarding");
        content.addView(enabledCheck);

        Button save = new Button(this);
        save.setText("Save settings");
        save.setOnClickListener(v -> saveSettings());
        content.addView(save, fullWidth());

        Button permissions = new Button(this);
        permissions.setText("Grant SMS permissions");
        permissions.setOnClickListener(v -> requestSmsPermissions());
        content.addView(permissions, fullWidth());

        Button update = new Button(this);
        update.setText("Update app");
        update.setOnClickListener(v -> openLatestApk());
        content.addView(update, fullWidth());

        TextView updateNote = new TextView(this);
        updateNote.setText("Opens the latest Archimedes-signed APK in your browser. The forwarder itself keeps no Internet permission.");
        updateNote.setPadding(0, 0, 0, dp(8));
        content.addView(updateNote);

        permissionStatus = new TextView(this);
        permissionStatus.setPadding(0, dp(14), 0, 0);
        content.addView(permissionStatus);

        forwardingStatus = new TextView(this);
        forwardingStatus.setPadding(0, dp(8), 0, 0);
        forwardingStatus.setTextIsSelectable(true);
        content.addView(forwardingStatus);

        TextView warning = new TextView(this);
        warning.setText("Security note: only the configured trusted controller can issue shortcode relay commands. This app has no Internet permission and does not read SMS history.");
        warning.setPadding(0, dp(18), 0, 0);
        content.addView(warning);

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(content);
        return scrollView;
    }

    private LinearLayout.LayoutParams fullWidth() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private void loadSettings() {
        destinationInput.setText(ForwardingPreferences.destination(this));
        codeOnlyCheck.setChecked(ForwardingPreferences.codeOnly(this));
        codeCopyFollowupCheck.setChecked(ForwardingPreferences.codeCopyFollowup(this));
        shortCodeRelayCheck.setChecked(ForwardingPreferences.shortCodeRelayEnabled(this));
        relayControllerInput.setText(ForwardingPreferences.relayControllerOverride(this));
        enabledCheck.setChecked(ForwardingPreferences.enabled(this));
    }

    private void saveSettings() {
        String destination = destinationInput.getText().toString().trim();
        String controller = relayControllerInput.getText().toString().trim();
        if (enabledCheck.isChecked() && destination.isEmpty()) {
            Toast.makeText(this, "Enter a destination number before enabling normal forwarding.", Toast.LENGTH_LONG).show();
            return;
        }
        if (shortCodeRelayCheck.isChecked() && destination.isEmpty() && controller.isEmpty()) {
            Toast.makeText(this, "Enter a destination or trusted controller number before enabling shortcode relay.", Toast.LENGTH_LONG).show();
            return;
        }

        ForwardingPreferences.saveSettings(this, destination, enabledCheck.isChecked(),
                codeOnlyCheck.isChecked(), codeCopyFollowupCheck.isChecked(),
                shortCodeRelayCheck.isChecked(), controller);
        if (enabledCheck.isChecked() || shortCodeRelayCheck.isChecked()) {
            ForwardingPreferences.setStatus(this, "SMS features enabled; waiting for messages.");
            requestSmsPermissions();
        } else {
            ForwardingPreferences.setStatus(this, "SMS features disabled.");
        }
        refreshStatus();
        Toast.makeText(this, "Settings saved.", Toast.LENGTH_SHORT).show();
    }

    private void requestSmsPermissions() {
        List<String> missing = new ArrayList<>();
        if (checkSelfPermission(Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED)
            missing.add(Manifest.permission.RECEIVE_SMS);
        if (checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED)
            missing.add(Manifest.permission.SEND_SMS);
        if (missing.isEmpty()) {
            Toast.makeText(this, "SMS permissions are already granted.", Toast.LENGTH_SHORT).show();
            refreshStatus();
            return;
        }
        requestPermissions(missing.toArray(new String[0]), SMS_PERMISSION_REQUEST);
    }

    private void openLatestApk() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(LATEST_APK_URL)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No browser is available to open the update link.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == SMS_PERMISSION_REQUEST) {
            refreshStatus();
            if (!allSmsPermissionsGranted())
                Toast.makeText(this, "Both receive and send SMS permissions are required.", Toast.LENGTH_LONG).show();
        }
    }

    private boolean allSmsPermissionsGranted() {
        return checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED;
    }

    private void refreshStatus() {
        boolean receive = checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED;
        boolean send = checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED;
        permissionStatus.setText("Permissions — receive: " + (receive ? "granted" : "missing")
                + ", send: " + (send ? "granted" : "missing"));
        forwardingStatus.setText("Status: " + ForwardingPreferences.status(this));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
