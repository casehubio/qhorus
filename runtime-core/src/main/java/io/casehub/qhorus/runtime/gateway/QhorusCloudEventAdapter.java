package io.casehub.qhorus.runtime.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.qhorus.api.gateway.MessageReceivedEvent;
import io.cloudevents.CloudEvent;
import org.jboss.logging.Logger;

import java.util.function.Consumer;

public class QhorusCloudEventAdapter {

    private static final Logger LOG = Logger.getLogger(QhorusCloudEventAdapter.class);

    Consumer<CloudEvent> cloudEventConsumer;
    ObjectMapper objectMapper;

    public QhorusCloudEventAdapter(Consumer<CloudEvent> cloudEventConsumer, ObjectMapper objectMapper) {
        this.cloudEventConsumer = cloudEventConsumer;
        this.objectMapper = objectMapper;
    }

    public void onMessageReceived(MessageReceivedEvent event) {
        try {
            cloudEventConsumer.accept(toCloudEvent(event));
        } catch (Exception ex) {
            LOG.warnf(ex, "CloudEvent dispatch failed for channel=%s type=%s",
                    event.channelId(), event.messageType());
        }
    }

    private CloudEvent toCloudEvent(MessageReceivedEvent event) {
        return CloudEventMapper.toCloudEvent(event, objectMapper);
    }
}
