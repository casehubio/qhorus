package io.casehub.qhorus.webhook;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookRegistryTest {

    private WebhookRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new WebhookRegistry();
    }

    @Test
    void registerAndLookupByChannel() {
        UUID channelId = UUID.randomUUID();
        var  reg       = registry.registerInMemory(channelId, "t1", "https://example.com/hook", null, Map.of());

        assertThat(reg.id()).isNotNull();
        assertThat(registry.findByChannelId(channelId)).hasSize(1);
    }

    @Test
    void globalWebhookScopedByTenant() {
        registry.registerInMemory(null, "t1", "https://example.com/global", null, Map.of());
        registry.registerInMemory(null, "t2", "https://other.com/global", null, Map.of());

        UUID anyChannel = UUID.randomUUID();
        assertThat(registry.findForChannel(anyChannel, "t1")).hasSize(1);
        assertThat(registry.findForChannel(anyChannel, "t1").iterator().next().url())
                .isEqualTo("https://example.com/global");
        assertThat(registry.findForChannel(anyChannel, "t2")).hasSize(1);
    }

    @Test
    void channelSpecificPlusGlobal() {
        UUID channelId = UUID.randomUUID();
        registry.registerInMemory(channelId, "t1", "https://example.com/specific", null, Map.of());
        registry.registerInMemory(null, "t1", "https://example.com/global", null, Map.of());

        assertThat(registry.findForChannel(channelId, "t1")).hasSize(2);
    }

    @Test
    void deregisterRemoves() {
        UUID channelId = UUID.randomUUID();
        var  reg       = registry.registerInMemory(channelId, "t1", "https://example.com/hook", null, Map.of());

        assertThat(registry.deregisterInMemory(reg.id())).isTrue();
        assertThat(registry.findByChannelId(channelId)).isEmpty();
    }

    @Test
    void deregisterUnknownReturnsFalse() {
        assertThat(registry.deregisterInMemory(UUID.randomUUID())).isFalse();
    }

    @Test
    void deregisterGlobalWebhook() {
        var reg = registry.registerInMemory(null, "t1", "https://example.com/global", null, Map.of());

        assertThat(registry.deregisterInMemory(reg.id())).isTrue();
        assertThat(registry.findForChannel(UUID.randomUUID(), "t1")).isEmpty();
    }

    @Test
    void listAllByTenantFilters() {
        registry.registerInMemory(UUID.randomUUID(), "t1", "https://a.com", null, Map.of());
        registry.registerInMemory(null, "t2", "https://b.com", null, Map.of());

        assertThat(registry.listAll("t1")).hasSize(1);
        assertThat(registry.listAll("t2")).hasSize(1);
    }

    @Test
    void crossTenantIsolationForGlobalHooks() {
        registry.registerInMemory(null, "tenant-a", "https://a.com/hook", null, Map.of());
        registry.registerInMemory(null, "tenant-b", "https://b.com/hook", null, Map.of());

        UUID channelId = UUID.randomUUID();
        assertThat(registry.findForChannel(channelId, "tenant-a")).hasSize(1);
        assertThat(registry.findForChannel(channelId, "tenant-a").iterator().next().url())
                .isEqualTo("https://a.com/hook");
    }

    @Test
    void removeChannelCleansUp() {
        UUID channelId = UUID.randomUUID();
        registry.registerInMemory(channelId, "t1", "https://a.com", null, Map.of());
        registry.registerInMemory(channelId, "t1", "https://b.com", null, Map.of());

        registry.removeChannel(channelId);
        assertThat(registry.findByChannelId(channelId)).isEmpty();
    }
}
