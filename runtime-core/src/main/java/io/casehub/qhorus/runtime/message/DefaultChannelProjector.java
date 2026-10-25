package io.casehub.qhorus.runtime.message;

import io.casehub.qhorus.api.channel.ChannelProjector;
import io.casehub.qhorus.api.spi.ChannelProjection;
import io.casehub.qhorus.api.spi.ProjectionResult;

import java.util.UUID;

/**
 * Default implementation of {@link ChannelProjector} delegating to {@link ProjectionService}.
 */
public class DefaultChannelProjector implements ChannelProjector {

    private final ProjectionService projectionService;

    public DefaultChannelProjector(ProjectionService projectionService) {
        this.projectionService = projectionService;
    }

    @Override
    public <S> ProjectionResult<S> project(UUID channelId, ChannelProjection<S> projection) {
        return projectionService.project(channelId, projection);
    }

    @Override
    public <S> ProjectionResult<S> project(UUID channelId, ChannelProjection<S> projection,
                                            ProjectionResult<S> previous) {
        return projectionService.project(channelId, previous, projection);
    }
}
