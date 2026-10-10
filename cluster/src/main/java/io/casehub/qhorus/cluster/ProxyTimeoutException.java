package io.casehub.qhorus.cluster;

public class ProxyTimeoutException extends RuntimeException {
    public ProxyTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
