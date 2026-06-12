package io.casehub.qhorus.message;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.casehub.qhorus.api.message.CommitmentDeclinedEvent;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.runtime.message.CommitmentService;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Verifies that {@link CommitmentService#decline()} fires {@link CommitmentDeclinedEvent}.
 * Uses unique correlation IDs per test — no store clearing needed.
 * Refs #251.
 */
@QuarkusTest
class CommitmentDeclinedEventTest {

    @Inject CommitmentService commitmentService;
    @Inject EventCapture capture;

    @Test
    void decline_firesCommitmentDeclinedEvent() {
        final String correlationId = "corr-declined-" + UUID.randomUUID();
        final UUID commitmentId = UUID.randomUUID();
        final UUID channelId = UUID.randomUUID();

        QuarkusTransaction.requiringNew().run(() ->
                commitmentService.open(commitmentId, correlationId, channelId,
                        MessageType.COMMAND, "requester-a", "obligor-b",
                        Instant.now().plusSeconds(60)));

        QuarkusTransaction.requiringNew().run(() ->
                commitmentService.decline(correlationId));

        final List<CommitmentDeclinedEvent> forCorr = capture.events().stream()
                .filter(e -> correlationId.equals(e.correlationId()))
                .toList();

        assertThat(forCorr)
                .hasSize(1)
                .first()
                .satisfies(e -> {
                    assertThat(e.commitmentId()).isEqualTo(commitmentId);
                    assertThat(e.channelId()).isEqualTo(channelId);
                    assertThat(e.obligor()).isEqualTo("obligor-b");
                    assertThat(e.requester()).isEqualTo("requester-a");
                });
    }

    @Test
    void decline_onAlreadyDeclined_doesNotFireAgain() {
        final String correlationId = "corr-declined-idem-" + UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() ->
                commitmentService.open(UUID.randomUUID(), correlationId, UUID.randomUUID(),
                        MessageType.COMMAND, "req", "obl", null));

        QuarkusTransaction.requiringNew().run(() -> commitmentService.decline(correlationId));
        QuarkusTransaction.requiringNew().run(() -> commitmentService.decline(correlationId));

        final long count = capture.events().stream()
                .filter(e -> correlationId.equals(e.correlationId())).count();
        assertThat(count).isEqualTo(1); // idempotent — fires once only
    }

    @Test
    void decline_onNonExistentCorrelationId_doesNotFire() {
        final String correlationId = "no-such-" + UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() ->
                commitmentService.decline(correlationId));

        assertThat(capture.events().stream()
                .filter(e -> correlationId.equals(e.correlationId())).count()).isZero();
    }

    @ApplicationScoped
    public static class EventCapture {
        private final List<CommitmentDeclinedEvent> captured = new CopyOnWriteArrayList<>();

        void onEvent(@Observes CommitmentDeclinedEvent event) { captured.add(event); }
        List<CommitmentDeclinedEvent> events() { return List.copyOf(captured); }
    }
}
