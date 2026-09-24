package io.casehub.qhorus.api.spi;

public enum Severity {
    ADVISORY,
    WARNING,
    CRITICAL;

    public boolean isAtLeast(Severity threshold) {
        return this.ordinal() >= threshold.ordinal();
    }
}