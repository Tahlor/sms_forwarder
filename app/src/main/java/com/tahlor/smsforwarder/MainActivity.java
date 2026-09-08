package com.tahlor.smsforwarder;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
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

    private static final int COLOR_BG = 0xFFF7F7FA;
    private static final int COLOR_SURFACE = 0xFFFFFFFF;
    private static final int COLOR_TEXT = 0xFF17171B;
    private static final int COLOR_MUTED = 0xFF6D6D76;
    private static final int COLOR_BORDER = 0xFFE5E5EA;
    private static final int COLOR_ACCENT = 0xFF315CF5;
    private static final int COLOR_ACCENT_SOFT = 0xFFEAF0FF;
    private static final int COLOR_SUCCESS = 0xFF168243;
    private static final int COLOR_DANGER = 0xFFB3261E;

    private static final String[] INCOMING_MODE_LABELS = {
            "All messages",
            "Security codes only",
            "Selected senders only",
            "Nothing"
    };
    private static final String[] OUTGOING_MODE_LABELS = {
            "Any number",
            "Short codes only",
            "Selected numbers only",
            "Nothing"
    };

    private ScrollView rootScroll;
    private LinearLayout profilesContainer;
    private LinearLayout editorContainer;
    private LinearLayout moreContainer;
    private TextView editorTitle;
    private TextView permissionStatus;
    private TextView permissionHelp;
    private TextView forwardingStatus;
    private TextView advancedRulesLink;
    private TextView removeEditingLink;
    private Button authorizeButton;

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

    private String editingOriginalNumber = "";
    private boolean advancedRulesShown;
    private boolean returningFromAppSettings;
    private boolean requestedPermissionsThisLaunch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("SMS Forwarder");
        getWindow().setStatusBarColor(COLOR_BG);
        getWindow().setNavigationBarColor(COLOR_BG);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

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
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(18), dp(20), dp(28));
        content.setBackgroundColor(COLOR_BG);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.TOP);

        LinearLayout headerText = new LinearLayout(this);
        headerText.setOrientation(LinearLayout.VERTICAL);
        TextView title = heading("SMS Forwarder", 28);
        headerText.addView(title);
        TextView description = body("Forward messages between this phone and your other phones.");
        description.setPadding(0, dp(4), 0, 0);
        headerText.addView(description);
        header.addView(headerText, weighted());

        TextView help = link("Help");
        help.setPadding(dp(14), dp(4), dp(2), dp(10));
        help.setOnClickListener(v -> startActivity(new Intent(this, HelpActivity.class)));
        header.addView(help);
        content.addView(header, fullWidth());

        permissionStatus = body("");
        permissionStatus.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        permissionStatus.setPadding(0, dp(14), 0, 0);
        content.addView(permissionStatus);

        permissionHelp = body("");
        permissionHelp.setPadding(0, dp(4), 0, dp(8));
        content.addView(permissionHelp);

        authorizeButton = primaryButton("Authorize SMS access");
        authorizeButton.setOnClickListener(v -> requestSmsPermissions(true));
        content.addView(authorizeButton, fullWidth());

        LinearLayout phonesHeader = new LinearLayout(this);
        phonesHeader.setOrientation(LinearLayout.HORIZONTAL);
        phonesHeader.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams phonesHeaderParams = fullWidth();
        phonesHeaderParams.topMargin = dp(22);

        TextView phonesTitle = heading("Your phones", 20);
        phonesHeader.addView(phonesTitle, weighted());
        TextView addPhone = link("+ Add phone");
        addPhone.setPadding(dp(12), dp(8), 0, dp(8));
        addPhone.setOnClickListener(v -> showNewProfileEditor());
        phonesHeader.addView(addPhone);
        content.addView(phonesHeader, phonesHeaderParams);

        profilesContainer = new LinearLayout(this);
        profilesContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams profilesParams = fullWidth();
        profilesParams.topMargin = dp(8);
        content.addView(profilesContainer, profilesParams);

        editorContainer = buildEditor();
        editorContainer.setVisibility(View.GONE);
        LinearLayout.LayoutParams editorParams = cardParams();
        editorParams.topMargin = dp(16);
        content.addView(editorContainer, editorParams);

        forwardingStatus = body("");
        forwardingStatus.setTextColor(COLOR_DANGER);
        forwardingStatus.setBackground(roundedBackground(0xFFFFF0EF, 12));
        forwardingStatus.setPadding(dp(14), dp(11), dp(14), dp(11));
        forwardingStatus.setTextIsSelectable(true);
        forwardingStatus.setVisibility(View.GONE);
        LinearLayout.LayoutParams statusParams = fullWidth();
        statusParams.topMargin = dp(14);
        content.addView(forwardingStatus, statusParams);

        TextView moreLink = link("More");
        moreLink.setPadding(0, dp(20), 0, dp(8));
        content.addView(moreLink);

        moreContainer = new LinearLayout(this);
        moreContainer.setOrientation(LinearLayout.VERTICAL);
        moreContainer.setVisibility(View.GONE);

        Button update = secondaryButton("Update app");
        update.setOnClickListener(v -> openLatestApk());
        moreContainer.addView(update, fullWidth());

        TextView deleteSetup = link("Delete all saved setup");
        deleteSetup.setTextColor(COLOR_DANGER);
        deleteSetup.setPadding(dp(4), dp(14), dp(4), dp(10));
        deleteSetup.setOnClickListener(v -> confirmDeleteSavedSetup());
        moreContainer.addView(deleteSetup);

        TextView privacy = body("No Internet permission. No SMS-history access.");
        privacy.setTextSize(13);
        privacy.setPadding(0, dp(4), 0, 0);
        moreContainer.addView(privacy);
        content.addView(moreContainer, fullWidth());

        moreLink.setOnClickListener(v -> {
            boolean show = moreContainer.getVisibility() != View.VISIBLE;
            moreContainer.setVisibility(show ? View.VISIBLE : View.GONE);
            moreLink.setText(show ? "Less" : "More");
        });

        rootScroll = new ScrollView(this);
        rootScroll.setFillViewport(true);
        rootScroll.setBackgroundColor(COLOR_BG);
        rootScroll.addView(content);
        return rootScroll;
    }

    private LinearLayout buildEditor() {
        LinearLayout editor = new LinearLayout(this);
        editor.setOrientation(LinearLayout.VERTICAL);
        editor.setPadding(dp(18), dp(16), dp(18), dp(18));
        editor.setBackground(roundedBackground(COLOR_SURFACE, 18));

        LinearLayout editorHeader = new LinearLayout(this);
        editorHeader.setOrientation(LinearLayout.HORIZONTAL);
        editorHeader.setGravity(Gravity.CENTER_VERTICAL);
        editorTitle = heading("Add phone", 21);
        editorHeader.addView(editorTitle, weighted());
        TextView cancel = link("Cancel");
        cancel.setPadding(dp(12), dp(6), 0, dp(6));
        cancel.setOnClickListener(v -> hideEditor());
        editorHeader.addView(cancel);
        editor.addView(editorHeader, fullWidth());

        addLabel(editor, "Phone number");
        numberInput = new EditText(this);
        numberInput.setHint("+1 801 555 1234");
        numberInput.setInputType(InputType.TYPE_CLASS_PHONE);
        editor.addView(numberInput, fullWidth());

        TextView incomingHeading = heading("What should this phone receive?", 17);
        incomingHeading.setPadding(0, dp(18), 0, dp(4));
        editor.addView(incomingHeading);
        incomingModeSpinner = makeSpinner(INCOMING_MODE_LABELS);
        editor.addView(incomingModeSpinner, fullWidth());

        codeCopyFollowupCheck = new CheckBox(this);
        codeCopyFollowupCheck.setText("Send detected code separately for easy copying");
        codeCopyFollowupCheck.setTextColor(COLOR_TEXT);
        codeCopyFollowupCheck.setTextSize(15);
        codeCopyFollowupCheck.setPadding(0, dp(4), 0, 0);
        editor.addView(codeCopyFollowupCheck);

        TextView outgoingHeading = heading("What can this phone send through here?", 17);
        outgoingHeading.setPadding(0, dp(18), 0, dp(4));
        editor.addView(outgoingHeading);
        outgoingModeSpinner = makeSpinner(OUTGOING_MODE_LABELS);
        editor.addView(outgoingModeSpinner, fullWidth());

        TextView outgoingHint = body("Example: [711711] SAVE sends SAVE to shortcode 711711.");
        outgoingHint.setTextSize(13);
        outgoingHint.setPadding(0, dp(6), 0, 0);
        editor.addView(outgoingHint);

        advancedRulesLink = link("Advanced rules");
        advancedRulesLink.setPadding(0, dp(16), 0, dp(8));
        advancedRulesLink.setOnClickListener(v -> {
            advancedRulesShown = !advancedRulesShown;
            updateEditorVisibility();
        });
        editor.addView(advancedRulesLink);

        incomingRulesContainer = buildRulesContainer(true);
        editor.addView(incomingRulesContainer, fullWidth());

        outgoingRulesContainer = buildRulesContainer(false);
        editor.addView(outgoingRulesContainer, fullWidth());

        Button save = primaryButton("Save phone");
        save.setOnClickListener(v -> saveProfile());
        LinearLayout.LayoutParams saveParams = fullWidth();
        saveParams.topMargin = dp(16);
        editor.addView(save, saveParams);

        removeEditingLink = link("Remove phone");
        removeEditingLink.setTextColor(COLOR_DANGER);
        removeEditingLink.setGravity(Gravity.CENTER);
        removeEditingLink.setPadding(dp(8), dp(14), dp(8), dp(2));
        removeEditingLink.setVisibility(View.GONE);
        removeEditingLink.setOnClickListener(v -> {
            if (!editingOriginalNumber.isEmpty()) confirmRemoveNumber(editingOriginalNumber);
        });
        editor.addView(removeEditingLink, fullWidth());

        AdapterView.OnItemSelectedListener listener = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateEditorVisibility();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        };
        incomingModeSpinner.setOnItemSelectedListener(listener);
        outgoingModeSpinner.setOnItemSelectedListener(listener);

        return editor;
    }

    private LinearLayout buildRulesContainer(boolean incoming) {
        LinearLayout rules = new LinearLayout(this);
        rules.setOrientation(LinearLayout.VERTICAL);
        rules.setPadding(dp(12), dp(8), dp(12), dp(12));
        rules.setBackground(roundedBackground(COLOR_ACCENT_SOFT, 12));
        rules.setVisibility(View.GONE);

        TextView title = heading(incoming ? "Incoming filters" : "Outgoing filters", 15);
        rules.addView(title);

        TextView note = body(incoming
                ? "Use an allow list for “Selected senders only.” Blocked senders always win."
                : "Use an allow list for “Selected numbers only.” Blocked destinations always win.");
        note.setTextSize(13);
        note.setPadding(0, dp(2), 0, dp(6));
        rules.addView(note);

        EditText allow = new EditText(this);
        allow.setHint(incoming ? "Allowed senders — one per line" : "Allowed destinations — one per line");
        allow.setMinLines(2);
        allow.setGravity(Gravity.TOP);
        allow.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        rules.addView(allow, fullWidth());

        EditText block = new EditText(this);
        block.setHint(incoming ? "Blocked senders — one per line" : "Blocked destinations — one per line");
        block.setMinLines(2);
        block.setGravity(Gravity.TOP);
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
        spinner.setPadding(0, dp(3), 0, dp(3));
        return spinner;
    }

    private void showNewProfileEditor() {
        resetProfileEditor();
        editorTitle.setText("Add phone");
        removeEditingLink.setVisibility(View.GONE);
        showEditor();
    }

    private void showEditor() {
        editorContainer.setVisibility(View.VISIBLE);
        editorContainer.post(() -> rootScroll.smoothScrollTo(0, editorContainer.getTop() - dp(12)));
    }

    private void hideEditor() {
        editorContainer.setVisibility(View.GONE);
        editingOriginalNumber = "";
    }

    private void resetProfileEditor() {
        if (numberInput == null) return;
        editingOriginalNumber = "";
        advancedRulesShown = false;
        numberInput.setText("");
        incomingModeSpinner.setSelection(PhoneProfile.IncomingMode.SECURITY_CODES.ordinal());
        outgoingModeSpinner.setSelection(PhoneProfile.OutgoingMode.SHORT_CODES.ordinal());
        codeCopyFollowupCheck.setChecked(true);
        incomingAllowInput.setText("");
        incomingBlockInput.setText("");
        outgoingAllowInput.setText("");
        outgoingBlockInput.setText("");
        updateEditorVisibility();
    }

    private void editProfile(PhoneProfile profile) {
        editingOriginalNumber = profile.number;
        advancedRulesShown = !profile.incomingAllowList.isEmpty()
                || !profile.incomingBlockList.isEmpty()
                || !profile.outgoingAllowList.isEmpty()
                || !profile.outgoingBlockList.isEmpty();
        editorTitle.setText("Edit phone");
        numberInput.setText(profile.number);
        incomingModeSpinner.setSelection(profile.incomingMode.ordinal());
        outgoingModeSpinner.setSelection(profile.outgoingMode.ordinal());
        codeCopyFollowupCheck.setChecked(profile.codeCopyFollowup);
        incomingAllowInput.setText(joinList(profile.incomingAllowList));
        incomingBlockInput.setText(joinList(profile.incomingBlockList));
        outgoingAllowInput.setText(joinList(profile.outgoingAllowList));
        outgoingBlockInput.setText(joinList(profile.outgoingBlockList));
        removeEditingLink.setVisibility(View.VISIBLE);
        updateEditorVisibility();
        showEditor();
    }

    private void updateEditorVisibility() {
        if (incomingModeSpinner == null || outgoingModeSpinner == null
                || incomingRulesContainer == null || outgoingRulesContainer == null) return;

        PhoneProfile.IncomingMode incomingMode = PhoneProfile.IncomingMode.values()[
                incomingModeSpinner.getSelectedItemPosition()];
        PhoneProfile.OutgoingMode outgoingMode = PhoneProfile.OutgoingMode.values()[
                outgoingModeSpinner.getSelectedItemPosition()];

        codeCopyFollowupCheck.setVisibility(
                incomingMode == PhoneProfile.IncomingMode.OFF ? View.GONE : View.VISIBLE);

        boolean incomingRulesNeeded = incomingMode == PhoneProfile.IncomingMode.SELECTED;
        boolean outgoingRulesNeeded = outgoingMode == PhoneProfile.OutgoingMode.SELECTED;
        incomingRulesContainer.setVisibility(
                advancedRulesShown || incomingRulesNeeded ? View.VISIBLE : View.GONE);
        outgoingRulesContainer.setVisibility(
                advancedRulesShown || outgoingRulesNeeded ? View.VISIBLE : View.GONE);
        advancedRulesLink.setText(advancedRulesShown ? "Hide advanced rules" : "Advanced rules");
    }

    private void saveProfile() {
        String number = numberInput.getText().toString().trim();
        if (number.isEmpty()) {
            Toast.makeText(this, "Enter the downstream phone number.", Toast.LENGTH_LONG).show();
            return;
        }

        PhoneProfile.IncomingMode incomingMode = PhoneProfile.IncomingMode.values()[
                incomingModeSpinner.getSelectedItemPosition()];
        PhoneProfile.OutgoingMode outgoingMode = PhoneProfile.OutgoingMode.values()[
                outgoingModeSpinner.getSelectedItemPosition()];
        List<String> incomingAllow = parseAddressList(incomingAllowInput.getText().toString());
        List<String> incomingBlock = parseAddressList(incomingBlockInput.getText().toString());
        List<String> outgoingAllow = parseAddressList(outgoingAllowInput.getText().toString());
        List<String> outgoingBlock = parseAddressList(outgoingBlockInput.getText().toString());

        if (incomingMode == PhoneProfile.IncomingMode.SELECTED && incomingAllow.isEmpty()) {
            advancedRulesShown = true;
            updateEditorVisibility();
            Toast.makeText(this, "Add at least one allowed sender.", Toast.LENGTH_LONG).show();
            return;
        }
        if (outgoingMode == PhoneProfile.OutgoingMode.SELECTED && outgoingAllow.isEmpty()) {
            advancedRulesShown = true;
            updateEditorVisibility();
            Toast.makeText(this, "Add at least one allowed destination.", Toast.LENGTH_LONG).show();
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
            Toast.makeText(this, "Choose something for this phone to receive or send.", Toast.LENGTH_LONG).show();
            return;
        }

        if (!editingOriginalNumber.isEmpty()
                && !ShortCodeRelay.sameAddress(editingOriginalNumber, number)) {
            ForwardingPreferences.removeProfile(this, editingOriginalNumber);
        }
        ForwardingPreferences.saveProfile(this, profile);
        ForwardingPreferences.setStatus(this, "Saved forwarding setup for " + number + ".");
        hideEditor();
        resetProfileEditor();
        refreshProfiles();
        refreshStatus();
        if (!allSmsPermissionsGranted()) requestSmsPermissions(false);
        Toast.makeText(this, "Saved.", Toast.LENGTH_SHORT).show();
    }

    private void refreshProfiles() {
        profilesContainer.removeAllViews();
        List<PhoneProfile> profiles = ForwardingPreferences.profiles(this);
        if (profiles.isEmpty()) {
            LinearLayout empty = new LinearLayout(this);
            empty.setOrientation(LinearLayout.VERTICAL);
            empty.setPadding(dp(16), dp(14), dp(16), dp(14));
            empty.setBackground(roundedBackground(COLOR_SURFACE, 16));

            TextView emptyTitle = heading("No phones yet", 17);
            empty.addView(emptyTitle);
            TextView emptyText = body("Add a phone to choose what it should receive and what it can send through this phone.");
            emptyText.setPadding(0, dp(3), 0, 0);
            empty.addView(emptyText);
            profilesContainer.addView(empty, cardParams());
            return;
        }

        for (PhoneProfile profile : profiles) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(16), dp(13), dp(14), dp(13));
            card.setBackground(roundedBackground(COLOR_SURFACE, 16));
            card.setClickable(true);
            card.setFocusable(true);
            card.setOnClickListener(v -> editProfile(profile));

            LinearLayout top = new LinearLayout(this);
            top.setOrientation(LinearLayout.HORIZONTAL);
            top.setGravity(Gravity.CENTER_VERTICAL);
            TextView number = heading(profile.number, 17);
            top.addView(number, weighted());
            TextView edit = link("Edit ›");
            edit.setPadding(dp(12), dp(2), 0, dp(2));
            top.addView(edit);
            card.addView(top, fullWidth());

            TextView summary = body(compactSummary(profile));
            summary.setPadding(0, dp(4), 0, 0);
            card.addView(summary);
            profilesContainer.addView(card, cardParams());
        }
    }

    private String compactSummary(PhoneProfile profile) {
        String incoming;
        switch (profile.incomingMode) {
            case ALL: incoming = "all messages"; break;
            case SECURITY_CODES: incoming = "security codes"; break;
            case SELECTED: incoming = "selected senders"; break;
            default: incoming = "nothing";
        }

        String outgoing;
        switch (profile.outgoingMode) {
            case ANY: outgoing = "any number"; break;
            case SHORT_CODES: outgoing = "short codes"; break;
            case SELECTED: outgoing = "selected numbers"; break;
            default: outgoing = "nothing";
        }
        return "Receives " + incoming + "  •  Sends to " + outgoing;
    }

    private void confirmRemoveNumber(String number) {
        new AlertDialog.Builder(this)
                .setTitle("Remove phone?")
                .setMessage("Remove " + number + " and its forwarding rules?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Remove", (dialog, which) -> {
                    ForwardingPreferences.removeProfile(this, number);
                    hideEditor();
                    resetProfileEditor();
                    refreshProfiles();
                    refreshStatus();
                })
                .show();
    }

    private void confirmDeleteSavedSetup() {
        new AlertDialog.Builder(this)
                .setTitle("Delete all setup?")
                .setMessage("This removes every configured phone and forwarding rule. Android SMS permissions stay authorized.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    ForwardingPreferences.deleteSavedSetup(this);
                    hideEditor();
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
        String message = "Android is blocking one or both SMS permissions. Because this app is sideloaded, do this once:\n\n"
                + "1. Open App Info.\n"
                + "2. Tap the top-right ⋮ menu.\n"
                + "3. Tap Allow restricted settings.\n"
                + "4. Return here.\n\n"
                + "The SMS permission request will retry automatically.";
        new AlertDialog.Builder(this)
                .setTitle("One-time authorization")
                .setMessage(message)
                .setNegativeButton("Not now", null)
                .setNeutralButton("Retry", (dialog, which) -> requestSmsPermissions(true))
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
                            "Receive SMS and Send SMS are both required.",
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
            permissionStatus.setTextColor(COLOR_SUCCESS);
            permissionHelp.setVisibility(View.GONE);
            authorizeButton.setVisibility(View.GONE);
        } else {
            permissionStatus.setText("SMS access required");
            permissionStatus.setTextColor(COLOR_TEXT);
            permissionHelp.setText("Authorize once so this app can receive and forward texts. If Android blocks it, the app will guide the restricted-settings step.");
            permissionHelp.setVisibility(View.VISIBLE);
            authorizeButton.setVisibility(View.VISIBLE);
        }

        String status = ForwardingPreferences.status(this);
        if (needsAttention(status)) {
            forwardingStatus.setText(status);
            forwardingStatus.setVisibility(View.VISIBLE);
        } else {
            forwardingStatus.setVisibility(View.GONE);
        }
    }

    private boolean needsAttention(String status) {
        if (status == null) return false;
        String lower = status.toLowerCase();
        return lower.contains("failed")
                || lower.contains("missing")
                || lower.contains("denied")
                || lower.contains("ignored")
                || lower.contains("cannot")
                || lower.contains("blocked")
                || lower.contains("no downstream")
                || lower.contains("could not");
    }

    private TextView heading(String text, int size) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(COLOR_TEXT);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private TextView body(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(15);
        view.setTextColor(COLOR_MUTED);
        view.setLineSpacing(0, 1.08f);
        return view;
    }

    private TextView link(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(15);
        view.setTextColor(COLOR_ACCENT);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setClickable(true);
        view.setFocusable(true);
        return view;
    }

    private Button primaryButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(16);
        button.setTextColor(0xFFFFFFFF);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setBackgroundTintList(ColorStateList.valueOf(COLOR_ACCENT));
        button.setMinHeight(dp(48));
        return button;
    }

    private Button secondaryButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(15);
        button.setTextColor(COLOR_TEXT);
        button.setBackgroundTintList(ColorStateList.valueOf(COLOR_BORDER));
        button.setMinHeight(dp(46));
        return button;
    }

    private void addLabel(LinearLayout content, String text) {
        TextView label = body(text);
        label.setTextColor(COLOR_TEXT);
        label.setTextSize(13);
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        label.setPadding(0, dp(14), 0, dp(1));
        content.addView(label);
    }

    private GradientDrawable roundedBackground(int color, int radiusDp) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(radiusDp));
        if (color == COLOR_SURFACE) background.setStroke(dp(1), COLOR_BORDER);
        return background;
    }

    private LinearLayout.LayoutParams fullWidth() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams weighted() {
        return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams params = fullWidth();
        params.bottomMargin = dp(10);
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
