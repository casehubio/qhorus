package io.casehub.qhorus.persistence.memory;

import io.casehub.qhorus.api.channel.ThreadSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryThreadSummaryStoreTest {

    private InMemoryThreadSummaryStore store;

    private static final UUID CHANNEL_A = UUID.randomUUID();
    private static final UUID CHANNEL_B = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        store = new InMemoryThreadSummaryStore();
    }

    @Test
    void saveAndFindRoundTrip() {
        ThreadSummary summary = ThreadSummary.builder(CHANNEL_A, "corr-1")
                .content("Thread summary text")
                .annotations(Map.of("participants", "agent-a, agent-b"))
                .updatedAt(Instant.now())
                .updatedBy("system:thread-summariser")
                .tenancyId("tenant-1")
                .build();

        ThreadSummary saved = store.save(summary);
        assertThat(saved.id()).isNotNull();
        assertThat(saved.content()).isEqualTo("Thread summary text");

        var found = store.findByCorrelationId(CHANNEL_A, "corr-1");
        assertThat(found).isPresent();
        assertThat(found.get().content()).isEqualTo("Thread summary text");
        assertThat(found.get().annotations()).containsEntry("participants", "agent-a, agent-b");
    }

    @Test
    void upsertOverwritesSameKey() {
        store.save(ThreadSummary.builder(CHANNEL_A, "corr-1")
                .content("First version").build());
        store.save(ThreadSummary.builder(CHANNEL_A, "corr-1")
                .content("Second version").build());

        var found = store.findByCorrelationId(CHANNEL_A, "corr-1");
        assertThat(found).isPresent();
        assertThat(found.get().content()).isEqualTo("Second version");
    }

    @Test
    void findByChannelReturnsAllThreads() {
        store.save(ThreadSummary.builder(CHANNEL_A, "corr-1").content("T1").build());
        store.save(ThreadSummary.builder(CHANNEL_A, "corr-2").content("T2").build());
        store.save(ThreadSummary.builder(CHANNEL_B, "corr-3").content("T3").build());

        var channelA = store.findByChannel(CHANNEL_A);
        assertThat(channelA).hasSize(2);
        assertThat(channelA).extracting(ThreadSummary::correlationId)
                .containsExactlyInAnyOrder("corr-1", "corr-2");
    }

    @Test
    void deleteRemovesEntry() {
        store.save(ThreadSummary.builder(CHANNEL_A, "corr-1").content("T1").build());
        store.delete(CHANNEL_A, "corr-1");

        assertThat(store.findByCorrelationId(CHANNEL_A, "corr-1")).isEmpty();
    }

    @Test
    void findUnknownReturnsEmpty() {
        assertThat(store.findByCorrelationId(CHANNEL_A, "nonexistent")).isEmpty();
    }

    @Test
    void saveGeneratesIdWhenNull() {
        ThreadSummary saved = store.save(
                ThreadSummary.builder(CHANNEL_A, "corr-1").content("T1").build());
        assertThat(saved.id()).isNotNull();
    }
}
