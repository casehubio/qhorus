package io.casehub.qhorus.ledger;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.casehub.platform.api.identity.ActorType;
import io.casehub.qhorus.api.message.MessageDispatch;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.runtime.channel.ChannelService;
import io.casehub.qhorus.runtime.message.MessageService;
import io.casehub.qhorus.testing.QhorusTestHelper;
import io.casehub.qhorus.testing.QhorusTestHelper.CausalChainEntry;
import io.casehub.qhorus.testing.QhorusTestHelper.ObligationChainSummary;
import io.casehub.qhorus.testing.QhorusTestHelper.ObligationStats;
import io.casehub.qhorus.testing.QhorusTestHelper.StalledObligation;
import io.casehub.qhorus.testing.QhorusTestHelper.TelemetrySummary;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;

/**
 * End-to-end scenario tests for ledger query capabilities (Epic #110).
 *
 * <p>
 * Each test method drives a full or partial insurance claim scenario inline,
 * following the established pattern of self-contained {@code @TestTransaction}
 * tests (setup inside the test method, not in {@code @BeforeEach}).
 *
 * <p>
 * Covers: all 9 message types, all 4 channels, all 6 query helper.
 * Obligation patterns: QUERY→RESPONSE, COMMAND→STATUS→DONE, COMMAND→DECLINE,
 * COMMAND→HANDOFF→DONE, COMMAND→FAILURE, EVENT telemetry.
 *
 * <p>
 * Refs #117, Epic #110.
 */
@QuarkusTest
@TestTransaction
class LedgerQueryE2ETest {

    @Inject QhorusTestHelper helper;

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

    // =========================================================================
    // All-9-types smoke test
    // =========================================================================

    @Test
    void e2e_allNineMessageTypes_presentInLedger() {
        final String ch = "e2e-all9-1";
        setup(ch, "coordinator", "worker");

        // QUERY + RESPONSE
        var q1 = helper.sendMessage(ch, "coordinator", "query", "Info?", null, "corr-q1", null, null, null, null, null, null, null);
        helper.sendMessage(ch, "worker", "response", "Here.", null, "corr-q1", q1.messageId(), null, null, null, null, null, null);
        // COMMAND + STATUS + DONE
        var c1 = helper.sendMessage(ch, "coordinator", "command", "Work", null, "corr-c1", null, null, null, null, null, null, null);
        helper.sendMessage(ch, "worker", "status", "Doing it", null, "corr-c1", null, null, null, null, null, null, null);
        helper.sendMessage(ch, "worker", "done", "Done", null, "corr-c1", c1.messageId(), null, null, null, null, null, null);
        // DECLINE
        var c2 = helper.sendMessage(ch, "coordinator", "command", "Do X", null, "corr-c2", null, null, null, null, null, null, null);
        helper.sendMessage(ch, "worker", "decline", "Cannot", null, "corr-c2", c2.messageId(), null, null, null, null, null, null);
        // HANDOFF
        var c3 = helper.sendMessage(ch, "coordinator", "command", "Escalate", null, "corr-c3", null, null, null, null, null, null, null);
        helper.sendMessage(ch, "worker", "handoff", "Passing on", null, "corr-c3", c3.messageId(), null, "instance:coordinator", null, null, null, null);
        // FAILURE
        var c4 = helper.sendMessage(ch, "coordinator", "command", "Risk it", null, "corr-c4", null, null, null, null, null, null, null);
        helper.sendMessage(ch, "worker", "failure", "Failed", null, "corr-c4", c4.messageId(), null, null, null, null, null, null);
        // EVENT
        sendEvent(ch, "worker", "{\"tool_name\":\"ml-score\",\"duration_ms\":100,\"token_count\":50}");

        final List<Map<String, Object>> all = helper.listLedgerEntries(ch, null, null, null, null, null, null, 100);
        final java.util.Set<String> types = all.stream()
                .map(e -> (String) e.get("message_type"))
                .collect(java.util.stream.Collectors.toSet());

        final java.util.Set<String> expected = java.util.Set.of(
                "QUERY", "RESPONSE", "COMMAND", "STATUS", "DONE", "DECLINE", "HANDOFF", "FAILURE", "EVENT");
        assertEquals(expected, types, "All 9 message types must appear in the ledger");
    }

    // =========================================================================
    // list_ledger_entries — enhanced filters over scenario data
    // =========================================================================

    @Test
    void e2e_correlationIdFilter_returnsOnlyFraudChain() {
        final String ch = "e2e-corr-filter-1";
        setup(ch, "coordinator", "fraud-detection");
        var cmdFraud = helper.sendMessage(ch, "coordinator", "command", "Run fraud score", null, "corr-fraud", null, null, null, null, null, null, null);
        sendEvent(ch, "fraud-detection", "{\"tool_name\":\"ml\",\"duration_ms\":200,\"token_count\":100}");
        helper.sendMessage(ch, "fraud-detection", "done", "LOW 0.12", null, "corr-fraud", cmdFraud.messageId(), null, null, null, null, null, null);
        helper.sendMessage(ch, "coordinator", "command", "Unrelated work", null, "corr-other", null, null, null, null, null, null, null);

        final List<Map<String, Object>> entries = helper.listLedgerEntries(ch, null, null, null, null, "corr-fraud", null, 10);

        assertEquals(2, entries.size(), "Only COMMAND+DONE for corr-fraud (EVENT has no correlationId)");
        assertTrue(entries.stream().allMatch(e -> "corr-fraud".equals(e.get("correlation_id"))));
    }

    @Test
    void e2e_sortDesc_newestFirst() {
        final String ch = "e2e-sort-desc-1";
        setup(ch, "coordinator", "worker");
        var cmdSd1 = helper.sendMessage(ch, "coordinator", "command", "A", null, "corr-sd1", null, null, null, null, null, null, null);
        helper.sendMessage(ch, "worker", "status", "B", null, "corr-sd1", null, null, null, null, null, null, null);
        helper.sendMessage(ch, "worker", "done", "C", null, "corr-sd1", cmdSd1.messageId(), null, null, null, null, null, null);

        final List<Map<String, Object>> entries = helper.listLedgerEntries(ch, null, null, null, null, null, "desc", 10);

        assertEquals(3, entries.size());
        assertEquals("DONE", entries.get(0).get("message_type"));
        assertEquals("STATUS", entries.get(1).get("message_type"));
        assertEquals("COMMAND", entries.get(2).get("message_type"));
    }

    // =========================================================================
    // get_obligation_chain — multi-step obligation scenarios
    // =========================================================================

    @Test
    void e2e_obligationChain_commandToDone_sanctionsScenario() {
        final String ch = "e2e-goc-sanctions-1";
        setup(ch, "coordinator", "sanctions-screener");
        var cmdSanc = helper.sendMessage(ch, "coordinator", "command", "Screen Acme Corp on OFAC/HMT", null, "corr-sanctions", null, null, null, null, null, null, null);
        helper.sendMessage(ch, "sanctions-screener", "status", "Screening...", null, "corr-sanctions", null, null, null, null, null, null, null);
        helper.sendMessage(ch, "sanctions-screener", "done", "Acme Corp clear", null, "corr-sanctions", cmdSanc.messageId(), null, null, null, null, null, null);

        final ObligationChainSummary chain = helper.getObligationChain(ch, "corr-sanctions");

        assertEquals("corr-sanctions", chain.correlationId());
        assertEquals("coordinator", chain.initiator());
        assertEquals("DONE", chain.resolution());
        assertEquals(0, chain.handoffCount());
        assertEquals(2, chain.participants().size());
        assertEquals("coordinator", chain.participants().get(0));
        assertEquals("sanctions-screener", chain.participants().get(1));
        assertNotNull(chain.elapsedSeconds());
        assertTrue(chain.elapsedSeconds() >= 0);
    }

    @Test
    void e2e_obligationChain_commandToHandoffToDone_escalationScenario() {
        final String ch = "e2e-goc-escalation-1";
        setup(ch, "coordinator", "compliance-officer", "senior-adjuster");
        var cmdEsc = helper.sendMessage(ch, "coordinator", "command", "Syndicate approval escalation", null, "corr-escalation", null, null, null, null, null, null, null);
        var handoff = helper.sendMessage(ch, "compliance-officer", "handoff", "Escalating to senior", null, "corr-escalation", cmdEsc.messageId(), null, "instance:senior-adjuster", null, null, null, null);
        helper.sendMessage(ch, "senior-adjuster", "done", "LLY-2026-04-789 approved", null, "corr-escalation", handoff.messageId(), null, null, null, null, null, null);

        final ObligationChainSummary chain = helper.getObligationChain(ch, "corr-escalation");

        assertEquals(1, chain.handoffCount());
        assertEquals("DONE", chain.resolution());
        assertEquals(3, chain.participants().size());
        assertEquals("coordinator", chain.participants().get(0));
        assertEquals("compliance-officer", chain.participants().get(1));
        assertEquals("senior-adjuster", chain.participants().get(2));
    }

    @Test
    void e2e_obligationChain_commandToDecline_fcaScenario() {
        final String ch = "e2e-goc-fca-1";
        setup(ch, "coordinator", "compliance-officer");
        var cmdFca = helper.sendMessage(ch, "coordinator", "command", "FCA compliance check", null, "corr-fca-fail", null, null, null, null, null, null, null);
        helper.sendMessage(ch, "compliance-officer", "decline", "Missing Lloyd's syndicate approval", null, "corr-fca-fail", cmdFca.messageId(), null, null, null, null, null, null);

        final ObligationChainSummary chain = helper.getObligationChain(ch, "corr-fca-fail");

        assertEquals("DECLINE", chain.resolution());
        assertNotNull(chain.resolvedAt());
        assertNotNull(chain.elapsedSeconds());
    }

    @Test
    void e2e_obligationChain_openObligation_nullResolution() {
        final String ch = "e2e-goc-open-1";
        setup(ch, "coordinator");
        helper.sendMessage(ch, "coordinator", "command", "Dispatch surveyor", null, "corr-stall-open", null, null, null, null, null, null, null);
        // No response sent — obligation remains open

        final ObligationChainSummary chain = helper.getObligationChain(ch, "corr-stall-open");

        assertNull(chain.resolution());
        assertNull(chain.resolvedAt());
        assertNull(chain.elapsedSeconds());
        assertEquals(1, chain.participants().size());
    }

    @Test
    void e2e_obligationChain_commandToFailure_bacsScenario() {
        final String ch = "e2e-goc-payments-1";
        setup(ch, "coordinator", "payment-processor");
        var cmdBacs = helper.sendMessage(ch, "coordinator", "command", "BACS payout £180k", null, "corr-bacs", null, null, null, null, null, null, null);
        helper.sendMessage(ch, "payment-processor", "failure", "Invalid sort code 20-14-09", null, "corr-bacs", cmdBacs.messageId(), null, null, null, null, null, null);

        final ObligationChainSummary chain = helper.getObligationChain(ch, "corr-bacs");

        assertEquals("FAILURE", chain.resolution());
    }

    // =========================================================================
    // get_causal_chain
    // =========================================================================

    @Test
    void e2e_causalChain_escalationDone_threeHops() {
        final String ch = "e2e-gcc-escalation-1";
        setup(ch, "coordinator", "compliance-officer", "senior-adjuster");
        var cmdEsc2 = helper.sendMessage(ch, "coordinator", "command", "Escalate", null, "corr-esc-gcc", null, null, null, null, null, null, null);
        var hof = helper.sendMessage(ch, "compliance-officer", "handoff", "Escalating", null, "corr-esc-gcc", cmdEsc2.messageId(), null, "instance:senior-adjuster", null, null, null, null);
        helper.sendMessage(ch, "senior-adjuster", "done", "Approved", null, "corr-esc-gcc", hof.messageId(), null, null, null, null, null, null);

        final List<Map<String, Object>> entries = helper.listLedgerEntries(ch, null, null, null, null, "corr-esc-gcc", null, 10);
        assertEquals(3, entries.size());
        final String doneEntryId = (String) entries.get(2).get("entry_id");
        assertNotNull(doneEntryId);

        final List<CausalChainEntry> chain = helper.getCausalChain(ch, doneEntryId);

        assertEquals(3, chain.size());
        assertEquals("COMMAND", chain.get(0).messageType());
        assertEquals("HANDOFF", chain.get(1).messageType());
        assertEquals("DONE", chain.get(2).messageType());
        assertNull(chain.get(0).causedByEntryId());
        assertNotNull(chain.get(1).causedByEntryId());
        assertNotNull(chain.get(2).causedByEntryId());
    }

    @Test
    void e2e_causalChain_commandToDone_twoHops() {
        final String ch = "e2e-gcc-done-1";
        setup(ch, "coordinator", "worker");
        var cmdW1 = helper.sendMessage(ch, "coordinator", "command", "Work", null, "corr-w1", null, null, null, null, null, null, null);
        helper.sendMessage(ch, "worker", "done", "Done", null, "corr-w1", cmdW1.messageId(), null, null, null, null, null, null);

        final List<Map<String, Object>> entries = helper.listLedgerEntries(ch, null, null, null, null, "corr-w1", null, 10);
        final String doneId = (String) entries.get(1).get("entry_id");

        final List<CausalChainEntry> chain = helper.getCausalChain(ch, doneId);

        assertEquals(2, chain.size());
        assertEquals("COMMAND", chain.get(0).messageType());
        assertEquals("DONE", chain.get(1).messageType());
    }

    // =========================================================================
    // list_stalled_obligations
    // =========================================================================

    @Test
    void e2e_stalledObligations_damageAssessor_detectedAtZeroThreshold() {
        final String ch = "e2e-lso-stall-1";
        setup(ch, "coordinator", "sanctions-screener");

        // One completed obligation
        var cmdDone = helper.sendMessage(ch, "coordinator", "command", "Sanctions check", null, "corr-done", null, null, null, null, null, null, null);
        helper.sendMessage(ch, "sanctions-screener", "done", "Clear", null, "corr-done", cmdDone.messageId(), null, null, null, null, null, null);

        // One stalled obligation
        helper.sendMessage(ch, "coordinator", "command", "Dispatch surveyor", null, "corr-stall", null, null, null, null, null, null, null);

        final List<StalledObligation> stalled = helper.listStalledObligations(ch, 0);

        assertEquals(1, stalled.size(), "Only the stalled obligation must appear");
        assertEquals("corr-stall", stalled.get(0).correlationId());
        assertEquals("coordinator", stalled.get(0).actorId());
        assertNotNull(stalled.get(0).occurredAt());
    }

    @Test
    void e2e_stalledObligations_allCompleted_emptyResult() {
        final String ch = "e2e-lso-clean-1";
        setup(ch, "coordinator", "worker");
        var cmdA = helper.sendMessage(ch, "coordinator", "command", "Task A", null, "corr-a", null, null, null, null, null, null, null);
        helper.sendMessage(ch, "worker", "done", "A done", null, "corr-a", cmdA.messageId(), null, null, null, null, null, null);
        var cmdB = helper.sendMessage(ch, "coordinator", "command", "Task B", null, "corr-b", null, null, null, null, null, null, null);
        helper.sendMessage(ch, "worker", "decline", "B refused", null, "corr-b", cmdB.messageId(), null, null, null, null, null, null);

        final List<StalledObligation> stalled = helper.listStalledObligations(ch, 0);

        assertTrue(stalled.isEmpty());
    }

    // =========================================================================
    // get_obligation_stats — cross-channel scenario
    // =========================================================================

    @Test
    void e2e_obligationStats_claimChannel_threeCommands() {
        final String ch = "e2e-gos-claim-1";
        setup(ch, "coordinator", "worker");

        // 2 DONE
        var g1 = helper.sendMessage(ch, "coordinator", "command", "Sanctions", null, "corr-g1", null, null, null, null, null, null, null);
        helper.sendMessage(ch, "worker", "done", "Clear", null, "corr-g1", g1.messageId(), null, null, null, null, null, null);
        var g2 = helper.sendMessage(ch, "coordinator", "command", "Fraud", null, "corr-g2", null, null, null, null, null, null, null);
        helper.sendMessage(ch, "worker", "done", "LOW", null, "corr-g2", g2.messageId(), null, null, null, null, null, null);
        // 1 open (stalled)
        helper.sendMessage(ch, "coordinator", "command", "Dispatch surveyor", null, "corr-g3", null, null, null, null, null, null, null);

        final ObligationStats stats = helper.getObligationStats(ch);

        assertEquals(3, stats.totalCommands());
        assertEquals(2, stats.fulfilled());
        assertEquals(0, stats.failed());
        assertEquals(0, stats.declined());
        assertEquals(0, stats.delegated());
        assertEquals(1, stats.stillOpen());
        assertEquals(2.0 / 3.0, stats.fulfillmentRate(), 0.001);
    }

    @Test
    void e2e_obligationStats_paymentsChannel_failureAndDone() {
        final String ch = "e2e-gos-payments-1";
        setup(ch, "coordinator", "payment-processor");

        var cmdBacs2 = helper.sendMessage(ch, "coordinator", "command", "BACS payout", null, "corr-bacs-gos", null, null, null, null, null, null, null);
        helper.sendMessage(ch, "payment-processor", "failure", "Invalid sort code", null, "corr-bacs-gos", cmdBacs2.messageId(), null, null, null, null, null, null);
        var cmdChaps = helper.sendMessage(ch, "coordinator", "command", "CHAPS retry", null, "corr-chaps-gos", null, null, null, null, null, null, null);
        helper.sendMessage(ch, "payment-processor", "done", "CHAPS confirmed", null, "corr-chaps-gos", cmdChaps.messageId(), null, null, null, null, null, null);

        final ObligationStats stats = helper.getObligationStats(ch);

        assertEquals(2, stats.totalCommands());
        assertEquals(1, stats.fulfilled());
        assertEquals(1, stats.failed());
        assertEquals(0.5, stats.fulfillmentRate(), 0.001);
    }

    @Test
    void e2e_obligationStats_complianceChannel_declineAndDone() {
        final String ch = "e2e-gos-compliance-1";
        setup(ch, "coordinator", "compliance-officer", "regulatory-reporter");

        // FCA fails (decline)
        var cmdFca1 = helper.sendMessage(ch, "coordinator", "command", "FCA check", null, "corr-fca1", null, null, null, null, null, null, null);
        helper.sendMessage(ch, "compliance-officer", "decline", "Missing approval", null, "corr-fca1", cmdFca1.messageId(), null, null, null, null, null, null);
        // FCA passes (done)
        var cmdFca2 = helper.sendMessage(ch, "coordinator", "command", "FCA re-check", null, "corr-fca2", null, null, null, null, null, null, null);
        helper.sendMessage(ch, "compliance-officer", "done", "Verified", null, "corr-fca2", cmdFca2.messageId(), null, null, null, null, null, null);
        // Solvency II (done)
        var cmdSolv = helper.sendMessage(ch, "coordinator", "command", "Solvency II", null, "corr-solv", null, null, null, null, null, null, null);
        helper.sendMessage(ch, "regulatory-reporter", "done", "Filed", null, "corr-solv", cmdSolv.messageId(), null, null, null, null, null, null);

        final ObligationStats stats = helper.getObligationStats(ch);

        assertEquals(3, stats.totalCommands());
        assertEquals(2, stats.fulfilled());
        assertEquals(1, stats.declined());
        assertEquals(0, stats.failed());
    }

    @Test
    void e2e_obligationStats_highValueReview_handoffDelegation() {
        final String ch = "e2e-gos-review-1";
        setup(ch, "coordinator", "compliance-officer", "senior-adjuster");

        var cmdSyn = helper.sendMessage(ch, "coordinator", "command", "Syndicate approval", null, "corr-esc", null, null, null, null, null, null, null);
        var hofSyn = helper.sendMessage(ch, "compliance-officer", "handoff", "Escalating", null, "corr-esc", cmdSyn.messageId(), null, "instance:senior-adjuster", null, null, null, null);
        helper.sendMessage(ch, "senior-adjuster", "done", "Approved", null, "corr-esc", hofSyn.messageId(), null, null, null, null, null, null);

        final ObligationStats stats = helper.getObligationStats(ch);

        // 1 COMMAND + 1 HANDOFF + 1 DONE: delegated counts HANDOFF entries,
        // fulfilled counts DONE entries — both can be 1 for COMMAND→HANDOFF→DONE.
        assertEquals(1, stats.totalCommands());
        assertEquals(1, stats.fulfilled());
        assertEquals(1, stats.delegated());
        assertEquals(0, stats.stillOpen());
        assertEquals(1.0, stats.fulfillmentRate(), 0.001);
    }

    // =========================================================================
    // get_telemetry_summary — multi-tool aggregation
    // =========================================================================

    @Test
    void e2e_telemetrySummary_mlFraudAndSolvencyApi() {
        final String ch = "e2e-gts-multi-1";
        setup(ch, "agent-a");

        // ML fraud score: 2 events
        sendEvent(ch, "agent-a", "{\"tool_name\":\"ml-fraud-score\",\"duration_ms\":2341,\"token_count\":1200}");
        sendEvent(ch, "agent-a", "{\"tool_name\":\"ml-fraud-score\",\"duration_ms\":1859,\"token_count\":1100}");
        // Solvency API: 1 event
        sendEvent(ch, "agent-a", "{\"tool_name\":\"solvency-api\",\"duration_ms\":450,\"token_count\":0}");

        final TelemetrySummary summary = helper.getTelemetrySummary(ch, null);

        assertEquals(3, summary.totalEvents());
        assertEquals(2300, summary.totalTokens());
        assertEquals(4650, summary.totalDurationMs());

        final var mlScore = summary.byTool().get("ml-fraud-score");
        assertNotNull(mlScore);
        assertEquals(2, mlScore.count());
        assertEquals(2100, mlScore.avgDurationMs(), "avg of 2341 and 1859");
        assertEquals(2300, mlScore.totalTokens());

        final var solvency = summary.byTool().get("solvency-api");
        assertNotNull(solvency);
        assertEquals(1, solvency.count());
        assertEquals(450, solvency.avgDurationMs());
        assertEquals(0, solvency.totalTokens());
    }

    @Test
    void e2e_telemetrySummary_nonEventMessagesExcluded() {
        final String ch = "e2e-gts-exclude-1";
        setup(ch, "coordinator", "worker");
        var cmdX = helper.sendMessage(ch, "coordinator", "command", "Work", null, "corr-x", null, null, null, null, null, null, null);
        helper.sendMessage(ch, "worker", "done", "Done", null, "corr-x", cmdX.messageId(), null, null, null, null, null, null);
        sendEvent(ch, "worker", "{\"tool_name\":\"t\",\"duration_ms\":5,\"token_count\":10}");

        final TelemetrySummary summary = helper.getTelemetrySummary(ch, null);

        assertEquals(1, summary.totalEvents());
    }

    @Test
    void e2e_telemetrySummary_missingToolName_countedUnderNull() {
        final String ch = "e2e-gts-null-tool-1";
        setup(ch, "agent-a");
        sendEvent(ch, "agent-a", "{\"duration_ms\":10}");

        final TelemetrySummary summary = helper.getTelemetrySummary(ch, null);

        assertEquals(1, summary.totalEvents());
        assertTrue(summary.byTool().containsKey(null));
    }

    // =========================================================================
    // Cross-channel integration — all 4 channels together
    // =========================================================================

    @Test
    void e2e_crossChannel_obligationStatsSummarizedPerChannel() {
        final String chClaim = "e2e-cross-claim-1";
        final String chCompliance = "e2e-cross-compliance-1";
        final String chPayments = "e2e-cross-payments-1";

        setup(chClaim, "coordinator", "sanctions-screener", "fraud-detection");
        setup(chCompliance, "coordinator", "compliance-officer", "regulatory-reporter");
        setup(chPayments, "coordinator", "payment-processor");

        // CH_CLAIM: 2 DONE
        var ccS = helper.sendMessage(chClaim, "coordinator", "command", "Sanctions", null, "corr-cc-s", null, null, null, null, null, null, null);
        helper.sendMessage(chClaim, "sanctions-screener", "done", "Clear", null, "corr-cc-s", ccS.messageId(), null, null, null, null, null, null);
        var ccF = helper.sendMessage(chClaim, "coordinator", "command", "Fraud", null, "corr-cc-f", null, null, null, null, null, null, null);
        helper.sendMessage(chClaim, "fraud-detection", "done", "LOW", null, "corr-cc-f", ccF.messageId(), null, null, null, null, null, null);

        // CH_COMPLIANCE: 1 DECLINE + 2 DONE
        var ccFc1 = helper.sendMessage(chCompliance, "coordinator", "command", "FCA fail", null, "corr-cc-fc1", null, null, null, null, null, null, null);
        helper.sendMessage(chCompliance, "compliance-officer", "decline", "Missing", null, "corr-cc-fc1", ccFc1.messageId(), null, null, null, null, null, null);
        var ccFc2 = helper.sendMessage(chCompliance, "coordinator", "command", "FCA pass", null, "corr-cc-fc2", null, null, null, null, null, null, null);
        helper.sendMessage(chCompliance, "compliance-officer", "done", "Verified", null, "corr-cc-fc2", ccFc2.messageId(), null, null, null, null, null, null);
        var ccSolv = helper.sendMessage(chCompliance, "coordinator", "command", "Solvency", null, "corr-cc-solv", null, null, null, null, null, null, null);
        helper.sendMessage(chCompliance, "regulatory-reporter", "done", "Filed", null, "corr-cc-solv", ccSolv.messageId(), null, null, null, null, null, null);

        // CH_PAYMENTS: 1 FAILURE + 1 DONE
        var ccBacs = helper.sendMessage(chPayments, "coordinator", "command", "BACS", null, "corr-cc-bacs", null, null, null, null, null, null, null);
        helper.sendMessage(chPayments, "payment-processor", "failure", "Bounced", null, "corr-cc-bacs", ccBacs.messageId(), null, null, null, null, null, null);
        var ccChaps = helper.sendMessage(chPayments, "coordinator", "command", "CHAPS", null, "corr-cc-chaps", null, null, null, null, null, null, null);
        helper.sendMessage(chPayments, "payment-processor", "done", "Paid", null, "corr-cc-chaps", ccChaps.messageId(), null, null, null, null, null, null);

        final ObligationStats claimStats = helper.getObligationStats(chClaim);
        final ObligationStats complianceStats = helper.getObligationStats(chCompliance);
        final ObligationStats paymentStats = helper.getObligationStats(chPayments);

        // Claim: 2/2 fulfilled
        assertEquals(2, claimStats.totalCommands());
        assertEquals(2, claimStats.fulfilled());
        assertEquals(1.0, claimStats.fulfillmentRate(), 0.001);

        // Compliance: 2/3 fulfilled (1 declined)
        assertEquals(3, complianceStats.totalCommands());
        assertEquals(2, complianceStats.fulfilled());
        assertEquals(1, complianceStats.declined());
        assertEquals(2.0 / 3.0, complianceStats.fulfillmentRate(), 0.001);

        // Payments: 1/2 fulfilled (1 failed)
        assertEquals(2, paymentStats.totalCommands());
        assertEquals(1, paymentStats.fulfilled());
        assertEquals(1, paymentStats.failed());
        assertEquals(0.5, paymentStats.fulfillmentRate(), 0.001);
    }

    // =========================================================================
    // Fixture
    // =========================================================================

    private void setup(final String channel, final String... agents) {
        helper.createChannel(channel, "Test channel", "APPEND", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        for (final String agent : agents) {
            helper.registerInstance(channel, agent, null, null, null);
        }
    }
}
