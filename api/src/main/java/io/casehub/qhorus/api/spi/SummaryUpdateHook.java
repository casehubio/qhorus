package io.casehub.qhorus.api.spi;

@FunctionalInterface
public interface SummaryUpdateHook {
    SummaryResult update(SummaryUpdateContext context);
}
