package io.casehub.qhorus.runtime.spring.config;

import io.casehub.qhorus.runtime.config.DeliveryConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "casehub.qhorus.delivery")
public class DeliveryConfigProperties implements DeliveryConfig {

    private boolean enabled = true;
    private int batchSize = 100;
    private int maxConsecutiveFailures = 10;
    private String reconciliationInterval = "30s";
    private int maxParticipantRetriesPerCycle = 100;
    private int maxParticipantConsecutiveFailures = 3;

    @Override public boolean enabled() { return enabled; }
    @Override public int batchSize() { return batchSize; }
    @Override public int maxConsecutiveFailures() { return maxConsecutiveFailures; }
    @Override public String reconciliationInterval() { return reconciliationInterval; }
    @Override public int maxParticipantRetriesPerCycle() { return maxParticipantRetriesPerCycle; }
    @Override public int maxParticipantConsecutiveFailures() { return maxParticipantConsecutiveFailures; }

    public void setEnabled(boolean v) { this.enabled = v; }
    public void setBatchSize(int v) { this.batchSize = v; }
    public void setMaxConsecutiveFailures(int v) { this.maxConsecutiveFailures = v; }
    public void setReconciliationInterval(String v) { this.reconciliationInterval = v; }
    public void setMaxParticipantRetriesPerCycle(int v) { this.maxParticipantRetriesPerCycle = v; }
    public void setMaxParticipantConsecutiveFailures(int v) { this.maxParticipantConsecutiveFailures = v; }
}
