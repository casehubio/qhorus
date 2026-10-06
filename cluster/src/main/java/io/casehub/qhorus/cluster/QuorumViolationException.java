package io.casehub.qhorus.cluster;

public class QuorumViolationException extends RuntimeException {
    public QuorumViolationException(String message) {
        super(message);
    }
}
