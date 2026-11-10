package com.tahlor.smsforwarder;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Connected launcher that adds the optional server pairing entry to the familiar SMS UI. */
public final class ConnectedMainActivity extends MainActivity {
    static final String EXTRA_PAIRING_URI = "pairing_uri";

    @Override
    protected View buildContent() {
        View view = super.buildContent();
        if (view instanceof ScrollView) {
            View child = ((ScrollView) view).getChildAt(0);
            if (child instanceof LinearLayout) {
                LinearLayout content = (LinearLayout) child;
                LinearLayout card = new LinearLayout(this);
                card.setOrientation(LinearLayout.VERTICAL);
                int horizontal = dpLocal(16);
                card.setPadding(horizontal, dpLocal(14), horizontal, dpLocal(14));
                card.setBackgroundColor(0xFFEAF0FF);

                TextView title = new TextView(this);
                title.setText("Self-hosted server connection");
                title.setTextSize(18);
                title.setTextColor(0xFF17171B);
                title.setTypeface(null, android.graphics.Typeface.BOLD);
                card.addView(title);

                TextView help = new TextView(this);
                help.setText("Pair by QR or paste a pairing URI. Sync and remote actions stay opt-in.");
                help.setTextSize(14);
                help.setTextColor(0xFF555761);
                help.setPadding(0, dpLocal(5), 0, dpLocal(9));
                card.addView(help);

                Button open = new Button(this);
                open.setText("Pair or manage server connection");
                open.setOnClickListener(v -> startActivity(
                        new Intent(this, ConnectedSettingsActivity.class)));
                card.addView(open, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));

                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                params.topMargin = dpLocal(16);
                content.addView(card, params);
                adjustConnectedOnlyText(content);
            }
        }
        return view;
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        ConnectedSyncScheduler.schedulePeriodic(this);
        handlePairingIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handlePairingIntent(intent);
    }

    private void handlePairingIntent(Intent intent) {
        Uri data = intent == null ? null : intent.getData();
        if (data == null || !"smsbridge".equalsIgnoreCase(data.getScheme())
                || !"pair".equalsIgnoreCase(data.getHost())) return;
        Intent settings = new Intent(this, ConnectedSettingsActivity.class)
                .putExtra(EXTRA_PAIRING_URI, data.toString());
        startActivity(settings);
        setIntent(new Intent(this, ConnectedMainActivity.class));
    }

    private void adjustConnectedOnlyText(View view) {
        if (view instanceof Button && "Update app".contentEquals(((Button) view).getText())) {
            view.setVisibility(View.GONE);
        } else if (view instanceof TextView
                && ((TextView) view).getText().toString().startsWith("No Internet permission.")) {
            ((TextView) view).setText(
                    "Connected edition sends data only to the paired server over HTTPS. "
                            + "It does not read SMS history.");
        } else if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) adjustConnectedOnlyText(group.getChildAt(i));
        }
    }

    private int dpLocal(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
