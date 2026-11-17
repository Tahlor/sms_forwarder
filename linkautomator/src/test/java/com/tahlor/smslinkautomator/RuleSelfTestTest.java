package com.tahlor.smslinkautomator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public final class RuleSelfTestTest {
    @Test public void defaultExampleWorksWithoutEnablingAnything() {
        Rule sample = Rule.example();
        assertEquals(false, sample.autoArm);
        assertEquals(false, sample.alwaysOn);
        RuleSelfTest.Result result = RuleSelfTest.run(sample);
        assertTrue(result.report(), result.success());
        assertEquals(12, result.total);
        assertEquals(0L, sample.armedUntilMs);
        assertEquals(false, sample.autoArm);
    }

    @Test public void canTestASecondTrustedSenderWithoutChangingItsState() {
        Rule configured = Rule.example();
        configured.sender = "8015550123";
        configured.allowedHosts = "example.com";
        configured.autoArm = true;
        configured.armedAtMs = 500;
        configured.armedUntilMs = 1000;
        RuleSelfTest.Result result = RuleSelfTest.run(configured);
        assertTrue(result.report(), result.success());
        assertEquals(500, configured.armedAtMs);
        assertEquals(1000, configured.armedUntilMs);
    }
}
