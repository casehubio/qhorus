package io.casehub.qhorus.runtime.capacity;

import io.casehub.platform.api.capacity.CapacitySignal;
import io.casehub.platform.api.capacity.CapacitySignalSource;
import io.casehub.platform.api.capacity.CapacitySignalTypes;
import io.casehub.qhorus.api.store.CrossTenantCommitmentStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class CommitmentCountCapacitySource implements CapacitySignalSource {

    private final CrossTenantCommitmentStore commitmentStore;
    private final int maxObligations;

    @Inject
    public CommitmentCountCapacitySource(
            CrossTenantCommitmentStore commitmentStore,
            @ConfigProperty(name = "casehub.qhorus.capacity.max-obligations",
                            defaultValue = "20") int maxObligations) {
        this.commitmentStore = commitmentStore;
        this.maxObligations = maxObligations;
    }

    @Override
    public List<CapacitySignal> observe(String actorId) {
        long count = commitmentStore.countOpenByObligor(actorId);
        double pressure = Math.min((double) count / maxObligations, 1.0);
        return List.of(new CapacitySignal(
                actorId, CapacitySignalTypes.TASK_COUNT, pressure, Instant.now(),
                Map.of("commitmentCount", String.valueOf(count),
                       "maxObligations", String.valueOf(maxObligations))));
    }

    @Override
    public List<CapacitySignal> observeOverloaded(double threshold) {
        int minCount = (int) Math.ceil(threshold * maxObligations);
        return commitmentStore.findObligorsExceedingCount(minCount).entrySet().stream()
                .map(e -> new CapacitySignal(
                        e.getKey(), CapacitySignalTypes.TASK_COUNT,
                        Math.min((double) e.getValue() / maxObligations, 1.0),
                        Instant.now(),
                        Map.of("commitmentCount", String.valueOf(e.getValue()),
                               "maxObligations", String.valueOf(maxObligations))))
                .toList();
    }
}
