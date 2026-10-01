package io.casehub.qhorus.websocket.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "casehub.qhorus.websocket.catchup")
public class WebSocketObserverSpringProperties {

    private int maxMessages = 500;

    public int getMaxMessages() { return maxMessages; }
    public void setMaxMessages(int maxMessages) { this.maxMessages = maxMessages; }
}
