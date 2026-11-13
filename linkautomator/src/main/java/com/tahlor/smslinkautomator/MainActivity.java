package com.tahlor.smslinkautomator;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;

public final class MainActivity extends Activity {
    private LinearLayout content;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (Build.VERSION.SDK_INT >= 23 &&
                checkSelfPermission(Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECEIVE_SMS}, 100);
        }
        refresh();
    }
    @Override protected void onResume() { super.onResume(); refresh(); }
    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(requestCode, permissions, grants);
        refresh();
    }
    private TextView text(String value, int size) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setPadding(0, dp(5), 0, dp(5));
        return t;
    }
    private int dp(int value) {
        return (int)(value * getResources().getDisplayMetrics().density + 0.5f);
    }
    private Button button(String title, View.OnClickListener action) {
        Button b = new Button(this);
        b.setText(title);
        b.setAllCaps(false);
        b.setOnClickListener(action);
        return b;
    }
    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }
    private void section(String label) {
        TextView t = text(label, 18);
        t.setTypeface(null, Typeface.BOLD);
        content.addView(t);
    }
    private void refresh() {
        ScrollView scroll = new ScrollView(this);
        content = column();
        int padding = dp(14);
        content.setPadding(padding, padding, padding, padding);
        scroll.addView(content);
        setContentView(scroll);
        section("SMS Link Automator");
        content.addView(text("Automatically visit eligible links from trusted SMS senders. No tap needed. Rules start disabled.", 14));
        boolean hasPermission = Build.VERSION.SDK_INT < 23 ||
                checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED;
        content.addView(text(hasPermission
                ? "SMS receive permission granted"
                : "SMS receive permission required. Enable it in Android settings if the prompt was dismissed.", 14));
        if (!hasPermission) {
            content.addView(button("Request SMS permission", v -> {
                if (Build.VERSION.SDK_INT >= 23)
                    requestPermissions(new String[]{Manifest.permission.RECEIVE_SMS}, 101);
            }));
        }

        section("Rules");
        List<Rule> rules = RuleStore.get(this);
        if (rules.isEmpty()) content.addView(text("No rules configured.", 14));
        long now = System.currentTimeMillis();
        for (Rule r : rules) {
            LinearLayout item = column();
            item.setPadding(dp(8), dp(8), dp(8), dp(8));
            TextView title = text(r.name + "  ·  " + r.sender, 17);
            title.setTypeface(null, Typeface.BOLD);
            item.addView(title);
            item.addView(text("Allowed HTTPS hosts: " + r.allowedHosts, 13));
            CheckBox always = new CheckBox(this);
            always.setText("Always follow eligible links");
            always.setChecked(r.alwaysOn);
            always.setOnCheckedChangeListener((btn, checked) -> {
                r.alwaysOn = checked;
                RuleStore.update(this, r);
                RuleStore.log(this, r.name + ": always-on " + (checked ? "enabled" : "disabled"));
                refresh();
            });
            item.addView(always);
            CheckBox auto = new CheckBox(this);
            auto.setText("Auto-arm for " + r.durationMinutes + " min after heads-up");
            auto.setChecked(r.autoArm);
            auto.setOnCheckedChangeListener((btn, checked) -> {
                r.autoArm = checked;
                if (!checked) { r.armedAtMs = 0; r.armedUntilMs = 0; }
                RuleStore.update(this, r);
                RuleStore.log(this, r.name + ": auto-arm " + (checked ? "enabled" : "disabled"));
                refresh();
            });
            item.addView(auto);
            if (RuleEngine.timedArmActive(r, now)) {
                item.addView(text("ARMED until " + DateFormat.getTimeInstance(DateFormat.SHORT)
                        .format(new Date(r.armedUntilMs)), 14));
                item.addView(button("Disarm timed window", v -> {
                    r.armedAtMs = 0; r.armedUntilMs = 0;
                    RuleStore.update(this, r);
                    RuleStore.log(this, r.name + ": timed window disarmed manually");
                    refresh();
                }));
            } else {
                item.addView(text(r.alwaysOn ? "Active indefinitely" : "Not currently following links", 14));
            }
            item.addView(button("Edit rule", v -> {
                Intent edit = new Intent(this, RuleEditorActivity.class);
                edit.putExtra("id", r.id);
                startActivity(edit);
            }));
            View border = new View(this);
            border.setBackgroundColor(0xffcccccc);
            item.addView(border, new LinearLayout.LayoutParams(-1, dp(1)));
            content.addView(item);
        }
        content.addView(button("+ Add sender rule", v -> startActivity(new Intent(this, RuleEditorActivity.class))));
        section("Recent activity");
        List<String> logs = RuleStore.logs(this);
        if (logs.isEmpty()) content.addView(text("No activity yet. Send an SMS to a configured sender to test.", 13));
        int count = 0;
        for (String event : logs) {
            if (++count > 12) break;
            int separator = event.indexOf('|');
            if (separator < 0) continue;
            try {
                long timestamp = Long.parseLong(event.substring(0, separator));
                content.addView(text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                        .format(new Date(timestamp)) + "  " + event.substring(separator + 1), 12));
            } catch (NumberFormatException ignored) { }
        }
        section("Execution details");
        content.addView(text("Background HTTPS visit sends a GET request, with no JavaScript, browser cookies, or form submission. HTTP response is not proof of a successful claim. Browser mode requests a real browser launch, but Android may block it in the background. Neither mode needs a tap; both can fail, and results are logged.", 13));
        content.addView(text("Only exact approved senders and explicitly allowed HTTPS hosts are eligible. No SMS bodies, URLs, or claim tokens are stored. A timer expires automatically even if the app is not running.", 13));
        content.addView(text("Sideload use only. Android may restrict RECEIVE_SMS; force-stopped apps will not receive messages until opened again.", 12));
    }
}
