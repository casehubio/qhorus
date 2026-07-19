package io.casehub.qhorus.runtime.ledger;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.ledger.api.model.AttestationVerdict;
import io.casehub.ledger.runtime.model.LedgerAttestation;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.qhorus.api.store.InstanceStore;
import io.casehub.qhorus.runtime.config.QhorusConfig;
import io.casehub.qhorus.api.instance.Instance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PeerAttestationWriterTest {

    private PeerAttestationWriter writer;
    private StubLedgerEntryRepository ledger;
    private InstanceStore instanceStore;
    private final List<io.casehub.ledger.api.model.LedgerEntry> entries = new ArrayList<>();
    private static final String TENANT = "test-tenant";

    @BeforeEach
    void setUp() {
        writer = new PeerAttestationWriter();
        ledger = new StubLedgerEntryRepository(entries);
        instanceStore = mock(InstanceStore.class);
        writer.ledger = ledger;
        writer.instanceStore = instanceStore;
        writer.objectMapper = new ObjectMapper();
        writer.config = stubConfig(0.4, 0.5);
    }

    @Test
    void write_endorsed_creates_attestation() {
        MessageLedgerEntry entry = commandEntry("requester-agent");
        entries.add(entry);
        registerInstance("reviewer-agent");

        LedgerAttestation result = writer.write(entry.id, AttestationVerdict.ENDORSED,
                "good work", "reviewer-agent", TENANT);

        assertThat(result.verdict).isEqualTo(AttestationVerdict.ENDORSED);
        assertThat(result.attestorId).isEqualTo("reviewer-agent");
        assertThat(result.attestorType).isEqualTo(ActorType.AGENT);
        assertThat(result.attestorRole).isEqualTo("peer-reviewer");
        assertThat(result.confidence).isEqualTo(0.4);
        assertThat(result.ledgerEntryId).isEqualTo(entry.id);
        assertThat(result.subjectId).isEqualTo(entry.subjectId);
        assertThat(ledger.savedAttestations).hasSize(1);
    }

    @Test
    void write_challenged_uses_challenged_confidence() {
        MessageLedgerEntry entry = commandEntry("requester-agent");
        entries.add(entry);
        registerInstance("reviewer-agent");

        LedgerAttestation result = writer.write(entry.id, AttestationVerdict.CHALLENGED,
                "output incomplete", "reviewer-agent", TENANT);

        assertThat(result.verdict).isEqualTo(AttestationVerdict.CHALLENGED);
        assertThat(result.confidence).isEqualTo(0.5);
    }

    @Test
    void write_rejects_sound_verdict() {
        MessageLedgerEntry entry = commandEntry("requester-agent");
        entries.add(entry);
        registerInstance("reviewer-agent");

        assertThatThrownBy(() -> writer.write(entry.id, AttestationVerdict.SOUND,
                "evidence", "reviewer-agent", TENANT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ENDORSED or CHALLENGED");
    }

    @Test
    void write_rejects_flagged_verdict() {
        MessageLedgerEntry entry = commandEntry("requester-agent");
        entries.add(entry);
        registerInstance("reviewer-agent");

        assertThatThrownBy(() -> writer.write(entry.id, AttestationVerdict.FLAGGED,
                "evidence", "reviewer-agent", TENANT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ENDORSED or CHALLENGED");
    }

    @Test
    void write_rejects_missing_entry() {
        registerInstance("reviewer-agent");

        assertThatThrownBy(() -> writer.write(UUID.randomUUID(), AttestationVerdict.ENDORSED,
                "evidence", "reviewer-agent", TENANT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void write_rejects_non_command_entry() {
        MessageLedgerEntry entry = statusEntry("some-agent");
        entries.add(entry);
        registerInstance("reviewer-agent");

        assertThatThrownBy(() -> writer.write(entry.id, AttestationVerdict.ENDORSED,
                "evidence", "reviewer-agent", TENANT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("COMMAND or HANDOFF");
    }

    @Test
    void write_rejects_self_attestation() {
        MessageLedgerEntry entry = commandEntry("agent-x");
        entries.add(entry);
        registerInstance("agent-x");

        assertThatThrownBy(() -> writer.write(entry.id, AttestationVerdict.ENDORSED,
                "evidence", "agent-x", TENANT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Self-attestation");
    }

    @Test
    void write_rejects_unregistered_attestor() {
        MessageLedgerEntry entry = commandEntry("requester-agent");
        entries.add(entry);
        when(instanceStore.findByInstanceId(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> writer.write(entry.id, AttestationVerdict.ENDORSED,
                "evidence", "unknown-agent", TENANT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a registered instance");
    }

    @Test
    void write_sets_subject_id_from_entry() {
        MessageLedgerEntry entry = commandEntry("requester-agent");
        entries.add(entry);
        registerInstance("reviewer-agent");

        LedgerAttestation result = writer.write(entry.id, AttestationVerdict.ENDORSED,
                "evidence", "reviewer-agent", TENANT);

        assertThat(result.subjectId).isEqualTo(entry.subjectId);
    }

    @Test
    void write_extracts_capability_tag_from_entry_content() {
        MessageLedgerEntry entry = commandEntry("requester-agent");
        entry.content = "{\"capability\": \"code-review\", \"task\": \"review PR\"}";
        entries.add(entry);
        registerInstance("reviewer-agent");

        LedgerAttestation result = writer.write(entry.id, AttestationVerdict.ENDORSED,
                "evidence", "reviewer-agent", TENANT);

        assertThat(result.capabilityTag).isEqualTo("code-review");
    }

    @Test
    void write_accepts_handoff_entry() {
        MessageLedgerEntry entry = handoffEntry("delegator-agent");
        entries.add(entry);
        registerInstance("reviewer-agent");

        LedgerAttestation result = writer.write(entry.id, AttestationVerdict.CHALLENGED,
                "evidence", "reviewer-agent", TENANT);

        assertThat(result.verdict).isEqualTo(AttestationVerdict.CHALLENGED);
    }

    private MessageLedgerEntry commandEntry(String actorId) {
        MessageLedgerEntry e = new MessageLedgerEntry();
        e.id = UUID.randomUUID();
        e.subjectId = UUID.randomUUID();
        e.channelId = UUID.randomUUID();
        e.messageId = 1L;
        e.messageType = "COMMAND";
        e.actorId = actorId;
        e.actorType = ActorType.AGENT;
        e.occurredAt = Instant.now();
        e.content = "{\"task\": \"do something\"}";
        return e;
    }

    private MessageLedgerEntry statusEntry(String actorId) {
        MessageLedgerEntry e = commandEntry(actorId);
        e.messageType = "STATUS";
        return e;
    }

    private MessageLedgerEntry handoffEntry(String actorId) {
        MessageLedgerEntry e = commandEntry(actorId);
        e.messageType = "HANDOFF";
        return e;
    }

    private void registerInstance(String instanceId) {
        Instance inst = Instance.builder(instanceId).build();
        when(instanceStore.findByInstanceId(instanceId)).thenReturn(Optional.of(inst));
    }

    private static QhorusConfig stubConfig(double endorsed, double challenged) {
        QhorusConfig.Attestation att = mock(QhorusConfig.Attestation.class);
        when(att.peerEndorsedConfidence()).thenReturn(endorsed);
        when(att.peerChallengedConfidence()).thenReturn(challenged);
        QhorusConfig cfg = mock(QhorusConfig.class);
        when(cfg.attestation()).thenReturn(att);
        return cfg;
    }
}
