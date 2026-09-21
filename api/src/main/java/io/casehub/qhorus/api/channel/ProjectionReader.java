package io.casehub.qhorus.api.channel;

import java.util.List;
import java.util.UUID;

public interface ProjectionReader {

    List<String> registeredNames();

    String project(UUID channelId, String projectionName, Integer maxMessages, String topic);
}
