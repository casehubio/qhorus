package io.casehub.qhorus.runtime.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@TestTransaction
class MerkleFrontierLockingTest {

    @Inject QhorusLedgerMerkleFrontierRepository frontierRepo;

    @Test
    void findBySubjectIdForUpdate_returnsEmptyWhenNoFrontier() {
        var result = frontierRepo.findBySubjectIdForUpdate(UUID.randomUUID(), "default");
        assertThat(result).isEmpty();
    }
}
