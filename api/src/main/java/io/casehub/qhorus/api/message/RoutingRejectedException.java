package io.casehub.qhorus.api.message;

public class RoutingRejectedException extends IllegalStateException {
    public RoutingRejectedException(String reason) {
        super(reason);
    }
}
