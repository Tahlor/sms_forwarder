package com.tahlor.smsforwarder;

import org.junit.Test;

import static org.junit.Assert.fail;

public class ClassicNetworkBoundaryTest {
    @Test
    public void classicKeepsTheHistoricalPackageAndContainsNoConnectedWorker() {
        try {
            Class.forName("com.tahlor.smsforwarder.ConnectedSyncWorker");
            fail("Connected network worker must not be packaged in classic.");
        } catch (ClassNotFoundException expected) {
            // Connected sources are excluded from this flavor.
        }
    }
}
