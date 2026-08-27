package io.casehub.qhorus.testing;

import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.platform.api.identity.TenancyConstants;
import io.casehub.qhorus.runtime.ledger.MessageLedgerEntry;

import java.time.Instant;
import java.util.UUID;

/** Builds {@link MessageLedgerEntry} instances with required fields populated. */
public final class MessageLedgerEntryTestFactory {

    private MessageLedgerEntryTestFactory() {}

    public static MessageLedgerEntry entry(String messageType) {
        return entry(UUID.randomUUID(), 1L, messageType, UUID.randomUUID(), null);
    }

    public static MessageLedgerEntry entry(UUID subjectId, Long messageId, String messageType,
            UUID channelId, String correlationId) {
        MessageLedgerEntry e = new MessageLedgerEntry();
        e.subjectId = subjectId;
        e.channelId = channelId;
        e.messageId = messageId;
        e.messageType = messageType;
        e.correlationId = correlationId;
        e.sequenceNumber = 1;
        e.entryType = LedgerEntryType.COMMAND;
        e.actorId = "test-actor";
        e.actorType = ActorType.AGENT;
        e.actorRole = "test-role";
        e.occurredAt = Instant.now();
        e.tenancyId = TenancyConstants.DEFAULT_TENANT_ID; // explicit default — matches QhorusLedgerEntryRepository null normalisation
        return e;
    }

    public static MessageLedgerEntry judgmentEvent(String toolName, UUID judgmentId,
                                                   String judgmentType, UUID channelId, String correlationId) {
        MessageLedgerEntry e = entry(channelId, null, "EVENT", channelId, correlationId);
        e.toolName     = toolName;
        e.judgmentId   = judgmentId;
        e.judgmentType = judgmentType;
        e.entryType    = LedgerEntryType.EVENT;
        return e;
    }
}
