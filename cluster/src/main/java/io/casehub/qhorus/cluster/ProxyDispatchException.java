package io.casehub.qhorus.cluster;

public class ProxyDispatchException extends RuntimeException {

    private final ProxyFallbackEvent event;

    public ProxyDispatchException(ProxyFallbackEvent event, Throwable cause) {
        super("Proxy dispatch to " + event.ownerNodeId() + " failed for channel "
              + event.channelId() + ": " + cause.getMessage(), cause);
        this.event = event;
    }

    public ProxyDispatchException(String nodeId, int statusCode, String message) {
        super("Proxy to " + nodeId + " failed (HTTP " + statusCode + "): " + message);
        this.event = null;
    }


    public ProxyFallbackEvent event() {
        return event;
    }
}
