package io.casehub.qhorus.runtime.message;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

import io.casehub.qhorus.api.message.Message;
import io.casehub.qhorus.api.message.MessageView;
import io.casehub.qhorus.api.spi.ChannelProjection;
import io.casehub.qhorus.api.spi.ProjectionResult;
import io.casehub.qhorus.api.store.MessageStore;
import io.casehub.qhorus.api.store.query.MessageQuery;

public class ProjectionService {

    private final MessageStore messageStore;
    private final Function<Message, MessageView> messageViewMapper;

    public ProjectionService(MessageStore messageStore, Function<Message, MessageView> messageViewMapper) {
        this.messageStore = messageStore;
        this.messageViewMapper = messageViewMapper;
    }

    public <S> ProjectionResult<S> project(final UUID channelId,
                                            final ChannelProjection<S> projection) {
        Objects.requireNonNull(channelId, "channelId");
        Objects.requireNonNull(projection, "projection");
        return fold(MessageQuery.builder().channelId(channelId).build(),
                    projection.identity(), null, projection);
    }

    public <S> ProjectionResult<S> project(final UUID channelId,
                                            final MessageQuery scope,
                                            final ChannelProjection<S> projection) {
        Objects.requireNonNull(channelId, "channelId");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(projection, "projection");
        validateScope(channelId, scope);
        final MessageQuery query = scope.toBuilder().channelId(channelId).build();
        return fold(query, projection.identity(), null, projection);
    }

    public <S> ProjectionResult<S> project(final UUID channelId,
                                            final ProjectionResult<S> previous,
                                            final ChannelProjection<S> projection) {
        Objects.requireNonNull(channelId, "channelId");
        Objects.requireNonNull(previous, "previous");
        Objects.requireNonNull(projection, "projection");
        final S initialState = previous.isEmpty() ? projection.identity() : previous.state();
        final MessageQuery query = MessageQuery.builder()
                .channelId(channelId)
                .afterId(previous.lastMessageId())
                .build();
        return fold(query, initialState, previous.lastMessageId(), projection);
    }

    public <S> ProjectionResult<S> project(final UUID channelId,
                                            final ProjectionResult<S> previous,
                                            final MessageQuery scope,
                                            final ChannelProjection<S> projection) {
        Objects.requireNonNull(channelId, "channelId");
        Objects.requireNonNull(previous, "previous");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(projection, "projection");
        validateScope(channelId, scope);
        final S initialState = previous.isEmpty() ? projection.identity() : previous.state();
        final MessageQuery query = scope.toBuilder()
                .channelId(channelId)
                .afterId(previous.lastMessageId())
                .build();
        return fold(query, initialState, previous.lastMessageId(), projection);
    }

    private <S> ProjectionResult<S> fold(final MessageQuery query,
                                          final S initialState,
                                          final Long cursorIn,
                                          final ChannelProjection<S> projection) {
        final var messages = messageStore.scan(query);
        var state = initialState;
        var lastId = cursorIn;
        for (final var msg : messages) {
            state = projection.apply(state, messageViewMapper.apply(msg));
            lastId = msg.id();
        }
        return new ProjectionResult<>(state, lastId);
    }

    private static void validateScope(final UUID channelId, final MessageQuery scope) {
        if (scope.channelId() != null && !scope.channelId().equals(channelId)) {
            throw new IllegalArgumentException(
                    "scope.channelId() conflicts with channelId parameter — remove it from scope");
        }
        if (scope.descending()) {
            throw new IllegalArgumentException(
                    "scope.descending(true) breaks fold order — projections always fold ascending");
        }
    }
}
