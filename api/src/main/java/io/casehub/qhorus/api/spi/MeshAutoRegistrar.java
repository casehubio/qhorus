package io.casehub.qhorus.api.spi;

import io.casehub.qhorus.api.message.MessageDispatch;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface MeshAutoRegistrar {

    /**
     * Called when AUTO_REGISTER mode fires for an unregistered sender.
     * Returns the instance to register, or empty to skip auto-registration
     * (effectively treating this dispatch as OPEN for this sender).
     *
     * Exceptions propagate and fail the dispatch — the sender is not
     * registered and the message is not delivered.
     */
    Optional<InstanceRegistration> autoRegister(MessageDispatch dispatch);

    record InstanceRegistration(
        String instanceId,
        String description,
        List<String> capabilityTags,
        boolean readOnly,
        Map<String, String> metadata
    ) {}
}
