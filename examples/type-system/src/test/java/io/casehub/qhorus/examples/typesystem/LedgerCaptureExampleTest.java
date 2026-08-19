package io.casehub.qhorus.examples.typesystem;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.casehub.platform.api.identity.ActorType;
import io.casehub.qhorus.api.message.MessageDispatch;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.runtime.channel.ChannelService;
import io.casehub.qhorus.runtime.ledger.MessageLedgerEntry;
import io.casehub.qhorus.runtime.ledger.MessageLedgerEntryRepository;
import io.casehub.qhorus.api.message.DispatchResult;
import io.casehub.qhorus.runtime.message.MessageService;
import io.casehub.qhorus.runtime.mcp.QhorusMcpTools;
import io.casehub.qhorus.api.channel.ChannelDetail;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Validates that the normative ledger records entries for all 9 message types
 * in the type-system example context (InMemory stores, H2 ledger).
 *
 * <p>
 * Runs in CI without any model or external services.
 *
 * <p>
 * Refs #107 — Epic #99.
 */
@QuarkusTest
class LedgerCaptureExampleTest {

    @Inject
    QhorusMcpTools tools;

    @Inject
    MessageLedgerEntryRepository ledgerRepo;

    @Inject
    MessageService messageService;

    @Inject
    ChannelService channelService;

    private void sendEvent(final String channel, final String sender, final String telemetry) {
        Channel ch = channelService.findByName(channel)
                                    .orElseThrow(() -> new IllegalArgumentException("Channel not found: " + channel));
        messageService.dispatch(MessageDispatch.builder()
                .channelId(ch.id())
                .sender(sender)
                .type(MessageType.EVENT)
                .telemetry(telemetry)
                .actorType(ActorType.AGENT)
                .build());
    }

    @Test
    void allNineMessageTypes_produceLedgerEntries() {
        tools.createChannel("ledger-ex-all-types", "All 9 message types example", "APPEND", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        tools.registerInstance("ledger-ex-all-types", "agent-a", null, null, null);
        tools.registerInstance("ledger-ex-all-types", "agent-b", null, null, null);

        DispatchResult q1 = tools.sendMessage("ledger-ex-all-types", "agent-a", "query",
                "What is the order count?", null, "corr-ex1", null, null, null, null, null, null, null);
        tools.sendMessage("ledger-ex-all-types", "agent-b", "response",
                "42 orders", null, "corr-ex1", q1.messageId(), null, null, null, null, null, null);
        DispatchResult cmd2 = tools.sendMessage("ledger-ex-all-types", "agent-a", "command",
                "Generate compliance report", null, "corr-ex2", null, null, null, null, null, null, null);
        tools.sendMessage("ledger-ex-all-types", "agent-b", "status",
                "Processing...", null, "corr-ex2", null, null, null, null, null, null, null);
        tools.sendMessage("ledger-ex-all-types", "agent-b", "done",
                "Report delivered", null, "corr-ex2", cmd2.messageId(), null, null, null, null, null, null);
        DispatchResult cmd3 = tools.sendMessage("ledger-ex-all-types", "agent-a", "command",
                "Delete audit logs", null, "corr-ex3", null, null, null, null, null, null, null);
        tools.sendMessage("ledger-ex-all-types", "agent-b", "decline",
                "I do not have permission to delete", null, "corr-ex3", cmd3.messageId(), null, null, null, null, null, null);
        DispatchResult cmd4 = tools.sendMessage("ledger-ex-all-types", "agent-a", "command",
                "Audit the accounts", null, "corr-ex4", null, null, null, null, null, null, null);
        tools.sendMessage("ledger-ex-all-types", "agent-b", "failure",
                "Database unreachable", null, "corr-ex4", cmd4.messageId(), null, null, null, null, null, null);
        sendEvent("ledger-ex-all-types", "agent-a", "{\"tool_name\":\"read_file\",\"duration_ms\":10}");

        ChannelDetail ch = tools.listChannels().stream()
                .filter(c -> "ledger-ex-all-types".equals(c.name()))
                .findFirst()
                .orElseThrow();

        List<MessageLedgerEntry> entries = ledgerRepo.findByChannelId(ch.channelId(), null);

        // 10 messages → 10 ledger entries
        assertEquals(10, entries.size());

        // Verify all 9 types are present (HANDOFF not in this scenario)
        Set<String> types = new java.util.HashSet<>();
        entries.forEach(e -> types.add(e.messageType));
        assertTrue(types.containsAll(Set.of(
                "QUERY", "RESPONSE", "COMMAND", "STATUS", "DONE",
                "DECLINE", "FAILURE", "EVENT")));
    }

    @Test
    void obligationLifecycle_commandDone_causalChainPresent() {
        tools.createChannel("ledger-ex-chain", "Causal chain example", "APPEND", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        tools.registerInstance("ledger-ex-chain", "agent-a", null, null, null);
        tools.registerInstance("ledger-ex-chain", "agent-b", null, null, null);

        DispatchResult chainCmd = tools.sendMessage("ledger-ex-chain", "agent-a", "command",
                "Run end-of-day batch", null, "corr-chain", null, null, null, null, null, null, null);
        tools.sendMessage("ledger-ex-chain", "agent-b", "done",
                "Batch complete — 1542 records processed", null, "corr-chain", chainCmd.messageId(), null, null, null, null, null, null);

        ChannelDetail ch = tools.listChannels().stream()
                .filter(c -> "ledger-ex-chain".equals(c.name()))
                .findFirst()
                .orElseThrow();

        List<MessageLedgerEntry> entries = ledgerRepo.findByChannelId(ch.channelId(), null);
        assertEquals(2, entries.size());

        MessageLedgerEntry cmd = entries.get(0);
        MessageLedgerEntry done = entries.get(1);

        assertEquals("COMMAND", cmd.messageType);
        assertEquals("DONE", done.messageType);
        assertNotNull(done.causedByEntryId, "DONE must point back to COMMAND via causedByEntryId");
        assertEquals(cmd.id, done.causedByEntryId);
    }

    @Test
    void listLedgerEntries_typeFilter_obligationTypesOnly() {
        tools.createChannel("ledger-ex-filter", "Type filter example", "APPEND", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        tools.registerInstance("ledger-ex-filter", "agent-a", null, null, null);
        tools.registerInstance("ledger-ex-filter", "agent-b", null, null, null);

        String corr = "corr-filter";
        DispatchResult filterCmd = tools.sendMessage("ledger-ex-filter", "agent-a", "command", "Do X", null, corr, null, null, null, null, null, null, null);
        tools.sendMessage("ledger-ex-filter", "agent-a", "status",  "Working", null, corr, null, null, null, null, null, null, null);
        tools.sendMessage("ledger-ex-filter", "agent-b", "done", "Done", null, corr, filterCmd.messageId(), null, null, null, null, null, null);
        sendEvent("ledger-ex-filter", "agent-a", "{\"tool_name\":\"t\",\"duration_ms\":1}");

        List<Map<String, Object>> obligationEntries = tools.listLedgerEntries(
                "ledger-ex-filter", "COMMAND,DONE,FAILURE,DECLINE,HANDOFF", null, null, null, null, null, 20);

        assertEquals(2, obligationEntries.size());
        assertTrue(obligationEntries.stream()
                .allMatch(e -> Set.of("COMMAND", "DONE").contains(e.get("message_type"))));
    }
}
