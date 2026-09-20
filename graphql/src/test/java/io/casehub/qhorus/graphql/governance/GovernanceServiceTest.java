package io.casehub.qhorus.graphql.governance;

import io.casehub.qhorus.api.message.Commitment;
import io.casehub.qhorus.api.message.CommitmentPage;
import io.casehub.qhorus.api.message.CommitmentQuery;
import io.casehub.qhorus.api.message.CommitmentState;
import io.casehub.qhorus.api.store.CommitmentReader;
import io.casehub.qhorus.api.store.WatchdogStore;
import io.casehub.qhorus.api.store.query.WatchdogQuery;
import io.casehub.qhorus.api.watchdog.Watchdog;
import io.casehub.qhorus.api.watchdog.WatchdogConditionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GovernanceServiceTest {

    private GovernanceService service;
    private CommitmentReader commitmentReader;
    private WatchdogStore watchdogStore;

    @BeforeEach
    void setUp() {
        commitmentReader = mock(CommitmentReader.class);
        watchdogStore = mock(WatchdogStore.class);
        service = new GovernanceService(commitmentReader, watchdogStore);
    }

    @Test
    void commitmentsReturnsOpenByDefault() {
        Commitment c = createCommitment(CommitmentState.OPEN);
        when(commitmentReader.findAllOpen()).thenReturn(List.of(c));

        CommitmentPage result = service.commitments(null);

        assertThat(result.items()).hasSize(1);
        assertThat(result.hasNext()).isFalse();
    }

    @Test
    void commitmentsFiltersByChannelAndState() {
        UUID channelId = UUID.randomUUID();
        Commitment c = createCommitment(CommitmentState.FULFILLED);
        when(commitmentReader.findByState(CommitmentState.FULFILLED, channelId)).thenReturn(List.of(c));

        var query = new CommitmentQuery(channelId, CommitmentState.FULFILLED, null, null, 0, 10, null);
        CommitmentPage result = service.commitments(query);

        assertThat(result.items()).hasSize(1);
    }

    @Test
    void commitmentsFiltersByObligor() {
        Commitment c = createCommitment(CommitmentState.OPEN);
        when(commitmentReader.findOpenByObligor("agent-1")).thenReturn(List.of(c));

        var query = new CommitmentQuery(null, null, "agent-1", null, null, null, null);
        CommitmentPage result = service.commitments(query);

        assertThat(result.items()).hasSize(1);
    }

    @Test
    void commitmentsAppliesPagination() {
        List<Commitment> many = List.of(
                createCommitment(CommitmentState.OPEN),
                createCommitment(CommitmentState.OPEN),
                createCommitment(CommitmentState.OPEN));
        when(commitmentReader.findAllOpen()).thenReturn(many);

        var query = new CommitmentQuery(null, null, null, null, 0, 2, null);
        CommitmentPage result = service.commitments(query);

        assertThat(result.items()).hasSize(2);
        assertThat(result.hasNext()).isTrue();
    }

    @Test
    void pendingCommitmentsReturnsAllOpen() {
        var c = createCommitment(CommitmentState.OPEN);
        when(commitmentReader.findAllOpen()).thenReturn(List.of(c));

        var result = service.pendingCommitments();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).state()).isEqualTo(CommitmentState.OPEN);
    }

    @Test
    void myCommitmentsFiltersByObligorRole() {
        UUID channelId = UUID.randomUUID();
        var  c         = createCommitment(CommitmentState.OPEN);
        when(commitmentReader.findOpenByObligor("agent-1", channelId)).thenReturn(List.of(c));

        var result = service.myCommitments(channelId, "agent-1", "obligor");

        assertThat(result).hasSize(1);
        verify(commitmentReader).findOpenByObligor("agent-1", channelId);
        verify(commitmentReader, never()).findOpenByRequester(any(), any());
    }

    @Test
    void myCommitmentsBothRoleMergesAndSorts() {
        UUID channelId = UUID.randomUUID();
        var  c1        = createCommitment(CommitmentState.OPEN);
        var  c2        = createCommitment(CommitmentState.OPEN);
        when(commitmentReader.findOpenByObligor("agent-1", channelId)).thenReturn(List.of(c1));
        when(commitmentReader.findOpenByRequester("agent-1", channelId)).thenReturn(List.of(c2));

        var result = service.myCommitments(channelId, "agent-1", null);

        assertThat(result).hasSize(2);
    }

    @Test
    void commitmentByCorrelationIdReturns() {
        var c = createCommitment(CommitmentState.FULFILLED);
        when(commitmentReader.findByCorrelationId("corr-1")).thenReturn(Optional.of(c));

        var result = service.commitment("corr-1");

        assertThat(result.state()).isEqualTo(CommitmentState.FULFILLED);
    }

    @Test
    void commitmentByCorrelationIdThrowsWhenNotFound() {
        when(commitmentReader.findByCorrelationId("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.commitment("missing"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void watchdogsReturnsAll() {
        var w = Watchdog.builder(WatchdogConditionType.BARRIER_STUCK, "ch-1")
                        .id(UUID.randomUUID()).notificationChannel("alerts")
                        .createdBy("admin").build();
        when(watchdogStore.scan(any(WatchdogQuery.class))).thenReturn(List.of(w));

        var result = service.watchdogs();

        assertThat(result).hasSize(1);
    }

    @Test
    void registerWatchdogDelegates() {
        var w = Watchdog.builder(WatchdogConditionType.AGENT_STALE, "*")
                        .id(UUID.randomUUID()).build();
        when(watchdogStore.put(any(Watchdog.class))).thenReturn(w);

        var result = service.registerWatchdog("AGENT_STALE", "*", 300, null,
                                              null, "alerts", "admin", null);

        assertThat(result).isNotNull();
        verify(watchdogStore).put(any(Watchdog.class));
    }

    @Test
    void deleteWatchdogReturnsTrueWhenFound() {
        UUID id = UUID.randomUUID();
        when(watchdogStore.find(id)).thenReturn(Optional.of(
                Watchdog.builder(WatchdogConditionType.BARRIER_STUCK, "*").id(id).build()));

        assertThat(service.deleteWatchdog(id)).isTrue();
        verify(watchdogStore).delete(id);
    }

    @Test
    void deleteWatchdogReturnsFalseWhenNotFound() {
        UUID id = UUID.randomUUID();
        when(watchdogStore.find(id)).thenReturn(Optional.empty());

        assertThat(service.deleteWatchdog(id)).isFalse();
        verify(watchdogStore, never()).delete(any());
    }


    private Commitment createCommitment(CommitmentState state) {
        return Commitment.builder()
                .id(UUID.randomUUID())
                .channelId(UUID.randomUUID())
                .requester("requester")
                .obligor("obligor")
                .state(state)
                .createdAt(Instant.now())
                .build();
    }
}
