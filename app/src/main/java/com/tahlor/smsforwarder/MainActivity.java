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
import android.window.OnBackInvokedDispatcher;

import java.util.ArrayList;
import java.util.List;

public final class MainActivity extends Activity {
    private static final int SMS_PERMISSION_REQUEST = 1001;
    private static final String LATEST_APK_URL =
            "https://taylorarchibald.com/apks/sms-code-forwarder-latest.apk";

    private static final String[] INCOMING_AUTH_LABELS = {
            "No incoming access",
            "Any sender",
            "Selected senders only"
    };
    private static final String[] INCOMING_PREFERENCE_LABELS = {
            "Forward nothing",
            "All authorized messages",
            "Security codes only",
            "Selected authorized senders"
    };
    private static final String[] OUTGOING_MODE_LABELS = {
            "No outgoing access",
            "Short codes only",
            "Selected numbers only",
            "Any number"
    };

    private UiPalette palette;
    private ScrollView rootScroll;
    private LinearLayout profilesContainer;
    private LinearLayout editorContainer;
    private LinearLayout moreContainer;
    private TextView editorTitle;
    private TextView permissionStatus;
    private TextView permissionHelp;
    private TextView activityStatus;
    private TextView advancedRulesLink;
    private TextView removeEditingLink;
    private Button authorizeButton;
    private TextView moreLink;

    private EditText numberInput;
    private Spinner incomingAuthorizationSpinner;
    private Spinner incomingPreferenceSpinner;
    private Spinner outgoingModeSpinner;
    private EditText incomingAuthorizedInput;
    private EditText incomingBlockedInput;
    private EditText incomingPreferredInput;
    private EditText outgoingAllowInput;
    private EditText outgoingBlockInput;
    private CheckBox codeCopyFollowupCheck;
    private LinearLayout incomingSecurityRulesContainer;
    private LinearLayout incomingPreferenceRulesContainer;
    private LinearLayout outgoingRulesContainer;

    private String editingOriginalNumber = "";
    private boolean advancedRulesShown;
    private boolean returningFromAppSettings;
    private boolean requestedPermissionsThisLaunch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("SMS Forwarder");
        palette = UiPalette.from(this);

        View content = buildContent();
        setContentView(content);
        SystemBars.configure(this, content, palette.background, palette.dark);
        registerBackHandler();
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

    private void registerBackHandler() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    () -> {
                        if (!handleBackNavigation()) moveTaskToBack(true);
                    });
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        if (!handleBackNavigation()) super.onBackPressed();
    }

    private boolean handleBackNavigation() {
        if (editorContainer != null && editorContainer.getVisibility() == View.VISIBLE) {
            hideEditor();
            resetProfileEditor();
            return true;
        }
        if (moreContainer != null && moreContainer.getVisibility() == View.VISIBLE) {
            moreContainer.setVisibility(View.GONE);
            if (moreLink != null) moreLink.setText("More");
            return true;
        }
        return false;
    }

    private View buildContent() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(18), dp(20), dp(28));
        content.setBackgroundColor(palette.background);

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

        activityStatus = body("");
        activityStatus.setPadding(dp(14), dp(11), dp(14), dp(11));
        activityStatus.setTextIsSelectable(true);
        LinearLayout.LayoutParams statusParams = fullWidth();
        statusParams.topMargin = dp(14);
        content.addView(activityStatus, statusParams);

        moreLink = link("More");
        moreLink.setPadding(0, dp(20), 0, dp(8));
        content.addView(moreLink);

        moreContainer = new LinearLayout(this);
        moreContainer.setOrientation(LinearLayout.VERTICAL);
        moreContainer.setVisibility(View.GONE);

        Button update = secondaryButton("Update app");
        update.setOnClickListener(v -> openLatestApk());
        moreContainer.addView(update, fullWidth());

        TextView deleteSetup = link("Delete all saved setup");
        deleteSetup.setTextColor(palette.danger);
        deleteSetup.setPadding(dp(4), dp(14), dp(4), dp(10));
        deleteSetup.setOnClickListener(v -> confirmDeleteSavedSetup());
        moreContainer.addView(deleteSetup);

        TextView privacy = body(
                "No Internet permission. No SMS-history access. Help includes a local, body-free activity log.");
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
        rootScroll.setBackgroundColor(palette.background);
        rootScroll.addView(content);
        return rootScroll;
    }

    private LinearLayout buildEditor() {
        LinearLayout editor = new LinearLayout(this);
        editor.setOrientation(LinearLayout.VERTICAL);
        editor.setPadding(dp(18), dp(16), dp(18), dp(18));
        editor.setBackground(roundedBackground(palette.surface, 18, true));

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
        numberInput = singleLineInput("+1 801 555 1234", InputType.TYPE_CLASS_PHONE);
        editor.addView(numberInput, fullWidth());

        TextView securityHeading = heading("Incoming security authorization", 17);
        securityHeading.setPadding(0, dp(18), 0, dp(4));
        editor.addView(securityHeading);
        TextView securityHint = body(
                "Hard limit: this phone can never receive incoming messages outside this scope.");
        securityHint.setTextSize(13);
        securityHint.setPadding(0, 0, 0, dp(4));
        editor.addView(securityHint);
        incomingAuthorizationSpinner = makeSpinner(INCOMING_AUTH_LABELS);
        editor.addView(incomingAuthorizationSpinner, fullWidth());

        TextView preferenceHeading = heading("Automatic forwarding preference", 17);
        preferenceHeading.setPadding(0, dp(18), 0, dp(4));
        editor.addView(preferenceHeading);
        TextView preferenceHint = body(
                "What to actually send automatically within the security authorization above.");
        preferenceHint.setTextSize(13);
        preferenceHint.setPadding(0, 0, 0, dp(4));
        editor.addView(preferenceHint);
        incomingPreferenceSpinner = makeSpinner(INCOMING_PREFERENCE_LABELS);
        editor.addView(incomingPreferenceSpinner, fullWidth());

        codeCopyFollowupCheck = new CheckBox(this);
        codeCopyFollowupCheck.setText("Send detected code separately for easy copying");
        codeCopyFollowupCheck.setTextColor(palette.text);
        codeCopyFollowupCheck.setTextSize(15);
        codeCopyFollowupCheck.setPadding(0, dp(4), 0, 0);
        editor.addView(codeCopyFollowupCheck);

        TextView outgoingHeading = heading("Outgoing relay authorization", 17);
        outgoingHeading.setPadding(0, dp(18), 0, dp(4));
        editor.addView(outgoingHeading);
        TextView outgoingSecurityHint = body(
                "Security permission: where this downstream phone may instruct this phone to send SMS.");
        outgoingSecurityHint.setTextSize(13);
        outgoingSecurityHint.setPadding(0, 0, 0, dp(4));
        editor.addView(outgoingSecurityHint);
        outgoingModeSpinner = makeSpinner(OUTGOING_MODE_LABELS);
        editor.addView(outgoingModeSpinner, fullWidth());

        TextView outgoingHint = body(
                "Example: [8552448147] SAVE sends only SAVE when that destination is authorized.");
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

        incomingSecurityRulesContainer = buildIncomingSecurityRules();
        editor.addView(incomingSecurityRulesContainer, fullWidth());

        incomingPreferenceRulesContainer = buildIncomingPreferenceRules();
        editor.addView(incomingPreferenceRulesContainer, fullWidth());

        outgoingRulesContainer = buildOutgoingRules();
        editor.addView(outgoingRulesContainer, fullWidth());

        Button save = primaryButton("Save phone");
        save.setOnClickListener(v -> saveProfile());
        LinearLayout.LayoutParams saveParams = fullWidth();
        saveParams.topMargin = dp(16);
        editor.addView(save, saveParams);

        removeEditingLink = link("Remove phone");
        removeEditingLink.setTextColor(palette.danger);
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
        incomingAuthorizationSpinner.setOnItemSelectedListener(listener);
        incomingPreferenceSpinner.setOnItemSelectedListener(listener);
        outgoingModeSpinner.setOnItemSelectedListener(listener);

        return editor;
    }

    private LinearLayout buildIncomingSecurityRules() {
        LinearLayout rules = rulesContainer();
        rules.addView(heading("Incoming security limits", 15));

        TextView note = body(
                "Selected senders is the hard allow list. Blocked senders always win, including in Any sender mode.");
        note.setTextSize(13);
        note.setPadding(0, dp(2), 0, dp(6));
        rules.addView(note);

        incomingAuthorizedInput = multilineInput("Authorized senders — one per line");
        rules.addView(incomingAuthorizedInput, fullWidth());

        incomingBlockedInput = multilineInput("Blocked senders — one per line");
        rules.addView(incomingBlockedInput, fullWidth());
        return rules;
    }

    private LinearLayout buildIncomingPreferenceRules() {
        LinearLayout rules = rulesContainer();
        rules.addView(heading("Automatic forwarding filters", 15));

        TextView note = body(
                "Used only for Selected authorized senders. These entries never expand the security authorization.");
        note.setTextSize(13);
        note.setPadding(0, dp(2), 0, dp(6));
        rules.addView(note);

        incomingPreferredInput = multilineInput("Preferred senders — one per line");
        rules.addView(incomingPreferredInput, fullWidth());
        return rules;
    }

    private LinearLayout buildOutgoingRules() {
        LinearLayout rules = rulesContainer();
        rules.addView(heading("Outgoing security limits", 15));

        TextView note = body(
                "Selected numbers uses the allow list. Blocked destinations always win.");
        note.setTextSize(13);
        note.setPadding(0, dp(2), 0, dp(6));
        rules.addView(note);

        outgoingAllowInput = multilineInput("Authorized destinations — one per line");
        rules.addView(outgoingAllowInput, fullWidth());

        outgoingBlockInput = multilineInput("Blocked destinations — one per line");
        rules.addView(outgoingBlockInput, fullWidth());
        return rules;
    }

    private LinearLayout rulesContainer() {
        LinearLayout rules = new LinearLayout(this);
        rules.setOrientation(LinearLayout.VERTICAL);
        rules.setPadding(dp(12), dp(8), dp(12), dp(12));
        rules.setBackground(roundedBackground(palette.accentSoft, 12, false));
        rules.setVisibility(View.GONE);
        LinearLayout.LayoutParams params = fullWidth();
        params.bottomMargin = dp(8);
        rules.setLayoutParams(params);
        return rules;
    }

    private EditText singleLineInput(String hint, int inputType) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setInputType(inputType);
        input.setTextColor(palette.text);
        input.setHintTextColor(palette.muted);
        return input;
    }

    private EditText multilineInput(String hint) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setMinLines(2);
        input.setGravity(Gravity.TOP);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setTextColor(palette.text);
        input.setHintTextColor(palette.muted);
        return input;
    }

    private Spinner makeSpinner(String[] labels) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, labels);
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
        editorContainer.post(() ->
                rootScroll.smoothScrollTo(0, Math.max(0, editorContainer.getTop() - dp(12))));
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
        incomingAuthorizationSpinner.setSelection(
                PhoneProfile.IncomingAuthorization.NONE.ordinal());
        incomingPreferenceSpinner.setSelection(
                PhoneProfile.IncomingPreference.SECURITY_CODES.ordinal());
        outgoingModeSpinner.setSelection(PhoneProfile.OutgoingMode.OFF.ordinal());
        codeCopyFollowupCheck.setChecked(true);
        incomingAuthorizedInput.setText("");
        incomingBlockedInput.setText("");
        incomingPreferredInput.setText("");
        outgoingAllowInput.setText("");
        outgoingBlockInput.setText("");
        updateEditorVisibility();
    }

    private void editProfile(PhoneProfile profile) {
        editingOriginalNumber = profile.number;
        advancedRulesShown = !profile.incomingAuthorizedSenders.isEmpty()
                || !profile.incomingBlockedSenders.isEmpty()
                || !profile.incomingPreferredSenders.isEmpty()
                || !profile.outgoingAllowList.isEmpty()
                || !profile.outgoingBlockList.isEmpty();
        editorTitle.setText("Edit phone");
        numberInput.setText(profile.number);
        incomingAuthorizationSpinner.setSelection(profile.incomingAuthorization.ordinal());
        incomingPreferenceSpinner.setSelection(profile.incomingPreference.ordinal());
        outgoingModeSpinner.setSelection(profile.outgoingMode.ordinal());
        codeCopyFollowupCheck.setChecked(profile.codeCopyFollowup);
        incomingAuthorizedInput.setText(joinList(profile.incomingAuthorizedSenders));
        incomingBlockedInput.setText(joinList(profile.incomingBlockedSenders));
        incomingPreferredInput.setText(joinList(profile.incomingPreferredSenders));
        outgoingAllowInput.setText(joinList(profile.outgoingAllowList));
        outgoingBlockInput.setText(joinList(profile.outgoingBlockList));
        removeEditingLink.setVisibility(View.VISIBLE);
        updateEditorVisibility();
        showEditor();
    }

    private void updateEditorVisibility() {
        if (incomingAuthorizationSpinner == null || incomingPreferenceSpinner == null
                || outgoingModeSpinner == null || incomingSecurityRulesContainer == null
                || incomingPreferenceRulesContainer == null || outgoingRulesContainer == null) {
            return;
        }

        PhoneProfile.IncomingAuthorization authorization =
                PhoneProfile.IncomingAuthorization.values()[
                        incomingAuthorizationSpinner.getSelectedItemPosition()];
        PhoneProfile.IncomingPreference preference =
                PhoneProfile.IncomingPreference.values()[
                        incomingPreferenceSpinner.getSelectedItemPosition()];
        PhoneProfile.OutgoingMode outgoingMode = PhoneProfile.OutgoingMode.values()[
                outgoingModeSpinner.getSelectedItemPosition()];

        codeCopyFollowupCheck.setVisibility(
                preference == PhoneProfile.IncomingPreference.OFF ? View.GONE : View.VISIBLE);

        incomingSecurityRulesContainer.setVisibility(
                advancedRulesShown
                        || authorization == PhoneProfile.IncomingAuthorization.SELECTED
                        ? View.VISIBLE : View.GONE);
        incomingPreferenceRulesContainer.setVisibility(
                advancedRulesShown
                        || preference == PhoneProfile.IncomingPreference.SELECTED
                        ? View.VISIBLE : View.GONE);
        outgoingRulesContainer.setVisibility(
                advancedRulesShown || outgoingMode == PhoneProfile.OutgoingMode.SELECTED
                        ? View.VISIBLE : View.GONE);
        advancedRulesLink.setText(
                advancedRulesShown ? "Hide advanced rules" : "Advanced rules");
    }

    private void saveProfile() {
        String number = numberInput.getText().toString().trim();
        if (number.isEmpty()) {
            Toast.makeText(this, "Enter the downstream phone number.", Toast.LENGTH_LONG).show();
            return;
        }

        PhoneProfile.IncomingAuthorization authorization =
                PhoneProfile.IncomingAuthorization.values()[
                        incomingAuthorizationSpinner.getSelectedItemPosition()];
        PhoneProfile.IncomingPreference preference =
                PhoneProfile.IncomingPreference.values()[
                        incomingPreferenceSpinner.getSelectedItemPosition()];
        PhoneProfile.OutgoingMode outgoingMode = PhoneProfile.OutgoingMode.values()[
                outgoingModeSpinner.getSelectedItemPosition()];

        List<String> authorizedSenders =
                parseAddressList(incomingAuthorizedInput.getText().toString());
        List<String> blockedSenders =
                parseAddressList(incomingBlockedInput.getText().toString());
        List<String> preferredSenders =
                parseAddressList(incomingPreferredInput.getText().toString());
        List<String> outgoingAllow =
                parseAddressList(outgoingAllowInput.getText().toString());
        List<String> outgoingBlock =
                parseAddressList(outgoingBlockInput.getText().toString());

        if (authorization == PhoneProfile.IncomingAuthorization.SELECTED
                && authorizedSenders.isEmpty()) {
            advancedRulesShown = true;
            updateEditorVisibility();
            Toast.makeText(this,
                    "Add at least one authorized incoming sender.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (preference != PhoneProfile.IncomingPreference.OFF
                && authorization == PhoneProfile.IncomingAuthorization.NONE) {
            Toast.makeText(this,
                    "Choose an incoming security authorization before enabling automatic forwarding.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (preference == PhoneProfile.IncomingPreference.SELECTED
                && preferredSenders.isEmpty()) {
            advancedRulesShown = true;
            updateEditorVisibility();
            Toast.makeText(this,
                    "Add at least one preferred sender for automatic forwarding.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (outgoingMode == PhoneProfile.OutgoingMode.SELECTED
                && outgoingAllow.isEmpty()) {
            advancedRulesShown = true;
            updateEditorVisibility();
            Toast.makeText(this,
                    "Add at least one authorized outgoing destination.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        PhoneProfile profile = new PhoneProfile(
                number,
                authorization,
                authorizedSenders,
                blockedSenders,
                preference,
                preferredSenders,
                codeCopyFollowupCheck.isChecked(),
                outgoingMode,
                outgoingAllow,
                outgoingBlock);
        if (!profile.hasAnyCapabilityEnabled()) {
            Toast.makeText(this,
                    "Grant at least one incoming or outgoing capability, or cancel this phone.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        if (!editingOriginalNumber.isEmpty()
                && !ShortCodeRelay.sameAddress(editingOriginalNumber, number)) {
            ForwardingPreferences.removeProfile(this, editingOriginalNumber);
        }
        ForwardingPreferences.saveProfile(this, profile);
        ForwardingPreferences.setStatus(this,
                "Saved security and forwarding setup for " + number + ".");
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
            empty.setBackground(roundedBackground(palette.surface, 16, true));

            TextView emptyTitle = heading("No phones yet", 17);
            empty.addView(emptyTitle);
            TextView emptyText = body(
                    "Add a phone, explicitly choose its security capabilities, then choose what should be forwarded automatically.");
            emptyText.setPadding(0, dp(3), 0, 0);
            empty.addView(emptyText);
            profilesContainer.addView(empty, cardParams());
            return;
        }

        for (PhoneProfile profile : profiles) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(16), dp(13), dp(14), dp(13));
            card.setBackground(roundedBackground(palette.surface, 16, true));
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
            edit.setOnClickListener(v -> editProfile(profile));
            top.addView(edit);
            card.addView(top, fullWidth());

            TextView summary = body(compactSummary(profile));
            summary.setPadding(0, dp(4), 0, 0);
            card.addView(summary);
            profilesContainer.addView(card, cardParams());
        }
    }

    private String compactSummary(PhoneProfile profile) {
        return "Allowed: " + profile.authorizationLabel()
                + "  •  Auto: " + profile.preferenceLabel()
                + "  •  Sends: " + profile.outgoingLabel();
    }

    private void confirmRemoveNumber(String number) {
        new AlertDialog.Builder(this)
                .setTitle("Remove phone?")
                .setMessage("Remove " + number + " and its forwarding/security rules?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Remove", (dialog, which) -> {
                    ForwardingPreferences.removeProfile(this, number);
                    hideEditor();
                    resetProfileEditor();
                    ForwardingPreferences.setStatus(this,
                            "Removed downstream phone " + number + ".");
                    refreshProfiles();
                    refreshStatus();
                })
                .show();
    }

    private void confirmDeleteSavedSetup() {
        new AlertDialog.Builder(this)
                .setTitle("Delete all setup?")
                .setMessage(
                        "This removes every configured phone and forwarding/security rule. Android SMS permissions stay authorized.")
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
        if (checkSelfPermission(Manifest.permission.RECEIVE_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            missing.add(Manifest.permission.RECEIVE_SMS);
        }
        if (checkSelfPermission(Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            missing.add(Manifest.permission.SEND_SMS);
        }
        if (missing.isEmpty()) {
            if (userInitiated) {
                Toast.makeText(this,
                        "SMS access is already authorized.",
                        Toast.LENGTH_SHORT).show();
            }
            refreshStatus();
            return;
        }
        requestPermissions(missing.toArray(new String[0]), SMS_PERMISSION_REQUEST);
    }

    private void showRestrictedSettingsHelp() {
        String message =
                "Android is blocking one or both SMS permissions. Because this app is sideloaded, do this once:\n\n"
                + "1. Open App Info.\n"
                + "2. Tap the top-right ⋮ menu.\n"
                + "3. Tap Allow restricted settings.\n"
                + "4. Return here.\n\n"
                + "The SMS permission request will retry automatically.";
        new AlertDialog.Builder(this)
                .setTitle("One-time authorization")
                .setMessage(message)
                .setNegativeButton("Not now", null)
                .setNeutralButton("Retry",
                        (dialog, which) -> requestSmsPermissions(true))
                .setPositiveButton("Open App Info",
                        (dialog, which) -> openAppSettings())
                .show();
    }

    private void openAppSettings() {
        try {
            returningFromAppSettings = true;
            Intent intent = new Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
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
            Toast.makeText(
                    this,
                    "No browser is available to open the update link.",
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
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
        return checkSelfPermission(Manifest.permission.RECEIVE_SMS)
                == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.SEND_SMS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void refreshStatus() {
        boolean receive = checkSelfPermission(Manifest.permission.RECEIVE_SMS)
                == PackageManager.PERMISSION_GRANTED;
        boolean send = checkSelfPermission(Manifest.permission.SEND_SMS)
                == PackageManager.PERMISSION_GRANTED;
        if (receive && send) {
            permissionStatus.setText("✓ SMS access ready");
            permissionStatus.setTextColor(palette.success);
            permissionHelp.setVisibility(View.GONE);
            authorizeButton.setVisibility(View.GONE);
        } else {
            permissionStatus.setText("SMS access required");
            permissionStatus.setTextColor(palette.text);
            permissionHelp.setText(
                    "Authorize once so this app can receive and send texts. If Android blocks it, the app will guide the restricted-settings step.");
            permissionHelp.setVisibility(View.VISIBLE);
            authorizeButton.setVisibility(View.VISIBLE);
        }

        String status = ForwardingPreferences.status(this);
        activityStatus.setText("Last activity: " + status);
        if (needsAttention(status)) {
            activityStatus.setTextColor(palette.danger);
            activityStatus.setBackground(
                    roundedBackground(palette.dangerSoft, 12, false));
        } else {
            activityStatus.setTextColor(palette.text);
            activityStatus.setBackground(
                    roundedBackground(palette.accentSoft, 12, false));
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
                || lower.contains("could not")
                || lower.contains("did not");
    }

    private TextView heading(String text, int size) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(palette.text);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private TextView body(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(15);
        view.setTextColor(palette.muted);
        view.setLineSpacing(0, 1.08f);
        return view;
    }

    private TextView link(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(15);
        view.setTextColor(palette.accent);
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
        button.setBackgroundTintList(ColorStateList.valueOf(palette.accent));
        button.setMinHeight(dp(48));
        return button;
    }

    private Button secondaryButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(15);
        button.setTextColor(palette.text);
        button.setBackgroundTintList(ColorStateList.valueOf(palette.border));
        button.setMinHeight(dp(46));
        return button;
    }

    private void addLabel(LinearLayout content, String text) {
        TextView label = body(text);
        label.setTextColor(palette.text);
        label.setTextSize(13);
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        label.setPadding(0, dp(14), 0, dp(1));
        content.addView(label);
    }

    private GradientDrawable roundedBackground(
            int color, int radiusDp, boolean border) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(radiusDp));
        if (border) background.setStroke(dp(1), palette.border);
        return background;
    }

    private LinearLayout.LayoutParams fullWidth() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams weighted() {
        return new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
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
