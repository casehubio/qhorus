package io.casehub.qhorus.api.channel;

public enum PresenceStatus {
    ONLINE,
    AVAILABLE,
    BUSY,
    AWAY,
    OFFLINE;

    public boolean isReportable() {
        return this == ONLINE || this == AVAILABLE || this == BUSY;
    }
}
