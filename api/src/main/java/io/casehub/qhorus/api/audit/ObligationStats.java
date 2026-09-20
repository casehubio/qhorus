package io.casehub.qhorus.api.audit;

public record ObligationStats(
        int totalCommands,
        int fulfilled,
        int failed,
        int declined,
        int delegated,
        int stillOpen,
        int stalled,
        double fulfillmentRate) {
}
