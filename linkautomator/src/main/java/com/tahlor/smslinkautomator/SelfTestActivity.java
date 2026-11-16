package com.tahlor.smslinkautomator;

import android.app.Activity;
import android.os.Bundle;
import android.os.Build;
import android.content.Intent;
import android.net.Uri;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.net.URI;
import java.util.List;

/**
 * Deterministic, offline state-machine test. Does not touch saved rules or send
 * an SMS; network mode requires a separately entered URL and an explicit tap.
 */
public final class SelfTestActivity extends Activity {
    private Rule selected;
    private TextView results;
    private EditText urlInput;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        String id = getIntent().getStringExtra("id");
        for (Rule r : RuleStore.get(this)) {
            if (r.id.equals(id)) { selected = r; break; }
        }
        if (selected == null) { finish(); return; }

        ScrollView scroll = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int)(16 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);
        scroll.addView(layout);
        setContentView(scroll);
        TextView title = text("Test " + selected.name, 23);
        layout.addView(title);
        layout.addView(text("The simulation is offline and does not change your live rule, send SMS, or visit links.", 14));
        Button dryRun = new Button(this);
        dryRun.setText("Run heads-up → claim → expiry test");
        dryRun.setAllCaps(false);
        layout.addView(dryRun);
        results = text("", 15);
        layout.addView(results);
        dryRun.setOnClickListener(v -> runDryTest());
        layout.addView(text("Real SMS reception test", 18));
        layout.addView(text("To verify the SMS receiver end-to-end, add a separate test rule for the number of another phone you control and a harmless HTTPS hostname. Enable auto-arm, then send a heads-up SMS and a second SMS containing a link from that other phone. Watch Recent activity on the main screen. Sending SMS to your own number may be delivered as outgoing only, and it cannot impersonate a short code such as 34653.", 14));
        layout.addView(text("Optional real HTTPS request", 18));
        layout.addView(text("Paste a harmless URL on one of this rule's exact approved hosts. This is a REAL network request; do not enter a promotional claim link for testing.", 14));
        urlInput = new EditText(this);
        urlInput.setSingleLine(true);
        urlInput.setHint("https://approved-host.example/path");
        urlInput.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_URI);
        layout.addView(urlInput);
        Button network = new Button(this);
        network.setText("Visit entered URL once");
        network.setAllCaps(false);
        layout.addView(network);
        network.setOnClickListener(v -> {
            String value = urlInput.getText().toString().trim();
            URI eligible = RuleEngine.safeUri(value, selected);
            if (eligible == null) {
                results.setText("BLOCKED: URL is invalid or hostname is not approved by this rule.");
                return;
            }
            network.setEnabled(false);
            results.setText("Issuing one authorized request. A 200 response is not proof of a claim.");
            new Thread(() -> {
                String outcome = LinkExecutor.execute(getApplicationContext(), selected, eligible);
                runOnUiThread(() -> {
                    network.setEnabled(true);
                    results.setText("Manual network test on " + eligible.getHost() + ": " + outcome);
                });
            }, "link-test").start();
        });
        runDryTest();
    }

    private TextView text(String value, int size) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setPadding(0, 8, 0, 8);
        return view;
    }

    private void runDryTest() {
        Rule rule = new Rule();
        rule.id = selected.id;
        rule.name = selected.name;
        rule.sender = selected.sender;
        rule.allowedHosts = selected.allowedHosts;
        rule.headsUpPhrase = selected.headsUpPhrase == null || selected.headsUpPhrase.trim().isEmpty()
                ? "heads-up" : selected.headsUpPhrase;
        rule.durationMinutes = selected.durationMinutes;
        rule.autoArm = true;
        rule.alwaysOn = false;
        long start = 1_000_000L;
        StringBuilder report = new StringBuilder();
        boolean before = !RuleEngine.evaluate(rule, "claim link arriving early", start).shouldFollow;
        boolean trigger = RuleEngine.evaluate(rule, rule.headsUpPhrase, start + 1).armed;
        boolean triggerNotFollowed = !RuleEngine.evaluate(rule, rule.headsUpPhrase, start + 2).shouldFollow;
        boolean during = RuleEngine.evaluate(rule, "later SMS without keyword", start + 3).shouldFollow;
        boolean expired = !RuleEngine.evaluate(rule, "later SMS", start + 3
                + rule.durationMinutes * 60_000L).shouldFollow;
        String host = "";
        for (String candidate : RuleEngine.allowedHosts(rule)) { host = candidate; break; }
        boolean hostRejected = RuleEngine.safeUri("https://evil.invalid/c", rule) == null;
        boolean hostAllowed = !host.isEmpty()
                && RuleEngine.safeUri("https://" + host + "/dry-run", rule) != null;
        rule.autoArm = false;
        rule.alwaysOn = true;
        boolean always = RuleEngine.evaluate(rule, "later SMS", start + 100).shouldFollow;
        report.append(step("Inactive before heads-up", before));
        report.append(step("Heads-up arms the rule", trigger));
        report.append(step("Heads-up not auto-followed", triggerNotFollowed));
        report.append(step("Later SMS eligible during window", during));
        report.append(step("Window expires automatically", expired));
        report.append(step("Approved host accepted", hostAllowed));
        report.append(step("Unapproved host rejected", hostRejected));
        report.append(step("Always-on works independently", always));
        results.setText(report.toString());
    }

    private String step(String label, boolean ok) {
        return (ok ? "PASS  " : "FAIL  ") + label + "\n";
    }
}
