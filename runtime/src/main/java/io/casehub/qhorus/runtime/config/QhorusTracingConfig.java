package io.casehub.qhorus.runtime.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * OpenTelemetry tracing configuration for Qhorus operations.
 */
@ConfigMapping(prefix = "casehub.qhorus.tracing")
public interface QhorusTracingConfig {

    /**
     * Master switch for all tracing in Qhorus. When false, no spans are created
     * regardless of individual operation settings. Default: true.
     */
    @WithDefault("true")
    boolean enabled();

    /**
     * Trace message dispatch operations (MessageService.dispatch). Default: true.
     */
    @WithDefault("true")
    boolean dispatch();

    /**
     * Trace commitment lifecycle operations (open, fulfill, decline, etc). Default: true.
     */
    @WithDefault("true")
    boolean commitments();

    /**
     * Trace channel gateway fan-out to backends. Default: true.
     */
    @WithDefault("true")
    boolean fanOut();

    /**
     * Trace ledger write operations. Default: true.
     */
    @WithDefault("true")
    boolean ledgerWrite();

    /**
     * Trace delivery service operations (AT_LEAST_ONCE backends). Default: true.
     */
    @WithDefault("true")
    boolean delivery();
}
