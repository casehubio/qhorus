package io.casehub.qhorus.api.channel;

public enum ParticipationPolicyMode {
    OPEN,
    AUTO_REGISTER,
    ENFORCE;

    public boolean requiresLookup() {
        return this != OPEN;
    }
}
