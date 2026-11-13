package com.tahlor.smslinkautomator;

import org.junit.Test;
import java.net.URI;
import java.util.List;
import static org.junit.Assert.*;

public final class RuleEngineTest {
    @Test public void defaultsAreDisabled() {
        Rule r = Rule.example();
        assertFalse(r.alwaysOn);
        assertFalse(r.autoArm);
        assertEquals("34653", r.sender);
        assertEquals("claim.quikly.com", r.allowedHosts);
        assertEquals("heads-up", r.headsUpPhrase);
    }

    @Test public void independentModesAndExpiration() {
        long t = 1_000_000L;
        Rule r = Rule.example();
        r.autoArm = true;
        assertFalse(RuleEngine.evaluate(r, "Claim now: https://claim.quikly.com/t", t).shouldFollow);
        RuleEngine.Decision head = RuleEngine.evaluate(r, "Here's a HEADS-UP: coming soon", t + 1000);
        assertTrue(head.armed);
        assertFalse(head.shouldFollow);
        assertEquals(t + 1000 + 3_600_000L, r.armedUntilMs);
        assertTrue(RuleEngine.evaluate(r, "Claim https://claim.quikly.com/t", t + 2000).shouldFollow);
        assertFalse(RuleEngine.evaluate(r, "Claim https://claim.quikly.com/t", t + 3_600_001L + 1000).shouldFollow);
        r.autoArm = false;
        r.armedAtMs = 0;
        r.armedUntilMs = 0;
        assertFalse(RuleEngine.evaluate(r, "Claim", t + 3000).shouldFollow);
        r.alwaysOn = true;
        assertTrue(RuleEngine.evaluate(r, "Claim", t + 3000).shouldFollow);
        assertFalse(RuleEngine.evaluate(r, "heads-up", t + 3000).armed);
    }

    @Test public void rearmingExtendsFromLatestHeadsUpAndSameSmsDoesNotAutoClick() {
        Rule r = Rule.example();
        r.autoArm = true;
        long t = 10000;
        RuleEngine.evaluate(r, "heads-up", t);
        RuleEngine.Decision d = RuleEngine.evaluate(r, "heads-up https://claim.quikly.com/123", t + 30000);
        assertTrue(d.armed);
        assertFalse(d.shouldFollow);
        assertEquals(t + 30000 + 3600000, r.armedUntilMs);
        r.alwaysOn = true;
        assertTrue(RuleEngine.evaluate(r, "heads-up https://claim.quikly.com/123", t + 31000).shouldFollow);
    }

    @Test public void exactSenderAndHostOnly() {
        Rule r = Rule.example();
        assertTrue(RuleEngine.matchesSender(r, "34653"));
        assertFalse(RuleEngine.matchesSender(r, "34654"));
        assertFalse(RuleEngine.matchesSender(r, "134653"));
        assertNotNull(RuleEngine.safeUri("https://claim.quikly.com/11785/abc?q=1", r));
        assertNull(RuleEngine.safeUri("http://claim.quikly.com/x", r));
        assertNull(RuleEngine.safeUri("https://claim.quikly.com.attacker.com/x", r));
        assertNull(RuleEngine.safeUri("https://evil@claim.quikly.com/x", r));
        assertNull(RuleEngine.safeUri("https://claim.quikly.com:8080/x", r));
        assertNull(RuleEngine.safeUri("https://localhost/x", r));
        assertNull(RuleEngine.safeUri("https://127.0.0.1/x", r));
        assertNull(RuleEngine.safeUri("https://claim.quikly.com/x#fragment", r));
    }

    @Test public void anyApprovedLinkInArmedMessageWithoutExtraKeywords() {
        Rule r = Rule.example();
        List<URI> links = RuleEngine.eligibleLinks(r,
                "Claim now: https://claim.quikly.com/11785/38b78cu6ks. Also https://bad.example/a!");
        assertEquals(1, links.size());
        assertEquals("https://claim.quikly.com/11785/38b78cu6ks", links.get(0).toString());
        assertEquals(0, RuleEngine.eligibleLinks(r, "No link").size());
    }

    @Test public void configValidationFailsClosed() {
        Rule r = Rule.example();
        assertNull(RuleEngine.validate(r));
        r.allowedHosts = "localhost";
        assertNotNull(RuleEngine.validate(r));
        r.allowedHosts = "claim.quikly.com, 10.0.0.1";
        assertNotNull(RuleEngine.validate(r));
        r.allowedHosts = "claim.quikly.com";
        r.sender = "*";
        assertNotNull(RuleEngine.validate(r));
        r.sender = "34653";
        r.autoArm = true;
        r.headsUpPhrase = "";
        assertNotNull(RuleEngine.validate(r));
    }
}
