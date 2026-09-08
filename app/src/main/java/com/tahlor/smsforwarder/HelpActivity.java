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

        content.addView(heading("Examples & help", 24));

        TextView setup = heading("Current setup", 19);
        setup.setPadding(0, dp(18), 0, dp(6));
        content.addView(setup);

        boolean receive = checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED;
        boolean send = checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED;
        List<PhoneProfile> profiles = ForwardingPreferences.profiles(this);

        StringBuilder setupText = new StringBuilder();
        setupText.append("Receive SMS: ").append(receive ? "granted" : "MISSING");
        setupText.append("\nSend SMS: ").append(send ? "granted" : "MISSING");
        setupText.append("\nDownstream phones: ").append(profiles.size());
        for (PhoneProfile profile : profiles) setupText.append("\n• ").append(profile.summary());
        setupText.append("\nLast status: ").append(ForwardingPreferences.status(this));
        addBody(content, setupText.toString());

        addSection(content, "Forward only verification codes",
                "For the downstream phone, set Incoming messages → Forward to Secure codes only. A message such as ‘Your code is 123456’ is forwarded. If Send detected security code as a second text is enabled, 123456 is also sent by itself for easy copying.");

        addSection(content, "Forward every message except certain senders",
                "Set Incoming messages → Forward to All messages. Open Sender rules and add unwanted senders to the block list. The block list is always enforced.");

        addSection(content, "Forward only certain senders",
                "Set Incoming messages → Forward to Selected senders only, then put the permitted numbers or sender IDs in the allow list. You can still add a block list; blocking wins if an entry appears in both lists.");

        addSection(content, "Send a short-code reply from the downstream phone",
                "Set Downstream → outgoing SMS to Short codes only. On the downstream phone, send this to the phone running SMS Forwarder:\n\n[711711] SAVE\n\nThe app sends only SAVE to 711711. [711-711] SAVE works too.");

        addSection(content, "Send to a normal phone number",
                "Set Downstream → outgoing SMS to Any number, or use Selected numbers only and add the destination to the allow list. Then send something like:\n\n[8015551234] Sounds good, see you at 7\n\nThe forwarding phone sends the text to that destination.");

        addSection(content, "Receive a reply after sending",
                "After a bracketed outgoing command, replies from that destination are returned to the same downstream phone for 5 minutes. They arrive as [destination] <reply>. Sending another command refreshes the 5-minute conversation window.");

        addSection(content, "Allow and block lists",
                "Incoming Sender rules and outgoing Destination rules are independent. Selected mode uses its allow list. The block list applies in every enabled mode and always wins over the allow list. Phone-number formatting such as +1, spaces, parentheses, and dashes is normalized when matching.");

        addSection(content, "Use more than one downstream phone",
                "Add each downstream phone separately. Every phone can have its own incoming mode, outgoing mode, allow lists, block lists, and code-copy preference.");

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
