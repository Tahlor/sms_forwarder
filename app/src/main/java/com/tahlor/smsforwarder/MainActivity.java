package com.tahlor.smsforwarder;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
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

    private EditText destinationInput;
    private CheckBox codeOnlyCheck;
    private CheckBox codeCopyFollowupCheck;
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
        if (permissionStatus != null) {
            refreshStatus();
        }
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
        description.setText("Forwards newly received SMS messages to another phone number. Code-only mode is enabled by default and matches 6+ consecutive digits.");
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

        TextView followupNote = new TextView(this);
        followupNote.setText("When a 6+ digit code is found, this sends one additional SMS whose entire body is just the code. Disable this if you only want the full forwarded message. Carrier SMS charges may apply.");
        followupNote.setPadding(dp(32), 0, 0, dp(6));
        content.addView(followupNote);

        enabledCheck = new CheckBox(this);
        enabledCheck.setText("Enable forwarding");
        content.addView(enabledCheck);

        Button save = new Button(this);
        save.setText("Save settings");
        save.setOnClickListener(v -> saveSettings());
        content.addView(save, fullWidth());

        Button permissions = new Button(this);
        permissions.setText("Grant SMS permissions");
        permissions.setOnClickListener(v -> requestSmsPermissions());
        content.addView(permissions, fullWidth());

        permissionStatus = new TextView(this);
        permissionStatus.setPadding(0, dp(14), 0, 0);
        content.addView(permissionStatus);

        forwardingStatus = new TextView(this);
        forwardingStatus.setPadding(0, dp(8), 0, 0);
        forwardingStatus.setTextIsSelectable(true);
        content.addView(forwardingStatus);

        TextView warning = new TextView(this);
        warning.setText("Security note: forwarded verification codes are sensitive. Verify the destination number before enabling forwarding. This app has no Internet permission and does not read SMS history.");
        warning.setPadding(0, dp(18), 0, 0);
        content.addView(warning);

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(content);
        return scrollView;
    }

    private LinearLayout.LayoutParams fullWidth() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private void loadSettings() {
        destinationInput.setText(ForwardingPreferences.destination(this));
        codeOnlyCheck.setChecked(ForwardingPreferences.codeOnly(this));
        codeCopyFollowupCheck.setChecked(ForwardingPreferences.codeCopyFollowup(this));
        enabledCheck.setChecked(ForwardingPreferences.enabled(this));
    }

    private void saveSettings() {
        String destination = destinationInput.getText().toString().trim();
        if (enabledCheck.isChecked() && destination.isEmpty()) {
            Toast.makeText(this, "Enter a destination number before enabling forwarding.", Toast.LENGTH_LONG).show();
            return;
        }

        ForwardingPreferences.saveSettings(
                this,
                destination,
                enabledCheck.isChecked(),
                codeOnlyCheck.isChecked(),
                codeCopyFollowupCheck.isChecked());
        if (enabledCheck.isChecked()) {
            ForwardingPreferences.setStatus(this, "Forwarding enabled; waiting for a matching SMS.");
            requestSmsPermissions();
        } else {
            ForwardingPreferences.setStatus(this, "Forwarding disabled.");
        }
        refreshStatus();
        Toast.makeText(this, "Settings saved.", Toast.LENGTH_SHORT).show();
    }

    private void requestSmsPermissions() {
        List<String> missing = new ArrayList<>();
        if (checkSelfPermission(Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
            missing.add(Manifest.permission.RECEIVE_SMS);
        }
        if (checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            missing.add(Manifest.permission.SEND_SMS);
        }

        if (missing.isEmpty()) {
            Toast.makeText(this, "SMS permissions are already granted.", Toast.LENGTH_SHORT).show();
            refreshStatus();
            return;
        }
        requestPermissions(missing.toArray(new String[0]), SMS_PERMISSION_REQUEST);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == SMS_PERMISSION_REQUEST) {
            refreshStatus();
            if (!allSmsPermissionsGranted()) {
                Toast.makeText(this, "Both receive and send SMS permissions are required for forwarding.", Toast.LENGTH_LONG).show();
            }
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
