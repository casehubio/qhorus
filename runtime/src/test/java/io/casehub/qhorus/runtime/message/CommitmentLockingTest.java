package io.casehub.qhorus.runtime.message;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.casehub.qhorus.api.store.CommitmentStore;
import io.casehub.qhorus.testing.QhorusTestHelper;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@TestTransaction
class CommitmentLockingTest {

    @Inject CommitmentStore commitmentStore;
    @Inject QhorusTestHelper helper;

    @Test
    void findByCorrelationIdForUpdate_returnsCommitment() {
        var ch = helper.createChannel("commit-lock-" + UUID.randomUUID().toString().substring(0, 8));
        helper.registerInstance("agent-cl", null);
        var result = helper.sendMessage(ch.name(), "agent-cl", "command", "do something");
        String corrId = result.correlationId();

        var commitment = commitmentStore.findByCorrelationIdForUpdate(corrId);

        assertThat(commitment).isPresent();
        assertThat(commitment.get().correlationId()).isEqualTo(corrId);
    }

    @Test
    void findByCorrelationIdForUpdate_emptyWhenNotFound() {
        var commitment = commitmentStore.findByCorrelationIdForUpdate("nonexistent-" + UUID.randomUUID());
        assertThat(commitment).isEmpty();
    }
}
