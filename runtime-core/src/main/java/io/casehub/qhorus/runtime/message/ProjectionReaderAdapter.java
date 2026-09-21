package io.casehub.qhorus.runtime.message;

import io.casehub.qhorus.api.channel.ProjectionReader;
import io.casehub.qhorus.api.spi.ProjectionResult;
import io.casehub.qhorus.api.spi.RenderableProjection;
import io.casehub.qhorus.api.store.query.MessageQuery;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ProjectionReaderAdapter implements ProjectionReader {

    private final ProjectionRegistry projectionRegistry;
    private final ProjectionService projectionService;

    public ProjectionReaderAdapter(ProjectionRegistry projectionRegistry,
                                    ProjectionService projectionService) {
        this.projectionRegistry = projectionRegistry;
        this.projectionService = projectionService;
    }

    @Override
    public List<String> registeredNames() {
        return new ArrayList<>(projectionRegistry.registeredNames()).stream().sorted().toList();
    }

    @Override
    public String project(UUID channelId, String projectionName, Integer maxMessages, String topic) {
        RenderableProjection<?> projection = projectionRegistry.get(projectionName);
        return projectAndRender(channelId, projection, maxMessages, topic);
    }

    private <S> String projectAndRender(UUID channelId, RenderableProjection<S> projection,
                                         Integer maxMessages, String topic) {
        String normalizedTopic = (topic != null && !topic.isBlank()) ? topic : null;
        boolean hasLimit = maxMessages != null && maxMessages > 0;
        if (normalizedTopic != null || hasLimit) {
            MessageQuery.Builder qb = MessageQuery.builder();
            if (normalizedTopic != null) { qb.topic(normalizedTopic); }
            if (hasLimit) { qb.limit(maxMessages); }
            ProjectionResult<S> result = projectionService.project(channelId, qb.build(), projection);
            return projection.render(result);
        }
        ProjectionResult<S> result = projectionService.project(channelId, projection);
        return projection.render(result);
    }
}
