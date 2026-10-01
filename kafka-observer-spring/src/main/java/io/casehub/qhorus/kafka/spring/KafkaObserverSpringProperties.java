package io.casehub.qhorus.kafka.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Set;

@ConfigurationProperties(prefix = "casehub.qhorus.kafka")
public class KafkaObserverSpringProperties {

    private String topic = "qhorus-messages";
    private Set<String> channels;

    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }
    public Set<String> getChannels() { return channels; }
    public void setChannels(Set<String> channels) { this.channels = channels; }
}
