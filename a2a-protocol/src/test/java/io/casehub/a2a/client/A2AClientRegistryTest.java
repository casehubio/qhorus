package io.casehub.a2a.client;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class A2AClientRegistryTest {

    @Test
    void getOrCreate_samEndpoint_returnsSameInstance() {
        A2AClientRegistry registry = new A2AClientRegistry();
        A2AClient client1 = registry.getOrCreate("https://example.com/", AuthConfig.NONE);
        A2AClient client2 = registry.getOrCreate("https://example.com/", AuthConfig.NONE);
        assertThat(client1).isSameAs(client2);
    }

    @Test
    void getOrCreate_normalizesTrailingSlash() {
        A2AClientRegistry registry = new A2AClientRegistry();
        A2AClient client1 = registry.getOrCreate("https://example.com", AuthConfig.NONE);
        A2AClient client2 = registry.getOrCreate("https://example.com/", AuthConfig.NONE);
        assertThat(client1).isSameAs(client2);
    }

    @Test
    void evict_removesClient() {
        A2AClientRegistry registry = new A2AClientRegistry();
        A2AClient client1 = registry.getOrCreate("https://example.com", AuthConfig.NONE);
        registry.evict("https://example.com");
        A2AClient client2 = registry.getOrCreate("https://example.com", AuthConfig.NONE);
        assertThat(client2).isNotSameAs(client1);
    }

    @Test
    void shutdown_clearsAll() {
        A2AClientRegistry registry = new A2AClientRegistry();
        A2AClient client1 = registry.getOrCreate("https://a.com", AuthConfig.NONE);
        registry.getOrCreate("https://b.com", AuthConfig.NONE);
        registry.shutdown();
        A2AClient client2 = registry.getOrCreate("https://a.com", AuthConfig.NONE);
        assertThat(client2).isNotSameAs(client1);
    }
}
