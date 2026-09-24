package io.casehub.qhorus.runtime.cdi;

import io.casehub.qhorus.api.gateway.CommitmentStateChangedEvent;
import io.casehub.qhorus.api.message.CommitmentDeclinedEvent;
import io.casehub.qhorus.api.message.CommitmentExpiredEvent;
import io.casehub.qhorus.api.store.CommitmentStore;
import io.casehub.qhorus.runtime.config.QhorusTracingConfig;
import io.casehub.qhorus.runtime.message.CommitmentService;
import io.opentelemetry.api.trace.Tracer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
@Transactional
public class CdiCommitmentService extends CommitmentService {

    @Inject
    public CdiCommitmentService(CommitmentStore store,
                                Event<CommitmentDeclinedEvent> declinedEvents,
                                Event<CommitmentExpiredEvent> expiredEvents,
                                Event<CommitmentStateChangedEvent> stateChangedEvents,
                                Instance<Tracer> tracerInstance,
                                QhorusTracingConfig tracingConfig) {
        super(store, declinedEvents::fire, e -> {
            try { expiredEvents.fire(e); } catch (Exception ex) { /* logged by core */ }
        }, stateChangedEvents::fire,
                tracerInstance.isResolvable() ? tracerInstance::get : null, tracingConfig);
    }

    CdiCommitmentService() {}
}
