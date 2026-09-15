package io.casehub.qhorus.graphql.governance;

import io.casehub.qhorus.api.message.Commitment;
import io.casehub.qhorus.api.message.CommitmentPage;
import io.casehub.qhorus.api.message.CommitmentQuery;
import io.casehub.qhorus.api.message.CommitmentState;
import io.casehub.qhorus.api.store.CommitmentReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GovernanceServiceTest {

    private GovernanceService service;
    private CommitmentReader commitmentReader;

    @BeforeEach
    void setUp() {
        commitmentReader = mock(CommitmentReader.class);
        service = new GovernanceService(commitmentReader);
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
