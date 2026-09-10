package io.casehub.qhorus.graphql.governance;

import io.casehub.platform.graphql.PageInput;
import io.casehub.qhorus.api.message.Commitment;
import io.casehub.qhorus.api.message.CommitmentState;
import io.casehub.qhorus.api.store.CommitmentReader;
import io.casehub.qhorus.graphql.dto.CommitmentFilterInput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GovernanceQueryResolverTest {

    private GovernanceQueryResolver resolver;
    private CommitmentReader commitmentReader;

    @BeforeEach
    void setUp() {
        commitmentReader = mock(CommitmentReader.class);
        resolver = new GovernanceQueryResolver();
        resolver.commitmentReader = commitmentReader;
    }

    @Test
    void commitmentsReturnsOpenByDefault() {
        Commitment c = createCommitment(CommitmentState.OPEN);
        when(commitmentReader.findAllOpen()).thenReturn(List.of(c));

        var result = resolver.commitments(null, null);

        assertThat(result.items()).hasSize(1);
        assertThat(result.pageInfo().totalCount()).isEqualTo(1);
    }

    @Test
    void commitmentsFiltersByChannelAndState() {
        UUID channelId = UUID.randomUUID();
        Commitment c = createCommitment(CommitmentState.FULFILLED);
        when(commitmentReader.findByState(CommitmentState.FULFILLED, channelId)).thenReturn(List.of(c));

        var filter = new CommitmentFilterInput(channelId, CommitmentState.FULFILLED, null, null);
        var result = resolver.commitments(filter, new PageInput(0, 10, null));

        assertThat(result.items()).hasSize(1);
    }

    @Test
    void commitmentsFiltersByObligor() {
        Commitment c = createCommitment(CommitmentState.OPEN);
        when(commitmentReader.findOpenByObligor("agent-1")).thenReturn(List.of(c));

        var filter = new CommitmentFilterInput(null, null, "agent-1", null);
        var result = resolver.commitments(filter, null);

        assertThat(result.items()).hasSize(1);
    }

    @Test
    void commitmentsAppliesPagination() {
        List<Commitment> many = List.of(
                createCommitment(CommitmentState.OPEN),
                createCommitment(CommitmentState.OPEN),
                createCommitment(CommitmentState.OPEN));
        when(commitmentReader.findAllOpen()).thenReturn(many);

        var result = resolver.commitments(null, new PageInput(0, 2, null));

        assertThat(result.items()).hasSize(2);
        assertThat(result.pageInfo().hasNext()).isTrue();
        assertThat(result.pageInfo().totalCount()).isEqualTo(3);
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
