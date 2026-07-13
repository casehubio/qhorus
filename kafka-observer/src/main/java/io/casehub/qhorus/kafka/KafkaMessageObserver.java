package io.casehub.qhorus.kafka;

import java.util.Set;
import java.util.function.Consumer;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.casehub.qhorus.api.gateway.MessageObserver;
import io.casehub.qhorus.api.gateway.MessageReceivedEvent;
import io.casehub.qhorus.runtime.gateway.CloudEventMapper;
import io.cloudevents.CloudEvent;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;

@ApplicationScoped
public class KafkaMessageObserver implements MessageObserver {

    private final ObjectMapper objectMapper;
    private final Consumer<CloudEventRecord> sender;
    private final Set<String> channelFilter;

    record CloudEventRecord(CloudEvent cloudEvent, String key) {}

    @Inject
    public KafkaMessageObserver(ObjectMapper objectMapper,
                                 @Channel("qhorus-messages") Emitter<CloudEvent> emitter,
                                 KafkaObserverConfig config) {
        this.objectMapper = objectMapper;
        this.sender = record -> {
            OutgoingKafkaRecordMetadata<String> metadata = OutgoingKafkaRecordMetadata.<String>builder()
                    .withKey(record.key())
                    .build();
            emitter.send(Message.of(record.cloudEvent()).addMetadata(metadata));
        };
        this.channelFilter = config.channels().orElse(Set.of());
    }

    KafkaMessageObserver(ObjectMapper objectMapper,
                          Consumer<CloudEventRecord> sender,
                          Set<String> channelFilter) {
        this.objectMapper = objectMapper;
        this.sender = sender;
        this.channelFilter = channelFilter;
    }

    @Override
    public void onMessage(MessageReceivedEvent event) {
        CloudEvent ce = CloudEventMapper.toCloudEvent(event, objectMapper);
        sender.accept(new CloudEventRecord(ce, event.channelId().toString()));
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
