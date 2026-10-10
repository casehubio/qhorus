package io.casehub.qhorus.cluster;

public class ProxyAuthException extends RuntimeException {

    private final String targetNodeId;
    private final int statusCode;

    public ProxyAuthException(String targetNodeId, int statusCode) {
        super("Authentication rejected by " + targetNodeId + " (HTTP " + statusCode + ")");
        this.targetNodeId = targetNodeId;
        this.statusCode = statusCode;
    }

    public String targetNodeId() {
        return targetNodeId;
    }

    public int statusCode() {
        return statusCode;
    }
}
