package io.casehub.qhorus.api.channel;

import io.casehub.qhorus.api.spi.ChannelProjection;
import io.casehub.qhorus.api.spi.ProjectionResult;

import java.util.UUID;

/**
 * Consumer-facing facade for evaluating channel projections.
 *
 * <p>Follows the same pattern as {@link ChannelManager} and
 * {@link io.casehub.qhorus.api.message.MessageDispatcher} — an API-level
 * service contract with a runtime implementation.
 */
public interface ChannelProjector {

    /**
     * Full-scan projection: folds all messages in the channel from the beginning.
     */
    <S> ProjectionResult<S> project(UUID channelId, ChannelProjection<S> projection);

    /**
     * Incremental projection: resumes folding from the cursor in {@code previous}.
     * Only processes messages after {@code previous.lastMessageId()}.
     */
    <S> ProjectionResult<S> project(UUID channelId, ChannelProjection<S> projection,
                                     ProjectionResult<S> previous);
}
