package com.tahlor.smsforwarder;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
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
    private Button authorizeButton;
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
            permissionStatus.post(() -> requestSmsPermissions(false));
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
        description.setText("Register each phone number once, then choose what that phone should receive or control.");
        description.setTextSize(16);
        description.setPadding(0, dp(10), 0, dp(12));
        content.addView(description);

        Button help = new Button(this);
        help.setText("Examples & help");
        help.setOnClickListener(v -> startActivity(new Intent(this, HelpActivity.class)));
        content.addView(help, fullWidth());

        TextView permissionHeading = heading("1. Authorize SMS access");
        permissionHeading.setPadding(0, dp(18), 0, dp(4));
        content.addView(permissionHeading);

        permissionStatus = new TextView(this);
        content.addView(permissionStatus);

        permissionHelp = new TextView(this);
        permissionHelp.setPadding(0, dp(6), 0, dp(6));
        content.addView(permissionHelp);

        authorizeButton = new Button(this);
        authorizeButton.setText("Authorize SMS access");
        authorizeButton.setOnClickListener(v -> requestSmsPermissions(true));
        content.addView(authorizeButton, fullWidth());

        TextView profilesHeading = heading("2. Registered phones");
        profilesHeading.setPadding(0, dp(18), 0, dp(6));
        content.addView(profilesHeading);

        profilesContainer = new LinearLayout(this);
        profilesContainer.setOrientation(LinearLayout.VERTICAL);
        content.addView(profilesContainer, fullWidth());

        TextView editorHeading = heading("Add or edit a phone");
        editorHeading.setPadding(0, dp(16), 0, dp(4));
        content.addView(editorHeading);

        numberInput = new EditText(this);
        numberInput.setHint("Phone number, e.g. +18015551234");
        numberInput.setInputType(InputType.TYPE_CLASS_PHONE);
        content.addView(numberInput, fullWidth());

        TextView behaviorLabel = new TextView(this);
        behaviorLabel.setText("What should this phone do?");
        behaviorLabel.setPadding(0, dp(8), 0, 0);
        content.addView(behaviorLabel);

        forwardEnabledCheck = new CheckBox(this);
        forwardEnabledCheck.setText("Receive forwarded SMS");
        content.addView(forwardEnabledCheck);

        codeOnlyCheck = new CheckBox(this);
        codeOnlyCheck.setText("Only forward messages containing a 6+ digit code");
        content.addView(codeOnlyCheck);

        codeCopyFollowupCheck = new CheckBox(this);
        codeCopyFollowupCheck.setText("Also send the extracted code alone for easy copying");
        content.addView(codeCopyFollowupCheck);

        relayEnabledCheck = new CheckBox(this);
        relayEnabledCheck.setText("Allow this phone to control [711-711] shortcodes");
        content.addView(relayEnabledCheck);

        TextView relayNote = new TextView(this);
        relayNote.setText("Example: from this registered phone, send [711711] SAVE to the phone running the app. The app sends only SAVE to 711711 and forwards replies back for 5 minutes.");
        relayNote.setPadding(dp(32), 0, 0, dp(8));
        content.addView(relayNote);

        Button saveProfile = new Button(this);
        saveProfile.setText("Save phone");
        saveProfile.setOnClickListener(v -> saveProfile());
        content.addView(saveProfile, fullWidth());

        Button clearEditor = new Button(this);
        clearEditor.setText("Add another phone");
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
        backupNote.setText("Registered-phone settings participate in Android backup/restore. Temporary 5-minute relay sessions are never backed up. SMS authorization must be granted again after uninstall.");
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
        warning.setText("No Internet permission. No SMS history access. Android 13+ may require the one-time App Info → ⋮ → Allow restricted settings step for this sideloaded APK.");
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
        if (numberInput == null) return;
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
            Toast.makeText(this, "Turn on forwarding or shortcode control for this phone.", Toast.LENGTH_LONG).show();
            return;
        }
        ForwardingPreferences.saveProfile(this, profile);
        ForwardingPreferences.setStatus(this, "Saved phone profile " + number + ".");
        resetProfileEditor();
        refreshProfiles();
        refreshStatus();
        if (!allSmsPermissionsGranted()) requestSmsPermissions(false);
        Toast.makeText(this, "Phone saved.", Toast.LENGTH_SHORT).show();
    }

    private void refreshProfiles() {
        profilesContainer.removeAllViews();
        List<PhoneProfile> profiles = ForwardingPreferences.profiles(this);
        if (profiles.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No phones registered yet. Add the downstream phone below.");
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
                .setMessage("This removes every registered phone. It does not revoke Android SMS permissions.")
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
                Toast.makeText(this, "SMS access is already authorized.", Toast.LENGTH_SHORT).show();
            refreshStatus();
            return;
        }
        requestPermissions(missing.toArray(new String[0]), SMS_PERMISSION_REQUEST);
    }

    private void showRestrictedSettingsHelp() {
        String message = "Android is still blocking one or both SMS permissions. Because this app is sideloaded, do this once:\n\n"
                + "1. Tap Open App Info.\n"
                + "2. Tap the top-right ⋮ menu.\n"
                + "3. Tap Allow restricted settings and confirm.\n"
                + "4. Come back here.\n\n"
                + "The app will automatically ask for Receive SMS and Send SMS again when you return.";
        new AlertDialog.Builder(this)
                .setTitle("One-time Android authorization")
                .setMessage(message)
                .setNegativeButton("Not now", null)
                .setNeutralButton("Retry permissions", (dialog, which) -> requestSmsPermissions(true))
                .setPositiveButton("Open App Info", (dialog, which) -> openAppSettings())
                .show();
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    showRestrictedSettingsHelp();
                } else {
                    Toast.makeText(this,
                            "Both Receive SMS and Send SMS permissions are required.",
                            Toast.LENGTH_LONG).show();
                }
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
            permissionHelp.setText("Ready. Incoming SMS can be received and forwarded.");
            if (authorizeButton != null) authorizeButton.setText("SMS access authorized ✓");
        } else {
            permissionHelp.setText("Tap Authorize SMS access. If Android blocks the prompt, the app will walk you through App Info → ⋮ → Allow restricted settings and retry automatically.");
            if (authorizeButton != null) authorizeButton.setText("Authorize SMS access");
        }
        forwardingStatus.setText("Last status: " + ForwardingPreferences.status(this));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
