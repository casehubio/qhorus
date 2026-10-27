package io.casehub.qhorus.api.spi;

import java.util.UUID;

public interface ProtocolLifecycleHandler {

    boolean supports(String protocolName);

    void onInit(ProtocolLifecycleContext context);

    void onDestroy(UUID channelId, String protocolName);
}
