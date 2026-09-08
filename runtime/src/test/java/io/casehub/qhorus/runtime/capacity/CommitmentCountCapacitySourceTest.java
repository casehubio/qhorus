package io.casehub.qhorus.runtime.capacity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.casehub.platform.api.capacity.CapacitySignalTypes;
import io.casehub.qhorus.api.store.CrossTenantCommitmentStore;

class CommitmentCountCapacitySourceTest {

    @Test
    void observeReturnsPressureFromOpenCommitments() {
        var store = mock(CrossTenantCommitmentStore.class);
        when(store.countOpenByObligor("agent-1")).thenReturn(3L);

        var source = new CommitmentCountCapacitySource(store, 20);

        var signals = source.observe("agent-1");
        assertThat(signals).hasSize(1);
        assertThat(signals.getFirst().pressure()).isCloseTo(0.15, within(0.001));
        assertThat(signals.getFirst().signalType()).isEqualTo(CapacitySignalTypes.TASK_COUNT);
        assertThat(signals.getFirst().actorId()).isEqualTo("agent-1");
    }

    @Test
    void observeClampsPressureToOne() {
        var store = mock(CrossTenantCommitmentStore.class);
        when(store.countOpenByObligor("agent-1")).thenReturn(25L);

        var source = new CommitmentCountCapacitySource(store, 20);

        var signals = source.observe("agent-1");
        assertThat(signals.getFirst().pressure()).isCloseTo(1.0, within(0.001));
    }

    @Test
    void observeReturnsZeroForNoCommitments() {
        var store = mock(CrossTenantCommitmentStore.class);
        when(store.countOpenByObligor("agent-1")).thenReturn(0L);

        var source = new CommitmentCountCapacitySource(store, 20);

        var signals = source.observe("agent-1");
        assertThat(signals).hasSize(1);
        assertThat(signals.getFirst().pressure()).isCloseTo(0.0, within(0.001));
    }

    @Test
    void observeOverloadedUsesEfficientQuery() {
        var store = mock(CrossTenantCommitmentStore.class);
        when(store.findObligorsExceedingCount(16))
                .thenReturn(Map.of("agent-1", 18L, "agent-2", 20L));

        var source = new CommitmentCountCapacitySource(store, 20);

        var overloaded = source.observeOverloaded(0.8);
        assertThat(overloaded).hasSize(2);
        assertThat(overloaded.stream().map(s -> s.actorId()).toList())
                .containsExactlyInAnyOrder("agent-1", "agent-2");
    }

    @Test
    void observeOverloadedComputesCorrectMinCount() {
        var store = mock(CrossTenantCommitmentStore.class);
        when(store.findObligorsExceedingCount(14)).thenReturn(Map.of("agent-1", 15L));

        var source = new CommitmentCountCapacitySource(store, 20);

        var overloaded = source.observeOverloaded(0.7);
        assertThat(overloaded).hasSize(1);
        assertThat(overloaded.getFirst().pressure()).isCloseTo(0.75, within(0.001));
    }
}
