package io.casehub.qhorus.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WebhookRegistryTest {

    private WebhookRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new WebhookRegistry();
    }

    @Test
    void registerAndLookupByChannel() {
        UUID channelId = UUID.randomUUID();
        WebhookRegistration reg = registry.register(channelId, "https://example.com/hook", null, Map.of());

        assertThat(reg.id()).isNotNull();
        assertThat(registry.findByChannelId(channelId)).hasSize(1);
        assertThat(registry.findByChannelId(channelId).iterator().next().url()).isEqualTo("https://example.com/hook");
    }

    @Test
    void globalWebhookReceivedForAnyChannel() {
        registry.register(null, "https://example.com/global", null, Map.of());

        UUID anyChannel = UUID.randomUUID();
        var hooks = registry.findForChannel(anyChannel);

        assertThat(hooks).hasSize(1);
    }

    @Test
    void channelSpecificPlusGlobal() {
        UUID channelId = UUID.randomUUID();
        registry.register(channelId, "https://example.com/specific", null, Map.of());
        registry.register(null, "https://example.com/global", null, Map.of());

        var hooks = registry.findForChannel(channelId);

        assertThat(hooks).hasSize(2);
    }

    @Test
    void deregisterRemoves() {
        UUID channelId = UUID.randomUUID();
        WebhookRegistration reg = registry.register(channelId, "https://example.com/hook", null, Map.of());

        assertThat(registry.deregister(reg.id())).isTrue();
        assertThat(registry.findByChannelId(channelId)).isEmpty();
    }

    @Test
    void deregisterUnknownReturnsFalse() {
        assertThat(registry.deregister(UUID.randomUUID())).isFalse();
    }

    @Test
    void deregisterGlobalWebhook() {
        WebhookRegistration reg = registry.register(null, "https://example.com/global", null, Map.of());

        assertThat(registry.deregister(reg.id())).isTrue();
        assertThat(registry.findForChannel(UUID.randomUUID())).isEmpty();
    }

    @Test
    void listAllReturnsAllRegistrations() {
        registry.register(UUID.randomUUID(), "https://example.com/a", null, Map.of());
        registry.register(null, "https://example.com/b", null, Map.of());

        assertThat(registry.listAll()).hasSize(2);
    }
}
