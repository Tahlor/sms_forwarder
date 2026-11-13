package com.tahlor.smslinkautomator;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.List;
import java.util.UUID;

public final class RuleEditorActivity extends Activity {
    private Rule existing;
    private EditText name, sender, hosts, phrase, duration;
    private CheckBox always, auto;
    private RadioGroup mode;
    private int browserId;
    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }
    private EditText field(LinearLayout root, String label, String value, boolean number) {
        TextView title = new TextView(this);
        title.setText(label);
        title.setTextSize(14);
        title.setPadding(0, dp(8), 0, 0);
        root.addView(title);
        EditText input = new EditText(this);
        input.setSingleLine(true);
        if (number) input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(value);
        root.addView(input);
        return input;
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        String id = getIntent().getStringExtra("id");
        for (Rule rule : RuleStore.get(this)) {
            if (rule.id.equals(id)) { existing = rule; break; }
        }
        Rule r = existing == null ? new Rule() : existing;
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(12), dp(18), dp(20));
        scroll.addView(root);
        setContentView(scroll);
        TextView heading = new TextView(this);
        heading.setText(existing == null ? "Add a rule" : "Edit " + r.name);
        heading.setTextSize(22);
        root.addView(heading);
        name = field(root, "Rule name", r.name, false);
        sender = field(root, "Exact trusted sender (short code / number)", r.sender, false);
        hosts = field(root, "Approved HTTPS hostnames (comma separated)", r.allowedHosts, false);
        phrase = field(root, "Heads-up text (case-insensitive contains match)", r.headsUpPhrase, false);
        duration = field(root, "Auto-arm duration in minutes (1-1440)", Integer.toString(r.durationMinutes), true);
        always = new CheckBox(this);
        always.setText("Always follow eligible links");
        always.setChecked(r.alwaysOn);
        root.addView(always);
        auto = new CheckBox(this);
        auto.setText("Enable auto-arm when heads-up text matches");
        auto.setChecked(r.autoArm);
        root.addView(auto);
        TextView action = new TextView(this);
        action.setText("Automatic link action");
        action.setTextSize(16);
        root.addView(action);
        mode = new RadioGroup(this);
        RadioButton http = new RadioButton(this);
        int httpId = View.generateViewId();
        http.setId(httpId);
        http.setText("Background HTTPS GET (reliable request; no JavaScript/cookies)");
        mode.addView(http);
        RadioButton browser = new RadioButton(this);
        browserId = View.generateViewId();
        browser.setId(browserId);
        browser.setText("Launch browser (may be blocked by Android in background)");
        mode.addView(browser);
        mode.check(Rule.MODE_BROWSER.equals(r.mode) ? browserId : httpId);
        root.addView(mode);
        TextView warning = new TextView(this);
        warning.setText("A link is followed only if the sender and exact approved host match, and either Always is on or the timed window is active. The heads-up SMS only arms the window. Changes to sender, hosts or phrase cancel the current timed window.");
        warning.setTextSize(12);
        warning.setPadding(0, dp(14), 0, dp(12));
        root.addView(warning);
        Button save = new Button(this);
        save.setText("Save rule");
        save.setOnClickListener(v -> save());
        root.addView(save);
        if (existing != null) {
            Button delete = new Button(this);
            delete.setText("Delete rule");
            delete.setOnClickListener(v -> {
                RuleStore.remove(this, existing.id);
                RuleStore.log(this, "Deleted rule " + existing.name);
                finish();
            });
            root.addView(delete);
        }
    }

    private void save() {
        Rule r = new Rule();
        r.id = existing == null ? UUID.randomUUID().toString() : existing.id;
        r.name = name.getText().toString().trim();
        r.sender = RuleEngine.normalizeSender(sender.getText().toString());
        r.allowedHosts = hosts.getText().toString().trim();
        r.headsUpPhrase = phrase.getText().toString().trim();
        r.alwaysOn = always.isChecked();
        r.autoArm = auto.isChecked();
        try {
            r.durationMinutes = Integer.parseInt(duration.getText().toString().trim());
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Enter a duration in minutes.", Toast.LENGTH_LONG).show();
            return;
        }
        r.mode = mode.getCheckedRadioButtonId() == browserId ? Rule.MODE_BROWSER : Rule.MODE_HTTP;
        String error = RuleEngine.validate(r);
        if (error != null) {
            Toast.makeText(this, error, Toast.LENGTH_LONG).show();
            return;
        }
        if (existing != null && r.autoArm
                && r.sender.equals(existing.sender)
                && r.allowedHosts.equals(existing.allowedHosts)
                && r.headsUpPhrase.equals(existing.headsUpPhrase)
                && r.durationMinutes == existing.durationMinutes) {
            r.armedAtMs = existing.armedAtMs;
            r.armedUntilMs = existing.armedUntilMs;
        }
        RuleStore.update(this, r);
        RuleStore.log(this, "Saved rule " + r.name + " (no SMS content stored)");
        finish();
    }
}
