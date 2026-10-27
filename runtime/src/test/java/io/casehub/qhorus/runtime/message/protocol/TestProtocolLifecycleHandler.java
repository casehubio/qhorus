package io.casehub.qhorus.runtime.message.protocol;

import io.casehub.qhorus.api.spi.ProtocolLifecycleContext;
import io.casehub.qhorus.api.spi.ProtocolLifecycleHandler;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

@ApplicationScoped
public class TestProtocolLifecycleHandler implements ProtocolLifecycleHandler {

    static final String SUPPORTED_PROTOCOL = "TEST_LIFECYCLE_PROTO";

    private final List<ProtocolLifecycleContext> initCalls = new CopyOnWriteArrayList<>();
    private final List<DestroyCall> destroyCalls = new CopyOnWriteArrayList<>();

    @Override
    public boolean supports(String protocolName) {
        return SUPPORTED_PROTOCOL.equals(protocolName);
    }

    @Override
    public void onInit(ProtocolLifecycleContext context) {
        initCalls.add(context);
    }

    @Override
    public void onDestroy(UUID channelId, String protocolName) {
        destroyCalls.add(new DestroyCall(channelId, protocolName));
    }

    public List<ProtocolLifecycleContext> initCalls() {
        return initCalls;
    }

    public List<DestroyCall> destroyCalls() {
        return destroyCalls;
    }

    public void reset() {
        initCalls.clear();
        destroyCalls.clear();
    }

    public record DestroyCall(UUID channelId, String protocolName) {}
}
