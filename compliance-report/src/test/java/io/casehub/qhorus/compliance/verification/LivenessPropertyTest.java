package io.casehub.qhorus.compliance.verification;

import io.casehub.qhorus.api.message.Commitment;
import io.casehub.qhorus.api.message.CommitmentState;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.store.CommitmentStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LivenessPropertyTest {

    private LivenessProperty property;
    private CommitmentStore store;

    @BeforeEach
    void setUp() {
        property = new LivenessProperty();
        store = mock(CommitmentStore.class);
        property.commitmentStore = store;
    }

    @Test
    void noStaleCommitmentsReturnsEmpty() {
        when(store.findOpenOlderThan(any(), eq("default"))).thenReturn(List.of());

        Instant now = Instant.now();
        CheckResult result = property.check("default", now.minus(7, ChronoUnit.DAYS), now);

        assertThat(result.passed()).isTrue();
        assertThat(result.violations()).isEmpty();
    }

    @Test
    void staleCommitmentReturnsViolation() {
        Instant staleCreatedAt = Instant.now().minus(48, ChronoUnit.HOURS);
        Commitment stale = Commitment.builder()
                                     .id(UUID.randomUUID())
                                     .correlationId("corr-1")
                                     .channelId(UUID.randomUUID())
                                     .messageType(MessageType.COMMAND)
                                     .requester("requester")
                                     .obligor("obligor")
                                     .state(CommitmentState.OPEN)
                                     .createdAt(staleCreatedAt)
                                     .build();
        when(store.findOpenOlderThan(any(), eq("default"))).thenReturn(List.of(stale));

        Instant     now    = Instant.now();
        CheckResult result = property.check("default", now.minus(7, ChronoUnit.DAYS), now);

        assertThat(result.passed()).isFalse();
        assertThat(result.violations()).hasSize(1);
        assertThat(result.violations().get(0).propertyName()).isEqualTo("LIVENESS");
        assertThat(result.violations().get(0).severity()).isEqualTo("HIGH");
        assertThat(result.violations().get(0).evidence()).contains("corr-1");
    }

    @Test
    void metadataIsCorrect() {
        assertThat(property.name()).isEqualTo("LIVENESS");
        assertThat(property.ctlFormula()).contains("AG");
        assertThat(property.description()).isNotBlank();
    }
}
