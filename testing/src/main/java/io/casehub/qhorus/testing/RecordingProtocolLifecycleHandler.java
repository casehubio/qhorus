package io.casehub.qhorus.testing;

import io.casehub.qhorus.api.spi.ProtocolLifecycleContext;
import io.casehub.qhorus.api.spi.ProtocolLifecycleHandler;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public class RecordingProtocolLifecycleHandler implements ProtocolLifecycleHandler {

    private final Set<String> supportedProtocols;
    private final List<ProtocolLifecycleContext> initCalls = new CopyOnWriteArrayList<>();
    private final List<DestroyCall> destroyCalls = new CopyOnWriteArrayList<>();
    private RuntimeException throwOnInit;

    public RecordingProtocolLifecycleHandler(String... protocols) {
        this.supportedProtocols = Set.of(protocols);
    }

    @Override
    public boolean supports(String protocolName) {
        return supportedProtocols.contains(protocolName);
    }

    @Override
    public void onInit(ProtocolLifecycleContext context) {
        if (throwOnInit != null) {
            RuntimeException e = throwOnInit;
            throwOnInit = null;
            throw e;
        }
        initCalls.add(context);
    }

    @Override
    public void onDestroy(UUID channelId, String protocolName) {
        destroyCalls.add(new DestroyCall(channelId, protocolName));
    }

    public void throwOnNextInit(RuntimeException e) {
        this.throwOnInit = e;
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
        throwOnInit = null;
    }

    public record DestroyCall(UUID channelId, String protocolName) {}
}
