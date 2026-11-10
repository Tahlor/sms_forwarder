package com.tahlor.smsforwarder;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class PairingUriTest {
    @Test
    public void parsesEncodedPairingUriAndOneTimeToken() {
        PairingUri pairing = PairingUri.parse(
                "smsbridge://pair?server=https%3A%2F%2Fsms.example.test%2Fbase&token=one-time");
        assertEquals("https://sms.example.test/base", pairing.server);
        assertEquals("one-time", pairing.token);
    }

    @Test
    public void permitsHttpOnlyForLoopbackAndRequiresHttpsOtherwise() {
        assertEquals("http://localhost:8080", PairingUri.validateServer("http://localhost:8080/"));
        assertEquals("http://127.0.0.1:8080", PairingUri.validateServer("http://127.0.0.1:8080"));
        assertRejected("http://sms.example.test");
        assertRejected("https://user@sms.example.test");
        assertRejected("smsbridge://pair?server=https%3A%2F%2Fsms.example.test&token=t&token=x");
    }

    private static void assertRejected(String value) {
        try {
            if (value.startsWith("http") || value.startsWith("https")) {
                PairingUri.validateServer(value);
            } else {
                PairingUri.parse(value);
            }
            fail("Expected URI to be rejected: " + value);
        } catch (IllegalArgumentException expected) {
            // Expected: non-local HTTP, credentials in URL, or duplicate pairing fields.
        }
    }
}
