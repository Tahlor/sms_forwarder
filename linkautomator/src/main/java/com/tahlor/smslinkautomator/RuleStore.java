package com.tahlor.smslinkautomator;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class RuleStore {
    private static final String FILE = "link_rules";
    private static final String KEY_RULES = "rules";
    private static final String KEY_LOG = "recent_log";
    private static final String KEY_DEDUP = "recent_dedup";
    private RuleStore() { }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static synchronized List<Rule> get(Context ctx) {
        String raw = prefs(ctx).getString(KEY_RULES, null);
        List<Rule> rules = new ArrayList<>();
        if (raw == null) { rules.add(Rule.example()); return rules; }
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length() && i < 100; i++) {
                JSONObject o = arr.getJSONObject(i);
                Rule r = new Rule();
                r.id = o.optString("id", "");
                r.name = o.optString("name", "");
                r.sender = o.optString("sender", "");
                r.allowedHosts = o.optString("hosts", "");
                r.headsUpPhrase = o.optString("phrase", "");
                r.alwaysOn = o.optBoolean("always", false);
                r.autoArm = o.optBoolean("auto", false);
                r.durationMinutes = o.optInt("minutes", 60);
                r.armedAtMs = o.optLong("at", 0);
                r.armedUntilMs = o.optLong("until", 0);
                r.mode = o.optString("mode", Rule.MODE_HTTP);
                if (RuleEngine.validate(r) == null && !r.id.isEmpty()) rules.add(r);
            }
        } catch (Exception e) {
            // Corrupt config must never activate an automator.
            return new ArrayList<>();
        }
        return rules;
    }

    public static synchronized void save(Context ctx, List<Rule> rules) {
        JSONArray arr = new JSONArray();
        for (Rule r : rules) {
            if (RuleEngine.validate(r) != null) continue;
            JSONObject o = new JSONObject();
            try {
                o.put("id", r.id); o.put("name", r.name); o.put("sender", r.sender);
                o.put("hosts", r.allowedHosts); o.put("phrase", r.headsUpPhrase);
                o.put("always", r.alwaysOn); o.put("auto", r.autoArm);
                o.put("minutes", r.durationMinutes); o.put("at", r.armedAtMs);
                o.put("until", r.armedUntilMs); o.put("mode", r.mode);
                arr.put(o);
            } catch (Exception ignored) { }
        }
        prefs(ctx).edit().putString(KEY_RULES, arr.toString()).commit();
    }

    public static synchronized void update(Context ctx, Rule rule) {
        List<Rule> rules = get(ctx);
        boolean found = false;
        for (int i = 0; i < rules.size(); i++) {
            if (rules.get(i).id.equals(rule.id)) {
                rules.set(i, rule);
                found = true;
                break;
            }
        }
        if (!found) rules.add(rule);
        save(ctx, rules);
    }

    public static synchronized void remove(Context ctx, String id) {
        List<Rule> rules = get(ctx);
        for (int i = rules.size() - 1; i >= 0; i--) {
            if (rules.get(i).id.equals(id)) rules.remove(i);
        }
        save(ctx, rules);
    }

    public static synchronized void log(Context ctx, String event) {
        try {
            JSONArray old = new JSONArray(prefs(ctx).getString(KEY_LOG, "[]"));
            JSONArray latest = new JSONArray();
            latest.put(System.currentTimeMillis() + "|" + event);
            for (int i = 0; i < old.length() && i < 24; i++) latest.put(old.getString(i));
            prefs(ctx).edit().putString(KEY_LOG, latest.toString()).apply();
        } catch (Exception ignored) { }
    }

    public static synchronized List<String> logs(Context ctx) {
        List<String> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs(ctx).getString(KEY_LOG, "[]"));
            for (int i = 0; i < arr.length(); i++) out.add(arr.getString(i));
        } catch (Exception ignored) { }
        return out;
    }

    public static synchronized boolean firstSeen(Context ctx, String sender, long smsTime, String body) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest((sender + "|" + smsTime + "|" + body).getBytes(StandardCharsets.UTF_8));
            StringBuilder key = new StringBuilder();
            for (byte b : bytes) key.append(String.format("%02x", b & 0xff));
            long now = System.currentTimeMillis();
            JSONArray old = new JSONArray(prefs(ctx).getString(KEY_DEDUP, "[]"));
            JSONArray latest = new JSONArray();
            for (int i = 0; i < old.length() && latest.length() < 100; i++) {
                JSONObject item = old.optJSONObject(i);
                if (item == null || now - item.optLong("t") > 86_400_000L) continue;
                if (key.toString().equals(item.optString("key"))) return false;
                latest.put(item);
            }
            JSONObject item = new JSONObject();
            item.put("key", key.toString()); item.put("t", now);
            latest.put(item);
            // Commit before any network request, so duplicate broadcasts cannot follow twice.
            return prefs(ctx).edit().putString(KEY_DEDUP, latest.toString()).commit();
        } catch (Exception e) {
            // Fail closed when persistence/dedup breaks.
            return false;
        }
    }
}
