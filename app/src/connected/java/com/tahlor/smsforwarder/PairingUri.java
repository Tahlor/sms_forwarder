package com.tahlor.smsforwarder;

import java.net.URI;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;

/** Strict parser for smsbridge://pair URIs. The one-time token is never persisted here. */
final class PairingUri {
    final String server;
    final String token;

    private PairingUri(String server, String token) {
        this.server = server;
        this.token = token;
    }

    static PairingUri parse(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Paste a pairing URI first.");
        }
        try {
            URI uri = new URI(value.trim());
            if (!"smsbridge".equalsIgnoreCase(uri.getScheme())
                    || !"pair".equalsIgnoreCase(uri.getHost())
                    || (uri.getRawPath() != null && !uri.getRawPath().isEmpty())
                    || uri.getRawFragment() != null) {
                throw new IllegalArgumentException("This is not an SMS Bridge pairing URI.");
            }
            Map<String, String> values = new HashMap<>();
            String query = uri.getRawQuery();
            if (query != null) {
                for (String pair : query.split("&")) {
                    int separator = pair.indexOf('=');
                    if (separator < 0) continue;
                    String key = decode(pair.substring(0, separator));
                    String entry = decode(pair.substring(separator + 1));
                    if (values.put(key, entry) != null) {
                        throw new IllegalArgumentException("The pairing URI contains duplicate fields.");
                    }
                }
            }
            String server = values.get("server");
            String token = values.get("token");
            validateServer(server);
            if (token == null || token.trim().isEmpty()) {
                throw new IllegalArgumentException("The pairing URI is missing its one-time token.");
            }
            return new PairingUri(trimTrailingSlash(server.trim()), token.trim());
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("The pairing URI could not be read.", e);
        }
    }

    static String validateServer(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("The server URL is missing.");
        }
        try {
            URI server = new URI(value.trim());
            String scheme = server.getScheme();
            String host = server.getHost();
            if (scheme == null || host == null || host.isEmpty()
                    || server.getRawUserInfo() != null
                    || server.getRawQuery() != null || server.getRawFragment() != null) {
                throw new IllegalArgumentException("The server URL is invalid.");
            }
            if ("https".equalsIgnoreCase(scheme)) return trimTrailingSlash(value.trim());
            if ("http".equalsIgnoreCase(scheme) && isLoopback(host)) {
                return trimTrailingSlash(value.trim());
            }
            throw new IllegalArgumentException(
                    "Use HTTPS for the server. HTTP is allowed only for localhost.");
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("The server URL is invalid.", e);
        }
    }

    private static boolean isLoopback(String value) {
        String host = value.toLowerCase(java.util.Locale.ROOT);
        if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
        }
        return host.equals("localhost") || host.equals("127.0.0.1");
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 is unavailable.", e);
        }
    }

    private static String trimTrailingSlash(String value) {
        String result = value;
        while (result.endsWith("/") && result.length() > "https://x".length()) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
