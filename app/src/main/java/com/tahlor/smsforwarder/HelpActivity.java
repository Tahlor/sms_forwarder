package com.tahlor.smsforwarder;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

public final class HelpActivity extends Activity {
    private UiPalette palette;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("SMS Forwarder help");
        palette = UiPalette.from(this);
        ScrollView content = buildContent();
        setContentView(content);
        SystemBars.configure(this, content, palette.background, palette.dark);
    }

    private ScrollView buildContent() {
        int pad = dp(20);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(pad, dp(18), pad, dp(28));
        content.setBackgroundColor(palette.background);

        content.addView(heading("SMS Forwarder help", 26));
        TextView intro = body(
                "Command reference, permissions, security boundaries, forwarding behavior, and troubleshooting.");
        intro.setPadding(0, dp(4), 0, 0);
        content.addView(intro);

        addCurrentSetup(content);

        addSection(content, "Quick command reference",
                "Only a configured downstream phone can issue relay commands. The text must begin with a bracketed destination.");
        addCode(content, "[711711] SAVE");
        addBody(content,
                "Sends only SAVE from this forwarding phone to short code 711711, if that downstream phone is authorized for the destination.");
        addCode(content, "[8552448147] SAVE");
        addBody(content,
                "Sends only SAVE to the normal phone number 8552448147 when the downstream phone has Any destination access or that destination is explicitly allowed.");
        addCode(content, "[8552448147]");
        addBody(content,
                "Sends nothing. It only opens a 5-minute reply window for that destination.");
        addBody(content,
                "Destinations are normalized by removing common punctuation. A routable bracket destination must contain 3–15 digits after normalization. Short codes are 3–6 digits.");

        addSection(content, "Relay results and replies",
                "For a command with a message, the app waits for Android's carrier send callback. The downstream/controller phone then receives an SMS Forwarder acknowledgement saying the relay was sent or failed. A successful relay opens a 5-minute reply window. If the destination replies during that window, the downstream phone receives the reply as [destination] <reply>. Sending another command refreshes the window.");

        addSection(content, "Incoming security authorization",
                "This is the hard privacy boundary for each downstream phone. No incoming message can be forwarded outside this authorization, regardless of the automatic-forwarding preference.");
        addBullets(content,
                "No incoming access — this phone can never receive forwarded incoming SMS.",
                "Any sender — any sender is eligible, except blocked senders.",
                "Selected senders only — only the configured sender numbers or sender IDs are eligible.",
                "Blocked senders always win over every other setting.");

        addSection(content, "Automatic forwarding preference",
                "This controls what you actually want sent automatically, but only inside the security authorization above. Effective delivery is always security authorization ∩ automatic forwarding preference.");
        addBullets(content,
                "Forward nothing — no automatic incoming forwarding.",
                "All authorized messages — forward every message that passes the security authorization.",
                "Security codes only — forward authorized messages containing 6 or more consecutive ASCII digits.",
                "Selected authorized senders — forward only the preferred sender list, and only if those senders are also authorized.");

        addSection(content, "Security-code behavior",
                "Security-code mode recognizes common verification messages: 4–8 digit codes when the message contains authentication language such as code, verification, OTP, PIN, login, security, or do-not-share wording; formatted codes such as 123-456 or 123 456; prefixed codes such as G-123456; and, for backward compatibility, any run of 6 or more consecutive digits. When “Send detected code separately for easy copying” is enabled, the full authorized message is forwarded and the detected code is also sent as a second SMS by itself.");

        addSection(content, "Outgoing relay authorization",
                "This is a security capability granted to the downstream phone. It is independent of automatic incoming forwarding.");
        addBullets(content,
                "No outgoing access — bracket commands cannot send anything.",
                "Short codes only — only destinations containing 3–6 digits are allowed.",
                "Selected numbers only — only explicitly allowed destinations are permitted.",
                "Any number — any normalized 3–15 digit destination is permitted unless blocked.",
                "Blocked destinations always win.");

        addSection(content, "Remote management by SMS",
                "Remote management is a separate powerful capability and is OFF by default. Enable it only for a phone you fully trust. A remotely managed phone can change its own incoming security authorization and automatic-forwarding rules. Commands may optionally start with CMD.");
        addCode(content, "AUTH ANY");
        addBody(content, "Set hard incoming security authorization to any sender. AUTH SELECTED and AUTH OFF are also supported.");
        addCode(content, "ALLOW 711711");
        addBody(content, "Add a sender to the selected authorization list. If incoming authorization was Off, ALLOW switches it to Selected. UNALLOW removes the list entry.");
        addCode(content, "BLOCK 711711");
        addBody(content, "Hard-block a sender. UNBLOCK removes the block. A block always wins over authorization and forwarding preferences.");
        addCode(content, "MODE CODES");
        addBody(content, "Set automatic forwarding to Security codes only. MODE ALL, MODE SELECTED, and MODE OFF are also supported; MODE never expands the hard security authorization.");
        addCode(content, "PREFER 711711");
        addBody(content, "Add an already-authorized sender to the Selected automatic-forward list. UNPREFER removes it.");
        addCode(content, "LIST");
        addBody(content, "Return the phone's current authorization, block list, automatic-forwarding mode, preferred list, outgoing authorization, and remote-management state.");
        addCode(content, "STATUS");
        addBody(content, "Return the app's most recent routing/status message. HELP returns the command cheat sheet.");

        addSection(content, "Why security and forwarding are separate",
                "A fully trusted second phone can be authorized for messages from any sender while automatically receiving only security codes. A less-trusted phone can be authorized for only a few senders; choosing a broader automatic-forwarding preference still cannot expand that authorization.");

        addSection(content, "Android permissions",
                "The app requests RECEIVE_SMS and SEND_SMS only. It does not request READ_SMS and does not have Internet permission. On Android 13+ a sideloaded install may require App Info → top-right ⋮ → Allow restricted settings before Android will grant the SMS permissions.");

        addSection(content, "Privacy and diagnostics",
                "Message bodies and verification codes are not stored in the diagnostic history. The app keeps only a small local runtime activity log with routing/result descriptions and addressing metadata so you can distinguish: SMS never reached the app, SMS reached the app but rules rejected it, send was queued, or Android reported a send failure. Remote-management commands are recorded only as rule/result descriptions, not message bodies. The runtime log is not included in Android backup.");

        addSection(content, "If nothing happened",
                "Open this Help page and check Current setup and Recent activity. For an incoming SMS, you should see an “Incoming SMS received” event if Android delivered the broadcast to the app. For a bracket command, you should see whether it was blocked, queued, sent, or failed. If no receive event appears at all, investigate Android SMS permission/restricted-settings or device delivery rather than the forwarding filter.");

        Button done = new Button(this);
        done.setText("Back to setup");
        done.setAllCaps(false);
        done.setTextSize(16);
        done.setTextColor(0xFFFFFFFF);
        done.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        done.setBackgroundTintList(ColorStateList.valueOf(palette.accent));
        done.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams doneParams = fullWidth();
        doneParams.topMargin = dp(22);
        content.addView(done, doneParams);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(palette.background);
        scroll.addView(content);
        return scroll;
    }

    private void addCurrentSetup(LinearLayout content) {
        TextView title = heading("Current setup", 20);
        title.setPadding(0, dp(22), 0, dp(6));
        content.addView(title);

        boolean receive = checkSelfPermission(Manifest.permission.RECEIVE_SMS)
                == PackageManager.PERMISSION_GRANTED;
        boolean send = checkSelfPermission(Manifest.permission.SEND_SMS)
                == PackageManager.PERMISSION_GRANTED;
        List<PhoneProfile> profiles = ForwardingPreferences.profiles(this);

        StringBuilder setup = new StringBuilder();
        setup.append("Receive SMS: ").append(receive ? "granted" : "MISSING");
        setup.append("\nSend SMS: ").append(send ? "granted" : "MISSING");
        setup.append("\nDownstream phones: ").append(profiles.size());
        for (PhoneProfile profile : profiles) {
            setup.append("\n• ").append(profile.summary());
        }
        setup.append("\nLast status: ").append(ForwardingPreferences.status(this));
        addBody(content, setup.toString());

        List<String> activity = ForwardingPreferences.recentActivity(this);
        TextView recent = heading("Recent activity", 17);
        recent.setPadding(0, dp(14), 0, dp(4));
        content.addView(recent);
        if (activity.isEmpty()) {
            addBody(content, "No runtime activity has been recorded yet.");
        } else {
            StringBuilder log = new StringBuilder();
            for (String item : activity) {
                if (log.length() > 0) log.append("\n");
                log.append("• ").append(item);
            }
            TextView logView = body(log.toString());
            logView.setTextIsSelectable(true);
            content.addView(logView);
        }
    }

    private void addSection(LinearLayout content, String title, String body) {
        TextView heading = heading(title, 20);
        heading.setPadding(0, dp(22), 0, dp(4));
        content.addView(heading);
        addBody(content, body);
    }

    private void addBullets(LinearLayout content, String... bullets) {
        StringBuilder text = new StringBuilder();
        for (String bullet : bullets) {
            if (text.length() > 0) text.append("\n");
            text.append("• ").append(bullet);
        }
        addBody(content, text.toString());
    }

    private void addCode(LinearLayout content, String code) {
        TextView view = new TextView(this);
        view.setText(code);
        view.setTextSize(16);
        view.setTextColor(palette.text);
        view.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        view.setTextIsSelectable(true);
        view.setPadding(dp(12), dp(10), dp(12), dp(10));
        view.setBackground(roundedBackground(palette.surface, 10, true));
        LinearLayout.LayoutParams params = fullWidth();
        params.topMargin = dp(8);
        params.bottomMargin = dp(6);
        content.addView(view, params);
    }

    private void addBody(LinearLayout content, String text) {
        TextView view = body(text);
        view.setTextIsSelectable(true);
        content.addView(view);
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

    private GradientDrawable roundedBackground(int color, int radiusDp, boolean border) {
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

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
