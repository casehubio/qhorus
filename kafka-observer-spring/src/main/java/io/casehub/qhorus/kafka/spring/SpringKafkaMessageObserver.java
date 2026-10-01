package io.casehub.qhorus.kafka.spring;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.qhorus.api.gateway.MessageObserver;
import io.casehub.qhorus.api.gateway.MessageReceivedEvent;
import io.casehub.qhorus.runtime.gateway.CloudEventMapper;
import io.cloudevents.CloudEvent;
import io.cloudevents.jackson.JsonFormat;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Set;

public class SpringKafkaMessageObserver implements MessageObserver {

    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, byte[]> kafkaTemplate;
    private final String topic;
    private final Set<String> channelFilter;

    public SpringKafkaMessageObserver(ObjectMapper objectMapper,
                                       KafkaTemplate<String, byte[]> kafkaTemplate,
                                       String topic, Set<String> channelFilter) {
        this.objectMapper = objectMapper;
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.channelFilter = channelFilter;
    }

    @Override
    public void onMessage(MessageReceivedEvent event) {
        CloudEvent ce = CloudEventMapper.toCloudEvent(event, objectMapper);
        byte[] serialized = new JsonFormat().serialize(ce);
        kafkaTemplate.send(topic, event.channelId().toString(), serialized);
    }

    @Override
    public Scope scope() {
        return Scope.LOCAL;
    }

    @Override
    public Set<String> channels() {
        return channelFilter;
    }
}
