package io.casehub.qhorus.runtime.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Tests that template match fields are included in the tamper-evidence hash chain.
 * Refs casehubio/qhorus#546.
 */
class TemplateLedgerFieldsTest {

    @Test
    void domainContentBytes_includesTemplateFields() {
        MessageLedgerEntry entry = new MessageLedgerEntry();
        entry.channelId = UUID.randomUUID();
        entry.messageId = 1L;
        entry.messageType = "DONE";
        entry.templateNamespace = "protocol:review";
        entry.templateName = "vote";
        entry.templateFields = "{\"decision\":\"approve\"}";
        entry.templateScore = 0.95;

        String canonical = new String(entry.domainContentBytes(), StandardCharsets.UTF_8);

        assertThat(canonical).contains("|T:");
        assertThat(canonical).contains("protocol:review");
        assertThat(canonical).contains("vote");
        assertThat(canonical).contains("{\"decision\":\"approve\"}");
        assertThat(canonical).contains("0.95");
    }

    @Test
    void domainContentBytes_noTemplateFields_noTemplateSuffix() {
        MessageLedgerEntry entry = new MessageLedgerEntry();
        entry.channelId = UUID.randomUUID();
        entry.messageId = 1L;
        entry.messageType = "DONE";

        String canonical = new String(entry.domainContentBytes(), StandardCharsets.UTF_8);

        assertThat(canonical).doesNotContain("|T:");
    }

    @Test
    void domainContentBytes_partialTemplateFields_includesSuffix() {
        MessageLedgerEntry entry = new MessageLedgerEntry();
        entry.channelId = UUID.randomUUID();
        entry.messageId = 1L;
        entry.messageType = "DONE";
        entry.templateName = "vote";

        String canonical = new String(entry.domainContentBytes(), StandardCharsets.UTF_8);

        assertThat(canonical).contains("|T:|vote||");
    }

    @Test
    void domainContentBytes_templateFieldsDifferentFromNonTemplate() {
        MessageLedgerEntry withTemplate = new MessageLedgerEntry();
        withTemplate.channelId = UUID.randomUUID();
        withTemplate.messageId = 1L;
        withTemplate.messageType = "DONE";
        withTemplate.templateName = "vote";

        MessageLedgerEntry withoutTemplate = new MessageLedgerEntry();
        withoutTemplate.channelId = withTemplate.channelId;
        withoutTemplate.messageId = 1L;
        withoutTemplate.messageType = "DONE";

        String withBytes = new String(withTemplate.domainContentBytes(), StandardCharsets.UTF_8);
        String withoutBytes = new String(withoutTemplate.domainContentBytes(), StandardCharsets.UTF_8);

        assertThat(withBytes).isNotEqualTo(withoutBytes);
    }
}
