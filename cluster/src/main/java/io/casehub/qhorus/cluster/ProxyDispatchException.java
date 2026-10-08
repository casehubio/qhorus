package io.casehub.qhorus.cluster;

public class ProxyDispatchException extends RuntimeException {

    private final ProxyFallbackEvent event;

    public ProxyDispatchException(ProxyFallbackEvent event, Throwable cause) {
        super("Proxy dispatch to " + event.ownerNodeId() + " failed for channel "
              + event.channelId() + ": " + cause.getMessage(), cause);
        this.event = event;
    }

    public ProxyFallbackEvent event() {
        return event;
    }
}
