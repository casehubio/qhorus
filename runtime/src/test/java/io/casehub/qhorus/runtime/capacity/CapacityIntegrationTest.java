package io.casehub.qhorus.runtime.capacity;

import io.casehub.platform.api.capacity.CapacitySignalTypes;
import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.channel.ChannelCreateRequest;
import io.casehub.qhorus.api.channel.ChannelManager;
import io.casehub.qhorus.api.message.Commitment;
import io.casehub.qhorus.api.message.CommitmentState;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.store.ChannelStore;
import io.casehub.qhorus.api.store.CommitmentStore;
import io.casehub.qhorus.api.store.CrossTenantCommitmentStore;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@QuarkusTest
class CapacityIntegrationTest {

    @Inject CommitmentCountCapacitySource commitmentCountSource;
    @Inject CrossTenantCommitmentStore crossTenantCommitmentStore;
    @Inject CommitmentStore commitmentStore;
    @Inject ChannelManager channelManager;
    @Inject ChannelStore channelStore;
    @Inject io.casehub.qhorus.runtime.channel.ChannelService channelService;

    @Test
    void commitmentCountSourceReflectsRealCommitments() {
        String obligor = "agent-capacity-" + UUID.randomUUID();

        QuarkusTransaction.requiringNew().run(() -> {
            var ch = channelManager.create(ChannelCreateRequest.builder("cap-test-" + UUID.randomUUID()).build());
            for (int i = 0; i < 3; i++) {
                commitmentStore.save(Commitment.builder()
                        .id(UUID.randomUUID())
                        .correlationId(UUID.randomUUID().toString())
                        .channelId(ch.id())
                        .messageType(MessageType.COMMAND)
                        .requester("requester-1")
                        .obligor(obligor)
                        .state(CommitmentState.OPEN)
                        .tenancyId("278776f9-e1b0-46fb-9032-8bddebdcf9ce")
                        .build());
            }
        });

        var signals = commitmentCountSource.observe(obligor);
        assertThat(signals).hasSize(1);
        assertThat(signals.getFirst().pressure()).isCloseTo(0.15, within(0.01));
        assertThat(signals.getFirst().signalType()).isEqualTo(CapacitySignalTypes.TASK_COUNT);
    }

    @Test
    void commitmentCountSourceOverloadedQueryWorks() {
        String obligor = "agent-overloaded-" + UUID.randomUUID();

        QuarkusTransaction.requiringNew().run(() -> {
            var ch = channelManager.create(ChannelCreateRequest.builder("cap-overload-" + UUID.randomUUID()).build());
            for (int i = 0; i < 15; i++) {
                commitmentStore.save(Commitment.builder()
                        .id(UUID.randomUUID())
                        .correlationId(UUID.randomUUID().toString())
                        .channelId(ch.id())
                        .messageType(MessageType.COMMAND)
                        .requester("requester-1")
                        .obligor(obligor)
                        .state(CommitmentState.OPEN)
                        .tenancyId("278776f9-e1b0-46fb-9032-8bddebdcf9ce")
                        .build());
            }
        });

        var overloaded = commitmentCountSource.observeOverloaded(0.7);
        assertThat(overloaded.stream().filter(s -> s.actorId().equals(obligor)).toList())
                .hasSize(1);
        assertThat(overloaded.stream().filter(s -> s.actorId().equals(obligor))
                .findFirst().orElseThrow().pressure())
                .isCloseTo(0.75, within(0.01));
    }

    @Test
    void channelRedistributionThresholdPersistence() {
        Channel created = QuarkusTransaction.requiringNew().call(() ->
                channelManager.create(ChannelCreateRequest.builder("cap-threshold-" + UUID.randomUUID()).build()));

        assertThat(created.redistributionCapacityThreshold()).isNull();

        Channel updated = QuarkusTransaction.requiringNew().call(() ->
                channelService.setRedistributionCapacityThreshold(created.id(), 0.75));

        assertThat(updated.redistributionCapacityThreshold()).isEqualTo(0.75);
    }

    @Test
    void countOpenByObligorMatchesFindOpenByObligor() {
        String obligor = "agent-count-" + UUID.randomUUID();

        QuarkusTransaction.requiringNew().run(() -> {
            var ch = channelManager.create(ChannelCreateRequest.builder("cap-count-" + UUID.randomUUID()).build());
            for (int i = 0; i < 5; i++) {
                commitmentStore.save(Commitment.builder()
                        .id(UUID.randomUUID())
                        .correlationId(UUID.randomUUID().toString())
                        .channelId(ch.id())
                        .messageType(MessageType.COMMAND)
                        .requester("requester-1")
                        .obligor(obligor)
                        .state(CommitmentState.OPEN)
                        .tenancyId("278776f9-e1b0-46fb-9032-8bddebdcf9ce")
                        .build());
            }
        });

        long count = crossTenantCommitmentStore.countOpenByObligor(obligor);
        int listSize = crossTenantCommitmentStore.findOpenByObligor(obligor).size();
        assertThat(count).isEqualTo(listSize).isEqualTo(5);
    }
}
