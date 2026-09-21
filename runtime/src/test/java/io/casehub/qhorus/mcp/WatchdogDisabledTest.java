package io.casehub.qhorus.mcp;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.casehub.qhorus.testing.QhorusTestHelper;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Issue #44 — Watchdog tools return informative errors when disabled (default).
 * Refs #44, Epic #36.
 */
@QuarkusTest
class WatchdogDisabledTest {

    @Inject QhorusTestHelper helper;

    @Test
    @TestTransaction
    void registerWatchdogDisabledThrows() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> helper.registerWatchdog("BARRIER_STUCK", "test-channel", 300, null, null,
                        "alerts", "human", null));
        assertTrue(ex.getMessage().toLowerCase().contains("watchdog"),
                "error should mention watchdog");
    }

    @Test
    @TestTransaction
    void listWatchdogsDisabledThrows() {
        assertThrows(IllegalStateException.class, () -> helper.listWatchdogs());
    }

    @Test
    @TestTransaction
    void deleteWatchdogDisabledThrows() {
        assertThrows(IllegalStateException.class,
                () -> helper.deleteWatchdog(java.util.UUID.randomUUID().toString()));
    }
}
