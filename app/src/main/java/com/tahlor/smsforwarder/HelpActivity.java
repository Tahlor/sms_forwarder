package com.tahlor.smsforwarder;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

public final class HelpActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Examples & help");
        setContentView(buildContent());
    }

    private ScrollView buildContent() {
        int pad = dp(20);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(pad, pad, pad, pad);

        TextView title = heading("Examples & help", 24);
        content.addView(title);

        TextView setup = heading("Current setup", 19);
        setup.setPadding(0, dp(18), 0, dp(6));
        content.addView(setup);

        boolean receive = checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED;
        boolean send = checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED;
        List<PhoneProfile> profiles = ForwardingPreferences.profiles(this);

        StringBuilder setupText = new StringBuilder();
        setupText.append("Receive SMS: ").append(receive ? "granted" : "MISSING");
        setupText.append("\nSend SMS: ").append(send ? "granted" : "MISSING");
        setupText.append("\nRegistered phones: ").append(profiles.size());
        for (PhoneProfile profile : profiles) {
            setupText.append("\n• ").append(profile.summary());
        }
        setupText.append("\nLast status: ").append(ForwardingPreferences.status(this));
        addBody(content, setupText.toString());

        addSection(content, "Forward verification codes",
                "Register the downstream phone number. Leave Receive forwarded SMS, the 6+ digit filter, and code-only copy enabled. A source SMS such as ‘Your code is 123456’ is forwarded to that registered phone, followed by a second SMS containing only 123456.");

        addSection(content, "Send a command to shortcode 711711",
                "On the registered downstream phone, make sure shortcode control is enabled for that phone. Send this SMS to the phone running SMS Code Forwarder:\n\n[711711] SAVE\n\nThe app recognizes the registered sender, sends only SAVE to raw shortcode 711711, and opens a 5-minute reply window. [711-711] SAVE works too.");

        addSection(content, "Receive the shortcode reply",
                "For 5 minutes after a command, a reply received from 711711 is sent back to the same registered controller as:\n\n[711-711] <reply text>\n\nSending another command restarts that phone’s 5-minute window.");

        addSection(content, "Open a reply window without sending anything",
                "Send [711711] with nothing after it. The app opens the 5-minute return window but does not send an SMS to the shortcode.");

        addSection(content, "Use more than one downstream phone",
                "Add each phone once under Registered phones. Each phone independently chooses whether it receives forwarded SMS, gets a code-only copy, and can control shortcodes. There are no separate destination/controller number fields.");

        addSection(content, "If [711711] SAVE does nothing",
                "1. Current setup above must show both SMS permissions granted.\n2. The sending phone must appear under Registered phones.\n3. That phone must say shortcode relay/controller is enabled.\n4. The command must be sent to the phone running this app.\n5. Check Last status above after the attempt. The app now records whether a relay command was unregistered, relay-disabled, missing SEND_SMS permission, queued, or rejected synchronously.\n6. Some carriers/devices can still block outbound SMS to specific shortcodes.");

        addSection(content, "Authorize a sideloaded install",
                "On Android 13+, SMS permissions may be blocked until you open App Info for this app, tap the top-right ⋮ menu, choose Allow restricted settings, and return. The main screen’s Authorize SMS access button guides this flow and retries the permissions when you come back.");

        Button done = new Button(this);
        done.setText("Back to setup");
        done.setOnClickListener(v -> finish());
        done.setPadding(0, dp(10), 0, 0);
        content.addView(done, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        return scroll;
    }

    private void addSection(LinearLayout content, String title, String body) {
        TextView heading = heading(title, 19);
        heading.setPadding(0, dp(18), 0, dp(4));
        content.addView(heading);
        addBody(content, body);
    }

    private void addBody(LinearLayout content, String body) {
        TextView text = new TextView(this);
        text.setText(body);
        text.setTextSize(16);
        text.setTextIsSelectable(true);
        content.addView(text);
    }

    private TextView heading(String text, int size) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
