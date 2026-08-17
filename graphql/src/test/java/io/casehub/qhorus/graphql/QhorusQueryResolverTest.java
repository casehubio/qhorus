package io.casehub.qhorus.graphql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.graphql.PageInput;
import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.channel.ChannelReader;
import io.casehub.qhorus.api.channel.ChannelSemantic;
import io.casehub.qhorus.api.message.Commitment;
import io.casehub.qhorus.api.message.CommitmentState;
import io.casehub.qhorus.api.message.ConsumerMessaging;
import io.casehub.qhorus.api.message.Message;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.store.CommitmentReader;
import io.casehub.qhorus.api.store.query.ChannelQuery;
import io.casehub.qhorus.graphql.dto.ChannelFilterInput;
import io.casehub.qhorus.graphql.dto.CommitmentFilterInput;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

class QhorusQueryResolverTest {

    private QhorusQueryResolver resolver;
    private ChannelReader channelReader;
    private ConsumerMessaging consumerMessaging;
    private CommitmentReader commitmentReader;
    private CurrentPrincipal currentPrincipal;

    @BeforeEach
    void setUp() {
        resolver = new QhorusQueryResolver();
        channelReader = mock(ChannelReader.class);
        consumerMessaging = mock(ConsumerMessaging.class);
        commitmentReader = mock(CommitmentReader.class);
        currentPrincipal = mock(CurrentPrincipal.class);

        resolver.channelReader = channelReader;
        resolver.consumerMessaging = consumerMessaging;
        resolver.commitmentReader = commitmentReader;
        resolver.currentPrincipal = currentPrincipal;

        when(currentPrincipal.tenancyId()).thenReturn("test-tenant");
        when(currentPrincipal.actorId()).thenReturn("test-actor");
    }

    @Test
    void channelsReturnsPaginatedResults() {
        Channel ch = createChannel("test-channel");
        when(channelReader.scan(ArgumentMatchers.any(ChannelQuery.class))).thenReturn(List.of(ch));

        var result = resolver.channels(null, new PageInput(0, 10, null));

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).name()).isEqualTo("test-channel");
        assertThat(result.pageInfo().totalCount()).isEqualTo(1);
        assertThat(result.pageInfo().hasNext()).isFalse();
    }

    @Test
    void channelsAppliesPagination() {
        Channel ch1 = createChannel("ch-1");
        Channel ch2 = createChannel("ch-2");
        Channel ch3 = createChannel("ch-3");
        when(channelReader.scan(ArgumentMatchers.any(ChannelQuery.class)))
                .thenReturn(List.of(ch1, ch2, ch3));

        var result = resolver.channels(null, new PageInput(0, 2, null));

        assertThat(result.items()).hasSize(2);
        assertThat(result.pageInfo().hasNext()).isTrue();
        assertThat(result.pageInfo().hasPrevious()).isFalse();
        assertThat(result.pageInfo().totalCount()).isEqualTo(3);
    }

    @Test
    void channelsDefaultsPagination() {
        when(channelReader.scan(ArgumentMatchers.any(ChannelQuery.class))).thenReturn(List.of());

        var result = resolver.channels(null, null);

        assertThat(result.items()).isEmpty();
        assertThat(result.pageInfo().totalCount()).isEqualTo(0);
    }

    @Test
    void channelByIdReturnsChannel() {
        UUID id = UUID.randomUUID();
        Channel ch = createChannel("found");
        when(channelReader.findById(id)).thenReturn(Optional.of(ch));

        var result = resolver.channel(id, null);

        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo("found");
    }

    @Test
    void channelByNameReturnsChannel() {
        Channel ch = createChannel("by-name");
        when(channelReader.findByName("by-name")).thenReturn(Optional.of(ch));

        var result = resolver.channel(null, "by-name");

        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo("by-name");
    }

    @Test
    void channelReturnsNullWhenNotFound() {
        UUID id = UUID.randomUUID();
        when(channelReader.findById(id)).thenReturn(Optional.empty());

        var result = resolver.channel(id, null);

        assertThat(result).isNull();
    }

    @Test
    void channelThrowsWhenNeitherIdNorName() {
        assertThatThrownBy(() -> resolver.channel(null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void channelMessagesReturnsHistory() {
        UUID channelId = UUID.randomUUID();
        Message msg = createMessage(channelId, 1L);
        when(consumerMessaging.history(channelId, 0L, 50)).thenReturn(List.of(msg));

        var result = resolver.channelMessages(channelId, null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(1L);
    }

    @Test
    void channelMessagesUsesProvidedCursor() {
        UUID channelId = UUID.randomUUID();
        when(consumerMessaging.history(channelId, 42L, 10)).thenReturn(List.of());

        var result = resolver.channelMessages(channelId, 42L, 10);

        assertThat(result).isEmpty();
    }

    @Test
    void commitmentsReturnsOpenByDefault() {
        Commitment c = createCommitment(CommitmentState.OPEN);
        when(commitmentReader.findAllOpen()).thenReturn(List.of(c));

        var result = resolver.commitments(null, new PageInput(0, 10, null));

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).state()).isEqualTo(CommitmentState.OPEN);
    }

    @Test
    void commitmentsFiltersByChannelAndState() {
        UUID channelId = UUID.randomUUID();
        Commitment c = createCommitment(CommitmentState.FULFILLED);
        when(commitmentReader.findByState(CommitmentState.FULFILLED, channelId))
                .thenReturn(List.of(c));

        var filter = new CommitmentFilterInput(channelId, CommitmentState.FULFILLED, null, null);
        var result = resolver.commitments(filter, new PageInput(0, 10, null));

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).state()).isEqualTo(CommitmentState.FULFILLED);
    }

    @Test
    void commitmentsFiltersByObligor() {
        UUID channelId = UUID.randomUUID();
        Commitment c = createCommitment(CommitmentState.OPEN);
        when(commitmentReader.findOpenByObligor("agent-1", channelId)).thenReturn(List.of(c));

        var filter = new CommitmentFilterInput(channelId, null, "agent-1", null);
        var result = resolver.commitments(filter, new PageInput(0, 10, null));

        assertThat(result.items()).hasSize(1);
    }

    private Channel createChannel(String name) {
        return Channel.builder(name)
                .id(UUID.randomUUID())
                .semantic(ChannelSemantic.APPEND)
                .tenancyId("test-tenant")
                .createdAt(Instant.now())
                .lastActivityAt(Instant.now())
                .build();
    }

    private Message createMessage(UUID channelId, Long id) {
        return Message.builder()
                .id(id)
                .channelId(channelId)
                .sender("test-actor")
                .messageType(MessageType.STATUS)
                .content("test content")
                .createdAt(Instant.now())
                .build();
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
