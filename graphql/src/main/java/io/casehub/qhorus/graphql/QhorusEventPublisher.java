package io.casehub.qhorus.graphql;

import io.casehub.qhorus.api.channel.PresenceChangedEvent;
import io.casehub.qhorus.api.gateway.MessageReceivedEvent;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.subscription.BackPressureStrategy;
import io.smallrye.mutiny.subscription.MultiEmitter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@ApplicationScoped
public class QhorusEventPublisher {

    private final List<MultiEmitter<? super MessageReceivedEvent>> messageEmitters =
            new CopyOnWriteArrayList<>();
    private final List<MultiEmitter<? super PresenceChangedEvent>> presenceEmitters =
            new CopyOnWriteArrayList<>();

    void onMessageReceived(@ObservesAsync MessageReceivedEvent event) {
        for (var emitter : messageEmitters) {
            emitter.emit(event);
        }
    }

    void onPresenceChanged(@ObservesAsync PresenceChangedEvent event) {
        for (var emitter : presenceEmitters) {
            emitter.emit(event);
        }
    }

    public Multi<MessageReceivedEvent> messageStream() {
        return Multi.createFrom().<MessageReceivedEvent>emitter(emitter -> {
            messageEmitters.add(emitter);
            emitter.onTermination(() -> messageEmitters.remove(emitter));
        }, BackPressureStrategy.DROP);
    }

    public Multi<PresenceChangedEvent> presenceStream() {
        return Multi.createFrom().<PresenceChangedEvent>emitter(emitter -> {
            presenceEmitters.add(emitter);
            emitter.onTermination(() -> presenceEmitters.remove(emitter));
        }, BackPressureStrategy.DROP);
    }
}
