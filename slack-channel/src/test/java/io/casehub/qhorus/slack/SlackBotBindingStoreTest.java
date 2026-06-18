package io.casehub.qhorus.slack;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class SlackBotBindingStoreTest {

    @Inject
    SlackBotBindingStore store;

    @Test
    @TestTransaction
    void save_and_findByChannelId_roundTrips() {
        UUID channelId = UUID.randomUUID();
        SlackBotBinding b = binding(channelId, "C123", "T_TEST");
        store.save(b);

        assertThat(store.findByChannelId(channelId)).isPresent().hasValueSatisfying(found -> {
            assertThat(found.slackChannelId).isEqualTo("C123");
            assertThat(found.workspaceId).isEqualTo("T_TEST");
        });
    }

    @Test
    @TestTransaction
    void findByChannelId_absent_returnsEmpty() {
        assertThat(store.findByChannelId(UUID.randomUUID())).isEmpty();
    }

    @Test
    @TestTransaction
    void deleteByChannelId_removesBinding() {
        UUID channelId = UUID.randomUUID();
        store.save(binding(channelId, "C456", "T_TEST"));
        store.deleteByChannelId(channelId);
        assertThat(store.findByChannelId(channelId)).isEmpty();
    }

    @Test
    @TestTransaction
    void save_replacesExistingBinding() {
        UUID channelId = UUID.randomUUID();
        store.save(binding(channelId, "C_OLD", "T_TEST"));
        store.save(binding(channelId, "C_NEW", "T_TEST"));
        assertThat(store.findByChannelId(channelId))
                .isPresent()
                .hasValueSatisfying(b -> assertThat(b.slackChannelId).isEqualTo("C_NEW"));
    }

    private SlackBotBinding binding(UUID channelId, String slackChannelId, String workspaceId) {
        SlackBotBinding b = new SlackBotBinding();
        b.channelId = channelId;
        b.slackChannelId = slackChannelId;
        b.workspaceId = workspaceId;
        b.createdAt = Instant.now();
        return b;
    }
}
