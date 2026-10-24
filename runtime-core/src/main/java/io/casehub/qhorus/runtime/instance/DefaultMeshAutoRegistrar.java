package io.casehub.qhorus.runtime.instance;

import io.casehub.qhorus.api.message.MessageDispatch;
import io.casehub.qhorus.api.spi.MeshAutoRegistrar;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@DefaultBean
@ApplicationScoped
public class DefaultMeshAutoRegistrar implements MeshAutoRegistrar {

    @Override
    public Optional<InstanceRegistration> autoRegister(MessageDispatch dispatch) {
        return Optional.of(new InstanceRegistration(
                dispatch.sender(),
                "Auto-registered by mesh participation policy",
                List.of(),
                false,
                Map.of()));
    }
}
