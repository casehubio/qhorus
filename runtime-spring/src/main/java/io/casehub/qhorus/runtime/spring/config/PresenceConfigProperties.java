package io.casehub.qhorus.runtime.spring.config;

import io.casehub.qhorus.runtime.config.PresenceConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "casehub.qhorus.presence")
public class PresenceConfigProperties implements PresenceConfig {

    private Duration awayTimeout = Duration.ofMinutes(2);
    private Duration offlineTimeout = Duration.ofMinutes(10);
    private Duration heartbeatInterval = Duration.ofSeconds(30);

    @Override public Duration awayTimeout() { return awayTimeout; }
    @Override public Duration offlineTimeout() { return offlineTimeout; }
    @Override public Duration heartbeatInterval() { return heartbeatInterval; }

    public void setAwayTimeout(Duration v) { this.awayTimeout = v; }
    public void setOfflineTimeout(Duration v) { this.offlineTimeout = v; }
    public void setHeartbeatInterval(Duration v) { this.heartbeatInterval = v; }
}
