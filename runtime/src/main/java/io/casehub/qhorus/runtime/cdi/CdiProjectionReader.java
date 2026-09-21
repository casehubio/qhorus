package io.casehub.qhorus.runtime.cdi;

import io.casehub.qhorus.api.channel.ProjectionReader;
import io.casehub.qhorus.runtime.message.ProjectionReaderAdapter;
import io.casehub.qhorus.runtime.message.ProjectionRegistry;
import io.casehub.qhorus.runtime.message.ProjectionService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class CdiProjectionReader implements ProjectionReader {

    @Inject
    ProjectionRegistry projectionRegistry;

    @Inject
    ProjectionService projectionService;

    private ProjectionReaderAdapter delegate() {
        return new ProjectionReaderAdapter(projectionRegistry, projectionService);
    }

    @Override
    public List<String> registeredNames() {
        return delegate().registeredNames();
    }

    @Override
    public String project(UUID channelId, String projectionName, Integer maxMessages, String topic) {
        return delegate().project(channelId, projectionName, maxMessages, topic);
    }
}
