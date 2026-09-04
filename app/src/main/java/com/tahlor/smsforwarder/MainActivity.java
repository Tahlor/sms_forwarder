package com.tahlor.smsforwarder;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
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

    private EditText numberInput;
    private CheckBox forwardEnabledCheck;
    private CheckBox codeOnlyCheck;
    private CheckBox codeCopyFollowupCheck;
    private CheckBox relayEnabledCheck;
    private LinearLayout profilesContainer;
    private TextView permissionStatus;
    private TextView permissionHelp;
    private TextView forwardingStatus;
    private boolean returningFromAppSettings;
    private boolean requestedPermissionsThisLaunch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("SMS Code Forwarder");
        View content = buildContent();
        setContentView(content);
        resetProfileEditor();
        refreshProfiles();
        refreshStatus();
        content.post(this::requestMissingPermissionsOnLaunch);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (permissionStatus != null) refreshStatus();
        if (returningFromAppSettings) {
            returningFromAppSettings = false;
            if (!allSmsPermissionsGranted()) {
                permissionStatus.post(() -> requestSmsPermissions(false));
            }
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
        description.setText("Register the phone numbers that should receive forwarded messages or control shortcode replies. Each number has its own settings.");
        description.setTextSize(16);
        description.setPadding(0, dp(10), 0, dp(16));
        content.addView(description);

        TextView permissionHeading = heading("SMS access");
        content.addView(permissionHeading);

        permissionStatus = new TextView(this);
        content.addView(permissionStatus);

        permissionHelp = new TextView(this);
        permissionHelp.setPadding(0, dp(6), 0, dp(6));
        content.addView(permissionHelp);

        Button permissions = new Button(this);
        permissions.setText("Grant send + receive SMS permissions");
        permissions.setOnClickListener(v -> requestSmsPermissions(true));
        content.addView(permissions, fullWidth());

        Button restrictedSettings = new Button(this);
        restrictedSettings.setText("Open App Info / Allow restricted settings");
        restrictedSettings.setOnClickListener(v -> openAppSettings());
        content.addView(restrictedSettings, fullWidth());

        TextView profilesHeading = heading("Registered phones");
        profilesHeading.setPadding(0, dp(18), 0, dp(6));
        content.addView(profilesHeading);

        profilesContainer = new LinearLayout(this);
        profilesContainer.setOrientation(LinearLayout.VERTICAL);
        content.addView(profilesContainer, fullWidth());

        TextView editorHeading = heading("Add or edit phone");
        editorHeading.setPadding(0, dp(16), 0, dp(4));
        content.addView(editorHeading);

        numberInput = new EditText(this);
        numberInput.setHint("Phone number, e.g. +18015551234");
        numberInput.setInputType(InputType.TYPE_CLASS_PHONE);
        content.addView(numberInput, fullWidth());

        forwardEnabledCheck = new CheckBox(this);
        forwardEnabledCheck.setText("Forward SMS to this number");
        content.addView(forwardEnabledCheck);

        codeOnlyCheck = new CheckBox(this);
        codeOnlyCheck.setText("Only forward messages containing 6+ consecutive digits");
        content.addView(codeOnlyCheck);

        codeCopyFollowupCheck = new CheckBox(this);
        codeCopyFollowupCheck.setText("Also send extracted code as a second SMS");
        content.addView(codeCopyFollowupCheck);

        relayEnabledCheck = new CheckBox(this);
        relayEnabledCheck.setText("Allow this number to control [711-711] shortcode relay");
        content.addView(relayEnabledCheck);

        TextView relayNote = new TextView(this);
        relayNote.setText("Example: from a relay-enabled number, text [711-711] Y. This phone sends Y to 711711 and forwards replies back to that same registered number for 5 minutes.");
        relayNote.setPadding(dp(32), 0, 0, dp(8));
        content.addView(relayNote);

        Button saveProfile = new Button(this);
        saveProfile.setText("Add / update phone");
        saveProfile.setOnClickListener(v -> saveProfile());
        content.addView(saveProfile, fullWidth());

        Button clearEditor = new Button(this);
        clearEditor.setText("Clear editor");
        clearEditor.setOnClickListener(v -> resetProfileEditor());
        content.addView(clearEditor, fullWidth());

        TextView maintenanceHeading = heading("Maintenance");
        maintenanceHeading.setPadding(0, dp(18), 0, dp(6));
        content.addView(maintenanceHeading);

        Button update = new Button(this);
        update.setText("Update app");
        update.setOnClickListener(v -> openLatestApk());
        content.addView(update, fullWidth());

        TextView backupNote = new TextView(this);
        backupNote.setText("Registered-phone settings participate in Android backup/restore so they can come back after reinstall. Temporary 5-minute relay sessions are never backed up. SMS permissions must be granted again after uninstall.");
        backupNote.setPadding(0, dp(4), 0, dp(8));
        content.addView(backupNote);

        Button deleteSetup = new Button(this);
        deleteSetup.setText("Delete saved setup");
        deleteSetup.setOnClickListener(v -> confirmDeleteSavedSetup());
        content.addView(deleteSetup, fullWidth());

        forwardingStatus = new TextView(this);
        forwardingStatus.setPadding(0, dp(14), 0, 0);
        forwardingStatus.setTextIsSelectable(true);
        content.addView(forwardingStatus);

        TextView warning = new TextView(this);
        warning.setText("This app has no Internet permission and does not read SMS history. Android may require one-time restricted-settings approval because SMS permissions are sensitive.");
        warning.setPadding(0, dp(16), 0, 0);
        content.addView(warning);

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(content);
        return scrollView;
    }

    private TextView heading(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(19);
        return view;
    }

    private LinearLayout.LayoutParams fullWidth() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private void resetProfileEditor() {
        numberInput.setText("");
        forwardEnabledCheck.setChecked(true);
        codeOnlyCheck.setChecked(true);
        codeCopyFollowupCheck.setChecked(true);
        relayEnabledCheck.setChecked(true);
    }

    private void editProfile(PhoneProfile profile) {
        numberInput.setText(profile.number);
        forwardEnabledCheck.setChecked(profile.forwardEnabled);
        codeOnlyCheck.setChecked(profile.codeOnly);
        codeCopyFollowupCheck.setChecked(profile.codeCopyFollowup);
        relayEnabledCheck.setChecked(profile.relayEnabled);
        numberInput.requestFocus();
    }

    private void saveProfile() {
        String number = numberInput.getText().toString().trim();
        if (number.isEmpty()) {
            Toast.makeText(this, "Enter a phone number.", Toast.LENGTH_LONG).show();
            return;
        }
        PhoneProfile profile = new PhoneProfile(number,
                forwardEnabledCheck.isChecked(),
                codeOnlyCheck.isChecked(),
                codeCopyFollowupCheck.isChecked(),
                relayEnabledCheck.isChecked());
        if (!profile.hasAnyFeatureEnabled()) {
            Toast.makeText(this, "Turn on forwarding or shortcode relay for this phone.", Toast.LENGTH_LONG).show();
            return;
        }
        ForwardingPreferences.saveProfile(this, profile);
        ForwardingPreferences.setStatus(this, "Saved phone profile " + number + ".");
        resetProfileEditor();
        refreshProfiles();
        refreshStatus();
        if (!allSmsPermissionsGranted()) requestSmsPermissions(false);
        Toast.makeText(this, "Phone profile saved.", Toast.LENGTH_SHORT).show();
    }

    private void refreshProfiles() {
        profilesContainer.removeAllViews();
        List<PhoneProfile> profiles = ForwardingPreferences.profiles(this);
        if (profiles.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No phones registered yet.");
            profilesContainer.addView(empty);
            return;
        }

        for (PhoneProfile profile : profiles) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(0, dp(6), 0, dp(10));

            TextView summary = new TextView(this);
            summary.setText(profile.summary());
            summary.setTextIsSelectable(true);
            card.addView(summary);

            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);

            Button edit = new Button(this);
            edit.setText("Edit");
            edit.setOnClickListener(v -> editProfile(profile));
            actions.addView(edit, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            Button remove = new Button(this);
            remove.setText("Remove");
            remove.setOnClickListener(v -> confirmRemoveProfile(profile));
            actions.addView(remove, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            card.addView(actions, fullWidth());
            profilesContainer.addView(card, fullWidth());
        }
    }

    private void confirmRemoveProfile(PhoneProfile profile) {
        new AlertDialog.Builder(this)
                .setTitle("Remove phone?")
                .setMessage("Remove " + profile.number + " and its forwarding/relay settings?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Remove", (dialog, which) -> {
                    ForwardingPreferences.removeProfile(this, profile.number);
                    refreshProfiles();
                    refreshStatus();
                })
                .show();
    }

    private void confirmDeleteSavedSetup() {
        new AlertDialog.Builder(this)
                .setTitle("Delete saved setup?")
                .setMessage("This removes every registered phone and requests that Android backup the deleted state. It does not revoke SMS permissions.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    ForwardingPreferences.deleteSavedSetup(this);
                    resetProfileEditor();
                    refreshProfiles();
                    refreshStatus();
                    Toast.makeText(this, "Saved setup deleted.", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void requestMissingPermissionsOnLaunch() {
        if (!allSmsPermissionsGranted() && !requestedPermissionsThisLaunch) {
            requestedPermissionsThisLaunch = true;
            requestSmsPermissions(false);
        }
    }

    private void requestSmsPermissions(boolean userInitiated) {
        List<String> missing = new ArrayList<>();
        if (checkSelfPermission(Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED)
            missing.add(Manifest.permission.RECEIVE_SMS);
        if (checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED)
            missing.add(Manifest.permission.SEND_SMS);
        if (missing.isEmpty()) {
            if (userInitiated)
                Toast.makeText(this, "Send and receive SMS permissions are already granted.", Toast.LENGTH_SHORT).show();
            refreshStatus();
            return;
        }
        requestPermissions(missing.toArray(new String[0]), SMS_PERMISSION_REQUEST);
    }

    private void openAppSettings() {
        try {
            returningFromAppSettings = true;
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            returningFromAppSettings = false;
            Toast.makeText(this, "Could not open App Info.", Toast.LENGTH_LONG).show();
        }
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
            if (!allSmsPermissionsGranted()) {
                Toast.makeText(this,
                        "SMS access is still blocked. For a sideloaded app, open App Info, tap the top-right menu, choose Allow restricted settings, then return here.",
                        Toast.LENGTH_LONG).show();
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
        permissionStatus.setText("Receive SMS: " + (receive ? "granted" : "missing")
                + "   •   Send SMS: " + (send ? "granted" : "missing"));
        if (receive && send) {
            permissionHelp.setText("SMS access is ready.");
        } else {
            permissionHelp.setText("Both permissions are required. If Android refuses the permission prompt because this APK was sideloaded: open App Info below → top-right ⋮ menu → Allow restricted settings → return to the app. The permission prompt will retry automatically.");
        }
        forwardingStatus.setText("Status: " + ForwardingPreferences.status(this));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
