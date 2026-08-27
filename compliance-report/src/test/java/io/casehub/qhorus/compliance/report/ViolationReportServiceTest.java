package io.casehub.qhorus.compliance.report;

import io.casehub.ledger.runtime.service.LedgerVerificationService;
import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.channel.ChannelSemantic;
import io.casehub.qhorus.api.store.ChannelStore;
import io.casehub.qhorus.compliance.model.ViolationReport;
import io.casehub.qhorus.runtime.ledger.MessageLedgerEntry;
import io.casehub.qhorus.runtime.ledger.MessageLedgerEntryRepository;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ViolationReportServiceTest {

    static final String TENANCY = "test-tenant";
    static final Instant FROM = Instant.parse("2026-08-01T00:00:00Z");
    static final Instant TO = Instant.parse("2026-08-31T23:59:59Z");

    final UUID channelId = UUID.randomUUID();

    @Mock MessageLedgerEntryRepository ledgerRepo;
    @Mock ChannelStore channelStore;
    @Mock LedgerVerificationService verificationService;
    @Mock Instance<LedgerVerificationService> verificationServiceInstance;

    ViolationReportService service;

    @BeforeEach
    void setUp() {
        service = new ViolationReportService();
        service.ledgerRepo = ledgerRepo;
        service.channelStore = channelStore;
        service.verificationServiceInstance = verificationServiceInstance;
    }

    @Test
    void generate_extractsEnforcementEvents() {
        when(channelStore.find(channelId)).thenReturn(Optional.of(channel()));

        MessageLedgerEntry enfEntry = ledgerEntry("system:enforcement", "2026-08-15T10:00:00Z");
        when(ledgerRepo.listEntries(eq(channelId), eq(Set.of("EVENT")), any(), eq("system:enforcement"),
                eq(FROM), any(), eq(false), eq(10000), eq(TENANCY)))
                .thenReturn(List.of(enfEntry));
        when(ledgerRepo.listEntries(eq(channelId), eq(Set.of("EVENT")), any(), eq("system:watchdog"),
                eq(FROM), any(), eq(false), eq(10000), eq(TENANCY)))
                .thenReturn(List.of());
        when(verificationServiceInstance.isResolvable()).thenReturn(false);

        ViolationReport report = service.generate(channelId, FROM, TO, TENANCY);

        assertThat(report.violations()).hasSize(1);
        assertThat(report.totalBlocked()).isEqualTo(1);
        assertThat(report.channelId()).isEqualTo(channelId);
    }

    @Test
    void generate_includesWatchdogAlerts() {
        when(channelStore.find(channelId)).thenReturn(Optional.of(channel()));

        MessageLedgerEntry wdEntry = ledgerEntry("system:watchdog", "2026-08-20T10:00:00Z");
        when(ledgerRepo.listEntries(eq(channelId), eq(Set.of("EVENT")), any(), eq("system:enforcement"),
                eq(FROM), any(), eq(false), eq(10000), eq(TENANCY)))
                .thenReturn(List.of());
        when(ledgerRepo.listEntries(eq(channelId), eq(Set.of("EVENT")), any(), eq("system:watchdog"),
                eq(FROM), any(), eq(false), eq(10000), eq(TENANCY)))
                .thenReturn(List.of(wdEntry));
        when(verificationServiceInstance.isResolvable()).thenReturn(false);

        ViolationReport report = service.generate(channelId, FROM, TO, TENANCY);

        assertThat(report.violations()).hasSize(1);
        assertThat(report.totalAdvisory()).isEqualTo(1);
    }

    @Test
    void generate_aggregatesViolationsBySource() {
        when(channelStore.find(channelId)).thenReturn(Optional.of(channel()));

        MessageLedgerEntry e1 = ledgerEntry("system:enforcement", "2026-08-10T10:00:00Z");
        MessageLedgerEntry e2 = ledgerEntry("system:enforcement", "2026-08-12T10:00:00Z");
        MessageLedgerEntry e3 = ledgerEntry("system:watchdog", "2026-08-15T10:00:00Z");

        when(ledgerRepo.listEntries(eq(channelId), eq(Set.of("EVENT")), any(), eq("system:enforcement"),
                eq(FROM), any(), eq(false), eq(10000), eq(TENANCY)))
                .thenReturn(List.of(e1, e2));
        when(ledgerRepo.listEntries(eq(channelId), eq(Set.of("EVENT")), any(), eq("system:watchdog"),
                eq(FROM), any(), eq(false), eq(10000), eq(TENANCY)))
                .thenReturn(List.of(e3));
        when(verificationServiceInstance.isResolvable()).thenReturn(false);

        ViolationReport report = service.generate(channelId, FROM, TO, TENANCY);

        assertThat(report.violations()).hasSize(3);
        assertThat(report.violationsBySource()).containsEntry("enforcement", 2);
        assertThat(report.violationsBySource()).containsEntry("watchdog", 1);
    }

    @Test
    void generate_filtersOutEntriesAfterToTimestamp() {
        when(channelStore.find(channelId)).thenReturn(Optional.of(channel()));

        MessageLedgerEntry inRange = ledgerEntry("system:enforcement", "2026-08-15T10:00:00Z");
        MessageLedgerEntry outOfRange = ledgerEntry("system:enforcement", "2026-09-05T10:00:00Z");

        when(ledgerRepo.listEntries(eq(channelId), eq(Set.of("EVENT")), any(), eq("system:enforcement"),
                eq(FROM), any(), eq(false), eq(10000), eq(TENANCY)))
                .thenReturn(List.of(inRange, outOfRange));
        when(ledgerRepo.listEntries(eq(channelId), eq(Set.of("EVENT")), any(), eq("system:watchdog"),
                eq(FROM), any(), eq(false), eq(10000), eq(TENANCY)))
                .thenReturn(List.of());
        when(verificationServiceInstance.isResolvable()).thenReturn(false);

        ViolationReport report = service.generate(channelId, FROM, TO, TENANCY);

        assertThat(report.violations()).hasSize(1);
        assertThat(report.totalBlocked()).isEqualTo(1);
    }

    private Channel channel() {
        return new Channel(channelId, "test-channel", null, ChannelSemantic.APPEND,
                List.of(), List.of(), List.of(), null, null,
                null, null, false, false, null,
                List.of(), List.of(), List.of(), null, null,
                List.of(), null, TENANCY, Instant.now(), null);
    }

    private MessageLedgerEntry ledgerEntry(String actorId, String occurredAt) {
        MessageLedgerEntry entry = new MessageLedgerEntry();
        entry.id = UUID.randomUUID();
        entry.actorId = actorId;
        entry.occurredAt = Instant.parse(occurredAt);
        entry.messageType = "EVENT";
        entry.channelId = channelId;
        return entry;
    }
}
