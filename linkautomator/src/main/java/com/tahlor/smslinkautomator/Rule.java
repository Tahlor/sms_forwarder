package com.tahlor.smslinkautomator;

public final class Rule {
    public static final String MODE_HTTP = "http";
    public static final String MODE_BROWSER = "browser";
    public String id = "";
    public String name = "";
    public String sender = "";
    // Comma-separated, exact HTTPS DNS hostnames only. No wildcards.
    public String allowedHosts = "";
    public String headsUpPhrase = "";
    public boolean alwaysOn = false;
    public boolean autoArm = false;
    public int durationMinutes = 60;
    public long armedUntilMs = 0;
    public long armedAtMs = 0;
    public String mode = MODE_HTTP;

    public static Rule example() {
        Rule r = new Rule();
        r.id = "example-quikly";
        r.name = "Quikly (example)";
        r.sender = "34653";
        r.allowedHosts = "claim.quikly.com";
        r.headsUpPhrase = "heads-up";
        r.durationMinutes = 60;
        return r;
    }
}
