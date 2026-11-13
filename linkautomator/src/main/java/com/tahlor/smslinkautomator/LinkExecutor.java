package com.tahlor.smslinkautomator;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

public final class LinkExecutor {
    private LinkExecutor() { }

    public static String execute(Context context, Rule rule, URI target) {
        // Validate at the final boundary, even if a caller already filtered the link.
        if (RuleEngine.safeUri(target.toASCIIString(), rule) == null) return "URL rejected";
        if (Rule.MODE_BROWSER.equals(rule.mode)) {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(target.toASCIIString()));
                intent.addCategory(Intent.CATEGORY_BROWSABLE);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                // Android may silently block background activity launches. Not a success claim.
                return "Browser launch requested (Android may block in background)";
            } catch (RuntimeException e) {
                return "Browser launch failed";
            }
        }
        URI current = target;
        for (int redirect = 0; redirect <= 2; redirect++) {
            HttpURLConnection connection = null;
            try {
                // Only allow TLS; never send URL tokens to a disallowed redirect host.
                if (RuleEngine.safeUri(current.toASCIIString(), rule) == null) return "Redirect blocked by host policy";
                URL url = current.toURL();
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(2000);
                connection.setReadTimeout(2000);
                connection.setInstanceFollowRedirects(false);
                connection.setUseCaches(false);
                connection.setRequestMethod("GET");
                int status = connection.getResponseCode();
                if (status >= 300 && status < 400) {
                    String location = connection.getHeaderField("Location");
                    if (location == null || redirect == 2) return "HTTP " + status + " (redirect not followed)";
                    URI next = current.resolve(location);
                    if (RuleEngine.safeUri(next.toASCIIString(), rule) == null) return "Redirect blocked by host policy";
                    current = next;
                    continue;
                }
                // HTTP status proves a request happened, NOT that a giveaway claim succeeded.
                return "HTTPS GET returned " + status;
            } catch (IOException | IllegalArgumentException | SecurityException e) {
                return "HTTPS request failed";
            } finally {
                if (connection != null) connection.disconnect();
            }
        }
        return "Redirect limit reached";
    }
}
