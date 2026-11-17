package com.tahlor.smslinkautomator;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.net.URI;

/**
 * One-tap offline self-test. Network/real-SMS checks are explicitly separate
 * because a self-sent SMS cannot reliably exercise Android's incoming broadcast.
 */
public final class SelfTestActivity extends Activity {
    private Rule selected;
    private TextView results;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        String id = getIntent().getStringExtra("id");
        if (id != null) {
            for (Rule candidate : RuleStore.get(this)) {
                if (candidate.id.equals(id)) { selected = candidate; break; }
            }
        }
        if (selected == null) selected = Rule.example();

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, dp(16), pad, dp(24));
        scroll.addView(root);
        setContentView(scroll);
        EdgeToEdge.apply(this, scroll);

        root.addView(text("SMS self-test", 24));
        root.addView(text("Runs automatically with the " + selected.name
                + " sample. No setup, permission, or internet connection required.", 14));
        results = text("", 15);
        root.addView(results);
        Button again = button("Run test again");
        again.setOnClickListener(v -> runTest());
        root.addView(again);
        root.addView(text("This validates SMS matching and link decisions locally. "
                + "It does not prove the carrier delivered an incoming SMS or that a promotion was claimed.", 13));

        Button advancedToggle = button("Advanced: manual live checks");
        root.addView(advancedToggle);
        LinearLayout advanced = new LinearLayout(this);
        advanced.setOrientation(LinearLayout.VERTICAL);
        advanced.setVisibility(View.GONE);
        root.addView(advanced);
        advancedToggle.setOnClickListener(v -> advanced.setVisibility(
                advanced.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));

        advanced.addView(text("Real SMS delivery", 18));
        advanced.addView(text("To test the real Android receive path, configure a temporary "
                + "sender rule for another phone you control, then send a heads-up and "
                + "later an approved HTTPS link from that phone. Check Recent activity. "
                + "An SMS sent to your own number may not arrive as an incoming broadcast, "
                + "and cannot impersonate a short code.", 14));
        advanced.addView(text("Optional real HTTPS request", 18));
        advanced.addView(text("Only use a harmless test URL on an approved domain. "
                + "This sends a real request and never verifies a promotional claim.", 14));
        EditText url = new EditText(this);
        url.setSingleLine(true);
        url.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        url.setHint("https://approved-host/path");
        advanced.addView(url);
        Button network = button("Visit entered URL once");
        advanced.addView(network);
        TextView networkResult = text("", 14);
        advanced.addView(networkResult);
        network.setOnClickListener(v -> {
            URI eligible = RuleEngine.safeUri(url.getText().toString().trim(), selected);
            if (eligible == null) {
                networkResult.setText("BLOCKED: URL invalid or host not approved by this rule.");
                return;
            }
            network.setEnabled(false);
            networkResult.setText("Sending one approved HTTPS request...");
            new Thread(() -> {
                String outcome = LinkExecutor.execute(getApplicationContext(), selected, eligible);
                runOnUiThread(() -> {
                    network.setEnabled(true);
                    networkResult.setText("Request to " + eligible.getHost() + ": " + outcome);
                });
            }, "link-test").start();
        });
        runTest();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView text(String value, int size) {
        TextView label = new TextView(this);
        label.setText(value);
        label.setTextSize(size);
        label.setPadding(0, dp(6), 0, dp(6));
        return label;
    }

    private Button button(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        return button;
    }

    private void runTest() {
        results.setText(RuleSelfTest.run(selected).report());
    }
}
