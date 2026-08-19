package io.casehub.qhorus.runtime.ledger;

import io.casehub.ledger.api.model.AttestationVerdict;
import io.casehub.ledger.api.model.LedgerAttestation;
import io.casehub.ledger.api.spi.AttestorCredibilityPolicy;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.qhorus.api.message.DispatchResult;
import io.casehub.qhorus.runtime.channel.ChannelEntity;
import io.casehub.qhorus.runtime.mcp.QhorusMcpTools;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@TestTransaction
class AttestorCredibilityIntegrationTest {

    @Inject
    QhorusMcpTools tools;

    @Inject
    LedgerEntryRepository ledger;

    @Inject
    MessageLedgerEntryRepository ledgerRepo;

    @Inject
    AttestorCredibilityPolicy credibilityPolicy;

    @Inject
    CurrentPrincipal currentPrincipal;

    @Test
    void findPeerAttestationsByAttestorIds_returnsPeerVerdicts_notPolicyVerdicts() {
        String ch = "cred-peer-" + System.nanoTime();
        String reviewerId = "rev-peer-" + System.nanoTime();
        setup(ch, "agent-a", "agent-b");

        String corrId = UUID.randomUUID().toString();
        DispatchResult cmd = tools.sendMessage(ch, "agent-a", "command", "task1", null, corrId,
                null, null, null, null, null, null, null);
        tools.sendMessage(ch, "agent-b", "done", "done1", null, corrId,
                cmd.messageId(), null, null, null, null, null, null);

        UUID channelId = channelId(ch);
        MessageLedgerEntry commandEntry = ledgerRepo.findAllByCorrelationId(channelId, corrId, null).stream()
                .filter(e -> "COMMAND".equals(e.messageType))
                .findFirst().orElseThrow();

        ledger.saveAttestation(createAttestation(commandEntry, reviewerId,
                AttestationVerdict.ENDORSED), null);

        List<LedgerAttestation> result = ledger.findPeerAttestationsByAttestorIds(
                Set.of(reviewerId), null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).attestorId).isEqualTo(reviewerId);
        assertThat(result.get(0).verdict).isEqualTo(AttestationVerdict.ENDORSED);
    }

    @Test
    void findPeerAttestationsByAttestorIds_excludesPolicyVerdicts() {
        String ch = "cred-excl-" + System.nanoTime();
        String policyId = "pol-excl-" + System.nanoTime();
        setup(ch, "agent-a", "agent-b");

        String corrId = UUID.randomUUID().toString();
        DispatchResult cmd = tools.sendMessage(ch, "agent-a", "command", "task1", null, corrId,
                null, null, null, null, null, null, null);
        tools.sendMessage(ch, "agent-b", "done", "done1", null, corrId,
                cmd.messageId(), null, null, null, null, null, null);

        UUID channelId = channelId(ch);
        MessageLedgerEntry commandEntry = ledgerRepo.findAllByCorrelationId(channelId, corrId, null).stream()
                .filter(e -> "COMMAND".equals(e.messageType))
                .findFirst().orElseThrow();

        ledger.saveAttestation(createAttestation(commandEntry, policyId,
                AttestationVerdict.SOUND), null);

        List<LedgerAttestation> result = ledger.findPeerAttestationsByAttestorIds(
                Set.of(policyId), null);

        assertThat(result).isEmpty();
    }

    @Test
    void findPeerAttestationsByAttestorIds_returnsChallenged() {
        String ch = "cred-chal-" + System.nanoTime();
        String reviewerId = "rev-chal-" + System.nanoTime();
        setup(ch, "agent-a", "agent-b");

        String corrId = UUID.randomUUID().toString();
        DispatchResult cmd = tools.sendMessage(ch, "agent-a", "command", "task1", null, corrId,
                null, null, null, null, null, null, null);
        tools.sendMessage(ch, "agent-b", "done", "done1", null, corrId,
                cmd.messageId(), null, null, null, null, null, null);

        UUID channelId = channelId(ch);
        MessageLedgerEntry commandEntry = ledgerRepo.findAllByCorrelationId(channelId, corrId, null).stream()
                .filter(e -> "COMMAND".equals(e.messageType))
                .findFirst().orElseThrow();

        ledger.saveAttestation(createAttestation(commandEntry, reviewerId,
                AttestationVerdict.CHALLENGED), null);

        List<LedgerAttestation> result = ledger.findPeerAttestationsByAttestorIds(
                Set.of(reviewerId), null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).verdict).isEqualTo(AttestationVerdict.CHALLENGED);
    }

    @Test
    void findPeerAttestationsByAttestorIds_batchQuery() {
        String ch = "cred-batch-" + System.nanoTime();
        String revA = "rev-ba-" + System.nanoTime();
        String revB = "rev-bb-" + System.nanoTime();
        setup(ch, "agent-a", "agent-b");

        String corrId1 = UUID.randomUUID().toString();
        DispatchResult cmd1 = tools.sendMessage(ch, "agent-a", "command", "task1", null, corrId1,
                null, null, null, null, null, null, null);
        tools.sendMessage(ch, "agent-b", "done", "done1", null, corrId1,
                cmd1.messageId(), null, null, null, null, null, null);

        String corrId2 = UUID.randomUUID().toString();
        DispatchResult cmd2 = tools.sendMessage(ch, "agent-a", "command", "task2", null, corrId2,
                null, null, null, null, null, null, null);
        tools.sendMessage(ch, "agent-b", "done", "done2", null, corrId2,
                cmd2.messageId(), null, null, null, null, null, null);

        UUID channelId = channelId(ch);
        MessageLedgerEntry entry1 = ledgerRepo.findAllByCorrelationId(channelId, corrId1, null).stream()
                .filter(e -> "COMMAND".equals(e.messageType)).findFirst().orElseThrow();
        MessageLedgerEntry entry2 = ledgerRepo.findAllByCorrelationId(channelId, corrId2, null).stream()
                .filter(e -> "COMMAND".equals(e.messageType)).findFirst().orElseThrow();

        ledger.saveAttestation(createAttestation(entry1, revA, AttestationVerdict.ENDORSED), null);
        ledger.saveAttestation(createAttestation(entry2, revB, AttestationVerdict.CHALLENGED), null);

        List<LedgerAttestation> result = ledger.findPeerAttestationsByAttestorIds(
                Set.of(revA, revB), null);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(a -> a.attestorId).containsExactlyInAnyOrder(revA, revB);
    }

    @Test
    void findPeerAttestationPairCounts_groupsByAttestorAndSubject() {
        String ch = "cred-pairs-" + System.nanoTime();
        String reviewerId = "rev-pairs-" + System.nanoTime();
        setup(ch, "agent-a", "agent-b", "agent-c");

        MessageLedgerEntry entryByA1 = dispatchCommandDone(ch, "agent-a", "agent-b");
        MessageLedgerEntry entryByA2 = dispatchCommandDone(ch, "agent-a", "agent-b");
        MessageLedgerEntry entryByC = dispatchCommandDone(ch, "agent-c", "agent-b");

        ledger.saveAttestation(createAttestation(entryByA1, reviewerId, AttestationVerdict.ENDORSED), null);
        ledger.saveAttestation(createAttestation(entryByA2, reviewerId, AttestationVerdict.ENDORSED), null);
        ledger.saveAttestation(createAttestation(entryByC, reviewerId, AttestationVerdict.ENDORSED), null);

        Map<String, Map<String, Long>> result = ledger.findPeerAttestationPairCounts(
                Set.of(reviewerId), null);

        assertThat(result).containsKey(reviewerId);
        Map<String, Long> reviewerPairs = result.get(reviewerId);
        assertThat(reviewerPairs.get("agent-a")).isEqualTo(2L);
        assertThat(reviewerPairs.get("agent-c")).isEqualTo(1L);
    }

    @Test
    void findPeerAttestationPairCounts_emptyForNoEndorsements() {
        Map<String, Map<String, Long>> result = ledger.findPeerAttestationPairCounts(
                Set.of("nonexistent-reviewer"), null);

        assertThat(result).isEmpty();
    }

    @Test
    void credibilityPolicy_assessBatch_endToEnd() {
        String ch = "cred-e2e-" + System.nanoTime();
        String goodReviewer = "good-rev-" + System.nanoTime();
        String badReviewer = "bad-rev-" + System.nanoTime();
        setup(ch, "agent-a", "agent-b");

        for (int i = 0; i < 6; i++) {
            MessageLedgerEntry commandEntry = dispatchCommandDone(ch, "agent-a", "agent-b");
            ledger.saveAttestation(
                    createAttestation(commandEntry, goodReviewer, AttestationVerdict.ENDORSED), null);
            ledger.saveAttestation(
                    createAttestation(commandEntry, badReviewer, AttestationVerdict.CHALLENGED), null);
        }

        var assessments = credibilityPolicy.assessBatch(Set.of(goodReviewer, badReviewer));

        assertThat(assessments.get(goodReviewer).weight()).isGreaterThan(0.5);
        assertThat(assessments.get(badReviewer).weight()).isLessThan(0.5);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void setup(String channel, String... agents) {
        tools.createChannel(channel, "Credibility test channel", "APPEND",
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        for (String agent : agents) {
            tools.registerInstance(channel, agent, null, null, null);
        }
    }

    private UUID channelId(String channelName) {
        return ChannelEntity.<ChannelEntity>find("name", channelName)
                .firstResultOptional()
                .map(ch -> ch.id)
                .orElseThrow(() -> new IllegalStateException("Channel not found: " + channelName));
    }

    private MessageLedgerEntry dispatchCommandDone(String channel, String commander, String doer) {
        String corrId = UUID.randomUUID().toString();
        DispatchResult cmd = tools.sendMessage(channel, commander, "command", "task-" + corrId, null, corrId,
                null, null, null, null, null, null, null);
        tools.sendMessage(channel, doer, "done", "done-" + corrId, null, corrId,
                cmd.messageId(), null, null, null, null, null, null);

        UUID channelId = channelId(channel);
        return ledgerRepo.findAllByCorrelationId(channelId, corrId, null).stream()
                .filter(e -> "COMMAND".equals(e.messageType))
                .findFirst().orElseThrow();
    }

    private static LedgerAttestation createAttestation(MessageLedgerEntry entry, String attestorId,
                                                       AttestationVerdict verdict) {
        LedgerAttestation att = new io.casehub.ledger.runtime.model.LedgerAttestation();
        att.id = UUID.randomUUID();
        att.ledgerEntryId = entry.id;
        att.subjectId = entry.subjectId;
        att.attestorId = attestorId;
        att.attestorType = ActorType.AGENT;
        att.attestorRole = "peer-reviewer";
        att.verdict = verdict;
        att.confidence = 0.4;
        att.occurredAt = Instant.now();
        return att;
    }
}
