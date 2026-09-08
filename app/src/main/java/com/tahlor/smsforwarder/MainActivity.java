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
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public final class MainActivity extends Activity {
    private static final int SMS_PERMISSION_REQUEST = 1001;
    private static final String LATEST_APK_URL =
            "https://taylorarchibald.com/apks/sms-code-forwarder-latest.apk";

    private static final String[] INCOMING_MODE_LABELS = {
            "All messages",
            "Secure codes only",
            "Selected senders only",
            "Nothing"
    };
    private static final String[] OUTGOING_MODE_LABELS = {
            "Any number",
            "Short codes only",
            "Selected numbers only",
            "Nothing"
    };

    private EditText numberInput;
    private Spinner incomingModeSpinner;
    private Spinner outgoingModeSpinner;
    private EditText incomingAllowInput;
    private EditText incomingBlockInput;
    private EditText outgoingAllowInput;
    private EditText outgoingBlockInput;
    private CheckBox codeCopyFollowupCheck;
    private LinearLayout incomingRulesContainer;
    private LinearLayout outgoingRulesContainer;
    private Button incomingRulesButton;
    private Button outgoingRulesButton;
    private LinearLayout profilesContainer;
    private TextView permissionStatus;
    private TextView permissionHelp;
    private TextView forwardingStatus;
    private Button authorizeButton;
    private String editingOriginalNumber = "";
    private boolean returningFromAppSettings;
    private boolean requestedPermissionsThisLaunch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("SMS Forwarder");
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

        TextView title = heading("SMS Forwarder", 27);
        content.addView(title);

        TextView description = body("Forward texts between this phone and one or more downstream phones.");
        description.setPadding(0, dp(6), 0, dp(16));
        content.addView(description);

        permissionStatus = heading("Checking SMS access…", 17);
        content.addView(permissionStatus);

        permissionHelp = body("");
        permissionHelp.setPadding(0, dp(4), 0, dp(8));
        content.addView(permissionHelp);

        authorizeButton = new Button(this);
        authorizeButton.setText("Authorize SMS access");
        authorizeButton.setOnClickListener(v -> requestSmsPermissions(true));
        content.addView(authorizeButton, fullWidth());

        TextView profilesHeading = heading("Downstream phones", 20);
        profilesHeading.setPadding(0, dp(22), 0, dp(8));
        content.addView(profilesHeading);

        profilesContainer = new LinearLayout(this);
        profilesContainer.setOrientation(LinearLayout.VERTICAL);
        content.addView(profilesContainer, fullWidth());

        TextView editorHeading = heading("Forwarding setup", 20);
        editorHeading.setPadding(0, dp(20), 0, dp(8));
        content.addView(editorHeading);

        addLabel(content, "Downstream phone");
        numberInput = new EditText(this);
        numberInput.setHint("+1 801 555 1234");
        numberInput.setInputType(InputType.TYPE_CLASS_PHONE);
        content.addView(numberInput, fullWidth());

        TextView incomingHeading = heading("Incoming messages → downstream", 18);
        incomingHeading.setPadding(0, dp(20), 0, dp(6));
        content.addView(incomingHeading);

        addLabel(content, "Forward");
        incomingModeSpinner = makeSpinner(INCOMING_MODE_LABELS);
        content.addView(incomingModeSpinner, fullWidth());

        codeCopyFollowupCheck = new CheckBox(this);
        codeCopyFollowupCheck.setText("Send detected security code as a second, copyable text");
        codeCopyFollowupCheck.setPadding(0, dp(6), 0, 0);
        content.addView(codeCopyFollowupCheck);

        incomingRulesButton = new Button(this);
        incomingRulesButton.setText("Sender rules ▸");
        incomingRulesButton.setOnClickListener(v -> toggleRules(incomingRulesContainer, incomingRulesButton, "Sender rules"));
        content.addView(incomingRulesButton, fullWidth());

        incomingRulesContainer = buildRulesContainer(
                "The allow list is used by “Selected senders only.” The block list is always enforced and wins over the allow list.",
                true);
        content.addView(incomingRulesContainer, fullWidth());

        TextView outgoingHeading = heading("Downstream → outgoing SMS", 18);
        outgoingHeading.setPadding(0, dp(20), 0, dp(6));
        content.addView(outgoingHeading);

        addLabel(content, "Can send to");
        outgoingModeSpinner = makeSpinner(OUTGOING_MODE_LABELS);
        content.addView(outgoingModeSpinner, fullWidth());

        TextView replyHelp = body("From the downstream phone, send [711711] SAVE or [8015551234] your message to this phone. Replies from that destination return to the downstream phone for 5 minutes.");
        replyHelp.setPadding(0, dp(8), 0, dp(4));
        content.addView(replyHelp);

        outgoingRulesButton = new Button(this);
        outgoingRulesButton.setText("Destination rules ▸");
        outgoingRulesButton.setOnClickListener(v -> toggleRules(outgoingRulesContainer, outgoingRulesButton, "Destination rules"));
        content.addView(outgoingRulesButton, fullWidth());

        outgoingRulesContainer = buildRulesContainer(
                "The allow list is used by “Selected numbers only.” The block list is always enforced and wins over the allow list.",
                false);
        content.addView(outgoingRulesContainer, fullWidth());

        Button saveProfile = new Button(this);
        saveProfile.setText("Save forwarding setup");
        saveProfile.setOnClickListener(v -> saveProfile());
        LinearLayout.LayoutParams saveParams = fullWidth();
        saveParams.topMargin = dp(18);
        content.addView(saveProfile, saveParams);

        Button clearEditor = new Button(this);
        clearEditor.setText("Clear / add another phone");
        clearEditor.setOnClickListener(v -> resetProfileEditor());
        content.addView(clearEditor, fullWidth());

        TextView toolsHeading = heading("Help & maintenance", 18);
        toolsHeading.setPadding(0, dp(22), 0, dp(6));
        content.addView(toolsHeading);

        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.HORIZONTAL);

        Button help = new Button(this);
        help.setText("Examples & help");
        help.setOnClickListener(v -> startActivity(new Intent(this, HelpActivity.class)));
        tools.addView(help, weighted());

        Button update = new Button(this);
        update.setText("Update app");
        update.setOnClickListener(v -> openLatestApk());
        tools.addView(update, weighted());
        content.addView(tools, fullWidth());

        Button deleteSetup = new Button(this);
        deleteSetup.setText("Delete saved setup");
        deleteSetup.setOnClickListener(v -> confirmDeleteSavedSetup());
        content.addView(deleteSetup, fullWidth());

        forwardingStatus = body("");
        forwardingStatus.setPadding(0, dp(14), 0, 0);
        forwardingStatus.setTextIsSelectable(true);
        content.addView(forwardingStatus);

        TextView warning = body("No Internet permission and no SMS-history access. Android 13+ may require the one-time App Info → ⋮ → Allow restricted settings step for this sideloaded APK.");
        warning.setPadding(0, dp(14), 0, 0);
        content.addView(warning);

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(content);
        return scrollView;
    }

    private LinearLayout buildRulesContainer(String note, boolean incoming) {
        LinearLayout rules = new LinearLayout(this);
        rules.setOrientation(LinearLayout.VERTICAL);
        rules.setPadding(dp(12), dp(6), dp(12), dp(10));
        rules.setVisibility(View.GONE);

        TextView noteView = body(note);
        noteView.setPadding(0, 0, 0, dp(4));
        rules.addView(noteView);

        EditText allow = new EditText(this);
        allow.setHint(incoming ? "Allow list — sender numbers, one per line" : "Allow list — destination numbers, one per line");
        allow.setMinLines(2);
        allow.setGravity(android.view.Gravity.TOP);
        allow.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        rules.addView(allow, fullWidth());

        EditText block = new EditText(this);
        block.setHint(incoming ? "Block list — sender numbers, one per line" : "Block list — destination numbers, one per line");
        block.setMinLines(2);
        block.setGravity(android.view.Gravity.TOP);
        block.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        rules.addView(block, fullWidth());

        if (incoming) {
            incomingAllowInput = allow;
            incomingBlockInput = block;
        } else {
            outgoingAllowInput = allow;
            outgoingBlockInput = block;
        }
        return rules;
    }

    private Spinner makeSpinner(String[] labels) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        return spinner;
    }

    private void toggleRules(LinearLayout container, Button button, String title) {
        boolean opening = container.getVisibility() != View.VISIBLE;
        container.setVisibility(opening ? View.VISIBLE : View.GONE);
        button.setText(title + (opening ? " ▾" : " ▸"));
    }

    private void setRulesVisible(LinearLayout container, Button button, String title, boolean visible) {
        container.setVisibility(visible ? View.VISIBLE : View.GONE);
        button.setText(title + (visible ? " ▾" : " ▸"));
    }

    private TextView heading(String text, int size) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        return view;
    }

    private TextView body(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(15);
        return view;
    }

    private void addLabel(LinearLayout content, String text) {
        TextView label = body(text);
        label.setPadding(0, dp(5), 0, dp(2));
        content.addView(label);
    }

    private LinearLayout.LayoutParams fullWidth() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams weighted() {
        return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
    }

    private void resetProfileEditor() {
        if (numberInput == null) return;
        editingOriginalNumber = "";
        numberInput.setText("");
        incomingModeSpinner.setSelection(PhoneProfile.IncomingMode.SECURITY_CODES.ordinal());
        outgoingModeSpinner.setSelection(PhoneProfile.OutgoingMode.SHORT_CODES.ordinal());
        codeCopyFollowupCheck.setChecked(true);
        incomingAllowInput.setText("");
        incomingBlockInput.setText("");
        outgoingAllowInput.setText("");
        outgoingBlockInput.setText("");
        setRulesVisible(incomingRulesContainer, incomingRulesButton, "Sender rules", false);
        setRulesVisible(outgoingRulesContainer, outgoingRulesButton, "Destination rules", false);
    }

    private void editProfile(PhoneProfile profile) {
        editingOriginalNumber = profile.number;
        numberInput.setText(profile.number);
        incomingModeSpinner.setSelection(profile.incomingMode.ordinal());
        outgoingModeSpinner.setSelection(profile.outgoingMode.ordinal());
        codeCopyFollowupCheck.setChecked(profile.codeCopyFollowup);
        incomingAllowInput.setText(joinList(profile.incomingAllowList));
        incomingBlockInput.setText(joinList(profile.incomingBlockList));
        outgoingAllowInput.setText(joinList(profile.outgoingAllowList));
        outgoingBlockInput.setText(joinList(profile.outgoingBlockList));
        setRulesVisible(incomingRulesContainer, incomingRulesButton, "Sender rules",
                !profile.incomingAllowList.isEmpty() || !profile.incomingBlockList.isEmpty());
        setRulesVisible(outgoingRulesContainer, outgoingRulesButton, "Destination rules",
                !profile.outgoingAllowList.isEmpty() || !profile.outgoingBlockList.isEmpty());
        numberInput.requestFocus();
    }

    private void saveProfile() {
        String number = numberInput.getText().toString().trim();
        if (number.isEmpty()) {
            Toast.makeText(this, "Enter the downstream phone number.", Toast.LENGTH_LONG).show();
            return;
        }

        PhoneProfile.IncomingMode incomingMode = PhoneProfile.IncomingMode.values()[incomingModeSpinner.getSelectedItemPosition()];
        PhoneProfile.OutgoingMode outgoingMode = PhoneProfile.OutgoingMode.values()[outgoingModeSpinner.getSelectedItemPosition()];
        List<String> incomingAllow = parseAddressList(incomingAllowInput.getText().toString());
        List<String> incomingBlock = parseAddressList(incomingBlockInput.getText().toString());
        List<String> outgoingAllow = parseAddressList(outgoingAllowInput.getText().toString());
        List<String> outgoingBlock = parseAddressList(outgoingBlockInput.getText().toString());

        if (incomingMode == PhoneProfile.IncomingMode.SELECTED && incomingAllow.isEmpty()) {
            setRulesVisible(incomingRulesContainer, incomingRulesButton, "Sender rules", true);
            Toast.makeText(this, "Add at least one sender to the incoming allow list.", Toast.LENGTH_LONG).show();
            return;
        }
        if (outgoingMode == PhoneProfile.OutgoingMode.SELECTED && outgoingAllow.isEmpty()) {
            setRulesVisible(outgoingRulesContainer, outgoingRulesButton, "Destination rules", true);
            Toast.makeText(this, "Add at least one number to the outgoing allow list.", Toast.LENGTH_LONG).show();
            return;
        }

        PhoneProfile profile = new PhoneProfile(
                number,
                incomingMode,
                incomingAllow,
                incomingBlock,
                codeCopyFollowupCheck.isChecked(),
                outgoingMode,
                outgoingAllow,
                outgoingBlock);
        if (!profile.hasAnyFeatureEnabled()) {
            Toast.makeText(this, "Choose something to forward or allow in at least one direction.", Toast.LENGTH_LONG).show();
            return;
        }

        if (!editingOriginalNumber.isEmpty()
                && !ShortCodeRelay.sameAddress(editingOriginalNumber, number)) {
            ForwardingPreferences.removeProfile(this, editingOriginalNumber);
        }
        ForwardingPreferences.saveProfile(this, profile);
        ForwardingPreferences.setStatus(this, "Saved forwarding setup for " + number + ".");
        resetProfileEditor();
        refreshProfiles();
        refreshStatus();
        if (!allSmsPermissionsGranted()) requestSmsPermissions(false);
        Toast.makeText(this, "Forwarding setup saved.", Toast.LENGTH_SHORT).show();
    }

    private void refreshProfiles() {
        profilesContainer.removeAllViews();
        List<PhoneProfile> profiles = ForwardingPreferences.profiles(this);
        if (profiles.isEmpty()) {
            TextView empty = body("No downstream phone configured yet.");
            profilesContainer.addView(empty);
            return;
        }

        for (PhoneProfile profile : profiles) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(0, dp(5), 0, dp(10));

            TextView summary = body(profile.summary());
            summary.setTextIsSelectable(true);
            card.addView(summary);

            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);

            Button edit = new Button(this);
            edit.setText("Edit");
            edit.setOnClickListener(v -> editProfile(profile));
            actions.addView(edit, weighted());

            Button remove = new Button(this);
            remove.setText("Remove");
            remove.setOnClickListener(v -> confirmRemoveProfile(profile));
            actions.addView(remove, weighted());

            card.addView(actions, fullWidth());
            profilesContainer.addView(card, fullWidth());
        }
    }

    private void confirmRemoveProfile(PhoneProfile profile) {
        new AlertDialog.Builder(this)
                .setTitle("Remove downstream phone?")
                .setMessage("Remove " + profile.number + " and all of its forwarding rules?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Remove", (dialog, which) -> {
                    ForwardingPreferences.removeProfile(this, profile.number);
                    if (ShortCodeRelay.sameAddress(editingOriginalNumber, profile.number)) resetProfileEditor();
                    refreshProfiles();
                    refreshStatus();
                })
                .show();
    }

    private void confirmDeleteSavedSetup() {
        new AlertDialog.Builder(this)
                .setTitle("Delete saved setup?")
                .setMessage("This removes every downstream phone and its forwarding rules. It does not revoke Android SMS permissions.")
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

    private List<String> parseAddressList(String raw) {
        ArrayList<String> result = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) return result;
        for (String part : raw.split("[\\n,;]+")) {
            String value = part.trim();
            if (!value.isEmpty()) result.add(value);
        }
        return result;
    }

    private String joinList(List<String> values) {
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            if (result.length() > 0) result.append('\n');
            result.append(value);
        }
        return result.toString();
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
        if (receive && send) {
            permissionStatus.setText("✓ SMS access ready");
            permissionHelp.setText("Incoming messages can be filtered, forwarded, and replied to using the rules below.");
            authorizeButton.setText("SMS access authorized ✓");
        } else {
            permissionStatus.setText("SMS access needs attention");
            permissionHelp.setText("Authorize Receive SMS and Send SMS. If Android blocks the prompt, the app will guide the one-time restricted-settings step.");
            authorizeButton.setText("Authorize SMS access");
        }
        forwardingStatus.setText("Last status: " + ForwardingPreferences.status(this));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
