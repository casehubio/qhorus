package io.casehub.qhorus.kafka.spring;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Set;

@AutoConfiguration
@ConditionalOnClass(KafkaTemplate.class)
@EnableConfigurationProperties(KafkaObserverSpringProperties.class)
public class KafkaObserverSpringAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    SpringKafkaMessageObserver kafkaMessageObserver(ObjectMapper objectMapper,
                                                     KafkaTemplate<String, byte[]> kafkaTemplate,
                                                     KafkaObserverSpringProperties properties) {
        Set<String> filter = properties.getChannels() != null
                ? properties.getChannels()
                : Set.of();
        return new SpringKafkaMessageObserver(objectMapper, kafkaTemplate,
                properties.getTopic(), filter);
    }
}
