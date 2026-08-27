package io.casehub.qhorus.ledger;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.qhorus.api.judgment.JudgmentEventKinds;
import io.casehub.qhorus.runtime.ledger.LedgerWriteService;
import io.casehub.qhorus.runtime.ledger.MessageLedgerEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JudgmentTelemetryTest {

    private LedgerWriteService service;

    @BeforeEach
    void setUp() {
        service = new LedgerWriteService();
        service.objectMapper = new ObjectMapper();
    }

    @Test
    void extractsJudgmentFieldsFromTelemetry() throws Exception {
        var entry = new MessageLedgerEntry();
        entry.messageId = 1L;
        var judgmentId = UUID.randomUUID();
        String telemetry = """
            {"tool_name": "judgment_verified", "judgment_id": "%s",
             "judgment_type": "code_review", "verification_outcome": "ACCEPTED",
             "evidence_quality": 0.85}
            """.formatted(judgmentId);

        invokePopulateTelemetry(entry, telemetry);

        assertThat(entry.toolName).isEqualTo(JudgmentEventKinds.VERIFIED);
        assertThat(entry.judgmentId).isEqualTo(judgmentId);
        assertThat(entry.judgmentType).isEqualTo("code_review");
        assertThat(entry.verificationOutcome).isEqualTo("ACCEPTED");
        assertThat(entry.evidenceQuality).isEqualTo(0.85);
    }

    @Test
    void rejectsEvidenceQualityOutOfRange() throws Exception {
        var entry = new MessageLedgerEntry();
        entry.messageId = 2L;
        String telemetry = """
            {"tool_name": "judgment_responded", "evidence_quality": 1.5}
            """;

        invokePopulateTelemetry(entry, telemetry);

        assertThat(entry.evidenceQuality).isNull();
    }

    @Test
    void rejectsNegativeEvidenceQuality() throws Exception {
        var entry = new MessageLedgerEntry();
        entry.messageId = 3L;
        String telemetry = """
            {"tool_name": "judgment_responded", "evidence_quality": -0.1}
            """;

        invokePopulateTelemetry(entry, telemetry);

        assertThat(entry.evidenceQuality).isNull();
    }

    @Test
    void acceptsBoundaryEvidenceQuality() throws Exception {
        var entry = new MessageLedgerEntry();
        entry.messageId = 4L;
        String telemetry = """
            {"tool_name": "judgment_responded", "evidence_quality": 0.0}
            """;

        invokePopulateTelemetry(entry, telemetry);

        assertThat(entry.evidenceQuality).isEqualTo(0.0);

        var entry2 = new MessageLedgerEntry();
        entry2.messageId = 5L;
        String telemetry2 = """
            {"tool_name": "judgment_responded", "evidence_quality": 1.0}
            """;

        invokePopulateTelemetry(entry2, telemetry2);

        assertThat(entry2.evidenceQuality).isEqualTo(1.0);
    }

    @Test
    void handlesInvalidJudgmentIdUuid() throws Exception {
        var entry = new MessageLedgerEntry();
        entry.messageId = 6L;
        String telemetry = """
            {"tool_name": "judgment_yielded", "judgment_id": "not-a-uuid",
             "judgment_type": "code_review"}
            """;

        invokePopulateTelemetry(entry, telemetry);

        assertThat(entry.judgmentId).isNull();
        assertThat(entry.judgmentType).isEqualTo("code_review");
    }

    @Test
    void domainContentBytesUnchangedForNonJudgmentEntries() throws Exception {
        var entry = new MessageLedgerEntry();
        entry.channelId = UUID.randomUUID();
        entry.messageId = 1L;
        entry.messageType = "COMMAND";

        byte[] hashWithNulls = invokeDomainContentBytes(entry);

        var entry2 = new MessageLedgerEntry();
        entry2.channelId = entry.channelId;
        entry2.messageId = 1L;
        entry2.messageType = "COMMAND";

        assertThat(invokeDomainContentBytes(entry2)).isEqualTo(hashWithNulls);
    }

    @Test
    void domainContentBytesIncludesJudgmentSuffix() throws Exception {
        var entry = new MessageLedgerEntry();
        entry.channelId = UUID.randomUUID();
        entry.messageId = 1L;
        entry.messageType = "EVENT";
        entry.judgmentId = UUID.randomUUID();
        entry.judgmentType = "code_review";
        entry.verificationOutcome = "ACCEPTED";
        entry.evidenceQuality = 0.85;

        String content = new String(invokeDomainContentBytes(entry), StandardCharsets.UTF_8);

        assertThat(content).contains("|J:");
        assertThat(content).contains(entry.judgmentId.toString());
        assertThat(content).contains("code_review");
        assertThat(content).contains("ACCEPTED");
        assertThat(content).contains("0.85");
    }

    @Test
    void domainContentBytesNoCollisionWithPartialFields() throws Exception {
        var entry1 = new MessageLedgerEntry();
        entry1.channelId = UUID.randomUUID();
        entry1.messageId = 1L;
        entry1.judgmentType = "code_review";
        entry1.verificationOutcome = "ACCEPTED";

        var entry2 = new MessageLedgerEntry();
        entry2.channelId = entry1.channelId;
        entry2.messageId = 1L;
        entry2.judgmentType = "code_reviewACCEPTED";

        assertThat(invokeDomainContentBytes(entry1)).isNotEqualTo(invokeDomainContentBytes(entry2));
    }

    @Test
    void domainContentBytesDiffersFromNonJudgmentEntry() throws Exception {
        var base = new MessageLedgerEntry();
        base.channelId = UUID.randomUUID();
        base.messageId = 1L;
        base.messageType = "EVENT";

        var judgment = new MessageLedgerEntry();
        judgment.channelId = base.channelId;
        judgment.messageId = 1L;
        judgment.messageType = "EVENT";
        judgment.judgmentId = UUID.randomUUID();

        assertThat(invokeDomainContentBytes(judgment)).isNotEqualTo(invokeDomainContentBytes(base));
    }

    private void invokePopulateTelemetry(MessageLedgerEntry entry, String telemetry) throws Exception {
        var method = LedgerWriteService.class.getDeclaredMethod(
                "populateTelemetry", MessageLedgerEntry.class, String.class);
        method.setAccessible(true);
        method.invoke(service, entry, telemetry);
    }

    private byte[] invokeDomainContentBytes(MessageLedgerEntry entry) throws Exception {
        var method = MessageLedgerEntry.class.getDeclaredMethod("domainContentBytes");
        method.setAccessible(true);
        return (byte[]) method.invoke(entry);
    }
}
