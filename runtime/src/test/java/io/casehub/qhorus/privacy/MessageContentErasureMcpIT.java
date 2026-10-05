package io.casehub.qhorus.privacy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.runtime.channel.ChannelService;
import io.casehub.qhorus.runtime.ledger.MessageLedgerEntry;
import io.casehub.qhorus.runtime.ledger.MessageLedgerEntryRepository;
import io.casehub.ledger.api.model.ErasureReason;
import io.casehub.qhorus.runtime.privacy.MessageContentErasureService;
import io.casehub.qhorus.testing.QhorusTestHelper;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@TestTransaction
class MessageContentErasureMcpIT {

    @Inject QhorusTestHelper helper;
    @Inject ChannelService channelService;
    @Inject MessageLedgerEntryRepository ledgerRepo;
    @Inject MessageContentErasureService erasureService;

    @Test
    void eraseMessageContent_viaMcpTool_returnsConfirmation() {
        String chName = "mcp-erasure-" + UUID.randomUUID().toString().substring(0, 8);
        helper.createChannel(chName);
        helper.registerInstance("agent-mcp", null);
        helper.sendMessage(chName, "agent-mcp", "status", "mcp erasure target");

        UUID channelId = channelService.findByName(chName).map(Channel::id).orElseThrow();
        var entries = ledgerRepo.findByChannelId(channelId, null);
        MessageLedgerEntry entry = entries.stream()
                .filter(e -> "mcp erasure target".equals(e.content))
                .findFirst().orElseThrow();

        var result = erasureService.eraseMessageContent(
                entry.id, ErasureReason.GDPR_ART_17_REQUEST);

        assertThat(result.erasedEntryId()).isEqualTo(entry.id);
        assertThat(result.tombstoneEntryId()).isNotNull();
    }
}
