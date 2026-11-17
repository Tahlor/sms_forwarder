package com.tahlor.smslinkautomator;

import java.util.Set;

/**
 * Offline checks of the same decision and URL validation functions used by
 * the SMS receiver. Safe to run with no permissions, configured rules, or network.
 */
public final class RuleSelfTest {
    private RuleSelfTest() { }

    public static final class Result {
        public final int passed;
        public final int total;
        public final String details;

        Result(int passed, int total, String details) {
            this.passed = passed;
            this.total = total;
            this.details = details;
        }

        public boolean success() { return passed == total; }

        public String report() {
            return (success() ? "PASS" : "FAIL") + ": " + passed + "/" + total
                    + " local checks passed.\nNo SMS sent or website visited.\n\n" + details;
        }
    }

    public static Result run(Rule original) {
        Rule source = original == null ? Rule.example() : original;
        // Never mutate the installed rule or its live arming window.
        Rule rule = new Rule();
        rule.id = source.id;
        rule.name = source.name;
        rule.sender = source.sender;
        rule.allowedHosts = source.allowedHosts;
        rule.headsUpPhrase = source.headsUpPhrase == null || source.headsUpPhrase.trim().isEmpty()
                ? "heads-up" : source.headsUpPhrase;
        rule.durationMinutes = source.durationMinutes;
        rule.autoArm = true;
        rule.alwaysOn = false;

        Set<String> hosts = RuleEngine.allowedHosts(rule);
        String host = hosts.isEmpty() ? "" : hosts.iterator().next();
        String approvedLink = "https://" + host + "/self-test?synthetic=1";
        long start = 1_000_000L;
        boolean inactive = !RuleEngine.evaluate(rule, "Offer: " + approvedLink, start).shouldFollow;
        boolean trustedSender = RuleEngine.matchesSender(rule, rule.sender);
        String stranger = RuleEngine.normalizeSender(rule.sender).equals("999999999999")
                ? "888888888888" : "999999999999";
        boolean unknownSender = !RuleEngine.matchesSender(rule, stranger);
        boolean armed = RuleEngine.evaluate(rule, rule.headsUpPhrase, start + 1).armed;
        boolean triggerSafe = !RuleEngine.evaluate(
                rule, rule.headsUpPhrase + " " + approvedLink, start + 2).shouldFollow;
        RuleEngine.Decision during = RuleEngine.evaluate(
                rule, "Offer: " + approvedLink, start + 3);
        boolean laterLink = trustedSender && during.shouldFollow
                && RuleEngine.eligibleLinks(rule, "Offer: " + approvedLink).size() == 1;
        boolean expired = !RuleEngine.evaluate(rule, "Offer: " + approvedLink,
                start + 3 + rule.durationMinutes * 60_000L).shouldFollow;
        boolean allowedHost = !host.isEmpty()
                && RuleEngine.safeUri(approvedLink, rule) != null;
        boolean blockedHost = RuleEngine.safeUri("https://evil.invalid/claim", rule) == null;
        boolean spoofBlocked = RuleEngine.safeUri("https://" + host + ".evil.org/claim", rule) == null;
        boolean insecureBlocked = RuleEngine.safeUri("http://" + host + "/claim", rule) == null;
        rule.autoArm = false;
        rule.alwaysOn = true;
        boolean alwaysOn = RuleEngine.evaluate(rule, approvedLink, start + 100).shouldFollow;

        String[] descriptions = {
                "Inactive before heads-up",
                "Trusted sender recognized",
                "Unknown sender rejected",
                "Heads-up arms the window",
                "Trigger message link not opened",
                "Subsequent approved link eligible",
                "Arming window expires",
                "Approved HTTPS host accepted",
                "Disallowed HTTPS host rejected",
                "Spoofed subdomain rejected",
                "Insecure HTTP rejected",
                "Always-on independent of auto-arm"
        };
        boolean[] passes = {
                inactive, trustedSender, unknownSender, armed, triggerSafe, laterLink,
                expired, allowedHost, blockedHost, spoofBlocked, insecureBlocked, alwaysOn
        };
        int passed = 0;
        StringBuilder details = new StringBuilder();
        for (int i = 0; i < passes.length; i++) {
            if (passes[i]) passed++;
            details.append(passes[i] ? "PASS  " : "FAIL  ")
                    .append(descriptions[i]).append('\n');
        }
        return new Result(passed, passes.length, details.toString());
    }
}
