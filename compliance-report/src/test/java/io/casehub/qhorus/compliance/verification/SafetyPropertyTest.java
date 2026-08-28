package io.casehub.qhorus.compliance.verification;

import io.casehub.qhorus.runtime.ledger.MessageLedgerEntry;
import io.casehub.qhorus.runtime.ledger.MessageLedgerEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SafetyPropertyTest {

    private SafetyProperty property;
    private MessageLedgerEntryRepository messageRepo;

    @BeforeEach
    void setUp() {
        property = new SafetyProperty();
        messageRepo = mock(MessageLedgerEntryRepository.class);
        property.messageRepo = messageRepo;
    }

    @Test
    void allDoneEntriesHaveAttestationReturnsEmpty() {
        when(messageRepo.findDoneEntriesWithoutAttestation(any(), any(), eq("default")))
                .thenReturn(List.of());

        Instant now = Instant.now();
        CheckResult result = property.check("default", now.minus(7, ChronoUnit.DAYS), now);

        assertThat(result.passed()).isTrue();
        assertThat(result.violations()).isEmpty();
    }

    @Test
    void doneEntryWithoutAttestationReturnsViolation() {
        var entry = new MessageLedgerEntry();
        entry.id = UUID.randomUUID();
        entry.correlationId = "corr-2";
        entry.channelId = UUID.randomUUID();
        entry.occurredAt = Instant.now().minus(1, ChronoUnit.HOURS);

        when(messageRepo.findDoneEntriesWithoutAttestation(any(), any(), eq("default")))
                .thenReturn(List.of(entry));

        Instant now = Instant.now();
        CheckResult result = property.check("default", now.minus(7, ChronoUnit.DAYS), now);

        assertThat(result.passed()).isFalse();
        assertThat(result.violations()).hasSize(1);
        assertThat(result.violations().get(0).propertyName()).isEqualTo("SAFETY");
        assertThat(result.violations().get(0).severity()).isEqualTo("HIGH");
        assertThat(result.violations().get(0).evidence()).contains("corr-2");
    }

    @Test
    void metadataIsCorrect() {
        assertThat(property.name()).isEqualTo("SAFETY");
        assertThat(property.ctlFormula()).contains("FULFILLED");
        assertThat(property.description()).isNotBlank();
    }
}
