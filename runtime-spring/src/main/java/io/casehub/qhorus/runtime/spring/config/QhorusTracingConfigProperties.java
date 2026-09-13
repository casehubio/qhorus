package io.casehub.qhorus.runtime.spring.config;

import io.casehub.qhorus.runtime.config.QhorusTracingConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "casehub.qhorus.tracing")
public class QhorusTracingConfigProperties implements QhorusTracingConfig {

    private boolean enabled = true;
    private boolean dispatch = true;
    private boolean commitments = true;
    private boolean fanOut = true;
    private boolean ledgerWrite = true;
    private boolean delivery = true;

    @Override public boolean enabled() { return enabled; }
    @Override public boolean dispatch() { return dispatch; }
    @Override public boolean commitments() { return commitments; }
    @Override public boolean fanOut() { return fanOut; }
    @Override public boolean ledgerWrite() { return ledgerWrite; }
    @Override public boolean delivery() { return delivery; }

    public void setEnabled(boolean v) { this.enabled = v; }
    public void setDispatch(boolean v) { this.dispatch = v; }
    public void setCommitments(boolean v) { this.commitments = v; }
    public void setFanOut(boolean v) { this.fanOut = v; }
    public void setLedgerWrite(boolean v) { this.ledgerWrite = v; }
    public void setDelivery(boolean v) { this.delivery = v; }
}
