package io.casehub.qhorus.runtime.ledger;

import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.platform.api.identity.TenancyConstants;
import io.casehub.qhorus.api.judgment.JudgmentEventKinds;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.TestTransaction;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class JudgmentQueryTest {

    @Inject MessageLedgerEntryRepository repo;

    @Inject LedgerEntryRepository ledger;

    @Test
    @TestTransaction
    void findJudgmentEventsFiltersByJudgmentId() {
        var channelId = UUID.randomUUID();
        var judgmentId = UUID.randomUUID();
        var otherJudgmentId = UUID.randomUUID();

        persistJudgmentEvent(channelId, judgmentId, JudgmentEventKinds.YIELDED, "code_review", null, null, Instant.now());
        persistJudgmentEvent(channelId, judgmentId, JudgmentEventKinds.VERIFIED, "code_review", "ACCEPTED", null, Instant.now());
        persistJudgmentEvent(channelId, otherJudgmentId, JudgmentEventKinds.YIELDED, "code_review", null, null, Instant.now());

        var results = repo.findJudgmentEvents(null, judgmentId, null, null, null);

        assertThat(results).hasSize(2);
        assertThat(results).allMatch(e -> e.judgmentId.equals(judgmentId));
    }

    @Test
    @TestTransaction
    void findJudgmentEventsFiltersByChannelId() {
        var channel1 = UUID.randomUUID();
        var channel2 = UUID.randomUUID();
        var judgmentId = UUID.randomUUID();

        persistJudgmentEvent(channel1, judgmentId, JudgmentEventKinds.YIELDED, "code_review", null, null, Instant.now());
        persistJudgmentEvent(channel2, judgmentId, JudgmentEventKinds.RESPONDED, "code_review", null, 0.8, Instant.now());

        var results = repo.findJudgmentEvents(channel1, null, null, null, null);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().channelId).isEqualTo(channel1);
    }

    @Test
    @TestTransaction
    void findJudgmentEventsFiltersByTimeRange() {
        var channelId = UUID.randomUUID();
        var judgmentId = UUID.randomUUID();
        var now = Instant.now();

        persistJudgmentEvent(channelId, judgmentId, JudgmentEventKinds.YIELDED, "code_review", null, null, now.minus(2, ChronoUnit.HOURS));
        persistJudgmentEvent(channelId, judgmentId, JudgmentEventKinds.VERIFIED, "code_review", "ACCEPTED", null, now.minus(1, ChronoUnit.HOURS));
        persistJudgmentEvent(channelId, UUID.randomUUID(), JudgmentEventKinds.YIELDED, "code_review", null, null, now.minus(5, ChronoUnit.HOURS));

        var results = repo.findJudgmentEvents(null, null, now.minus(3, ChronoUnit.HOURS), now, null);

        assertThat(results).hasSize(2);
    }

    @Test
    @TestTransaction
    void findJudgmentEventsReturnsOrderedByOccurredAt() {
        var channelId = UUID.randomUUID();
        var judgmentId = UUID.randomUUID();
        var now = Instant.now();

        persistJudgmentEvent(channelId, judgmentId, JudgmentEventKinds.VERIFIED, "code_review", "ACCEPTED", null, now);
        persistJudgmentEvent(channelId, judgmentId, JudgmentEventKinds.YIELDED, "code_review", null, null, now.minus(2, ChronoUnit.MINUTES));

        var results = repo.findJudgmentEvents(null, judgmentId, null, null, null);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).toolName).isEqualTo(JudgmentEventKinds.YIELDED);
        assertThat(results.get(1).toolName).isEqualTo(JudgmentEventKinds.VERIFIED);
    }

    @Test
    @TestTransaction
    void countJudgmentOutcomesGroupsByTypeAndOutcome() {
        var channelId = UUID.randomUUID();
        var now = Instant.now();

        persistJudgmentEvent(channelId, UUID.randomUUID(), JudgmentEventKinds.VERIFIED, "code_review", "ACCEPTED", null, now.minus(1, ChronoUnit.HOURS));
        persistJudgmentEvent(channelId, UUID.randomUUID(), JudgmentEventKinds.VERIFIED, "code_review", "ACCEPTED", null, now.minus(30, ChronoUnit.MINUTES));
        persistJudgmentEvent(channelId, UUID.randomUUID(), JudgmentEventKinds.VERIFIED, "code_review", "REJECTED", null, now.minus(20, ChronoUnit.MINUTES));
        persistJudgmentEvent(channelId, UUID.randomUUID(), JudgmentEventKinds.VERIFIED, "quality_check", "ACCEPTED", null, now.minus(10, ChronoUnit.MINUTES));
        persistJudgmentEvent(channelId, UUID.randomUUID(), JudgmentEventKinds.ESCALATED, "code_review", null, null, now.minus(5, ChronoUnit.MINUTES));

        var results = repo.countJudgmentOutcomes(now.minus(2, ChronoUnit.HOURS), now, null);

        assertThat(results).hasSize(4);
    }

    @Test
    @TestTransaction
    void findPendingJudgmentsReturnsYieldedWithNoTerminal() {
        var channelId = UUID.randomUUID();
        var resolvedId = UUID.randomUUID();
        var pendingId = UUID.randomUUID();

        persistJudgmentEvent(channelId, resolvedId, JudgmentEventKinds.YIELDED, "code_review", null, null, Instant.now().minus(1, ChronoUnit.HOURS));
        persistJudgmentEvent(channelId, resolvedId, JudgmentEventKinds.VERIFIED, "code_review", "ACCEPTED", null, Instant.now());
        persistJudgmentEvent(channelId, pendingId, JudgmentEventKinds.YIELDED, "code_review", null, null, Instant.now().minus(30, ChronoUnit.MINUTES));

        var results = repo.findPendingJudgments(null);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().judgmentId).isEqualTo(pendingId);
    }

    @Test
    @TestTransaction
    void findPendingJudgmentsExcludesEscalated() {
        var channelId = UUID.randomUUID();
        var escalatedId = UUID.randomUUID();

        persistJudgmentEvent(channelId, escalatedId, JudgmentEventKinds.YIELDED, "code_review", null, null, Instant.now());
        persistJudgmentEvent(channelId, escalatedId, JudgmentEventKinds.ESCALATED, "code_review", null, null, Instant.now());

        var results = repo.findPendingJudgments(null);

        assertThat(results).isEmpty();
    }

    private void persistJudgmentEvent(UUID channelId, UUID judgmentId, String toolName,
            String judgmentType, String verificationOutcome, Double evidenceQuality, Instant occurredAt) {
        var e = new MessageLedgerEntry();
        e.subjectId = channelId;
        e.channelId = channelId;
        e.messageId = System.nanoTime();
        e.messageType = "EVENT";
        e.toolName = toolName;
        e.judgmentId = judgmentId;
        e.judgmentType = judgmentType;
        e.verificationOutcome = verificationOutcome;
        e.evidenceQuality = evidenceQuality;
        e.sequenceNumber = 1;
        e.entryType = LedgerEntryType.EVENT;
        e.actorId = "test-actor";
        e.actorType = ActorType.AGENT;
        e.actorRole = "test-role";
        e.occurredAt = occurredAt;
        e.tenancyId = TenancyConstants.DEFAULT_TENANT_ID;
        ledger.save(e, null);
    }
}
