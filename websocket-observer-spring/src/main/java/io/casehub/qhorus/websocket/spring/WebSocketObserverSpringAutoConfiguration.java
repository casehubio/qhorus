package io.casehub.qhorus.websocket.spring;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.qhorus.api.store.CrossTenantChannelStore;
import io.casehub.qhorus.api.store.CrossTenantMessageStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@AutoConfiguration
@ConditionalOnClass(WebSocketConfigurer.class)
@EnableWebSocket
@EnableConfigurationProperties(WebSocketObserverSpringProperties.class)
public class WebSocketObserverSpringAutoConfiguration implements WebSocketConfigurer {

    private final SpringWebSocketConnectionRegistry registry;
    private final CrossTenantChannelStore channelStore;
    private final CrossTenantMessageStore messageStore;
    private final ObjectMapper objectMapper;
    private final WebSocketObserverSpringProperties properties;

    public WebSocketObserverSpringAutoConfiguration(CrossTenantChannelStore channelStore,
                                                     CrossTenantMessageStore messageStore,
                                                     ObjectMapper objectMapper,
                                                     WebSocketObserverSpringProperties properties) {
        this.registry = new SpringWebSocketConnectionRegistry();
        this.channelStore = channelStore;
        this.messageStore = messageStore;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry wsRegistry) {
        wsRegistry.addHandler(channelWebSocketHandler(), "/qhorus/ws/channels/{channelId}")
                .setAllowedOrigins("*");
    }

    @Bean
    @ConditionalOnMissingBean
    SpringWebSocketConnectionRegistry webSocketConnectionRegistry() {
        return registry;
    }

    @Bean
    @ConditionalOnMissingBean(name = "channelWebSocketHandler")
    WebSocketHandler channelWebSocketHandler() {
        return new ChannelWebSocketHandler(registry, channelStore, messageStore,
                objectMapper, properties.getMaxMessages());
    }

    @Bean
    @ConditionalOnMissingBean
    SpringWebSocketMessageObserver webSocketMessageObserver(ObjectMapper objectMapper,
                                                             SpringWebSocketConnectionRegistry registry) {
        return new SpringWebSocketMessageObserver(objectMapper, registry);
    }
}
