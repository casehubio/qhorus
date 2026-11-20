package io.casehub.qhorus.playbook.bridge;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CompilationCacheTest {

    private CompilationCache cache;

    @BeforeEach
    void setUp() {
        cache = new CompilationCache();
    }

    @Test
    void put_and_get() {
        UUID ch = UUID.randomUUID();
        cache.put(ch, "gate", "yaml-content");
        assertThat(cache.get(ch, "gate")).contains("yaml-content");
    }

    @Test
    void get_returnsEmpty_forMissing() {
        assertThat(cache.get(UUID.randomUUID(), "missing")).isEmpty();
    }

    @Test
    void evict_removesEntry() {
        UUID ch = UUID.randomUUID();
        cache.put(ch, "gate", "content");
        cache.evict(ch, "gate");
        assertThat(cache.get(ch, "gate")).isEmpty();
    }

    @Test
    void evictAll_removesAllForChannel() {
        UUID ch = UUID.randomUUID();
        cache.put(ch, "gate-a", "a");
        cache.put(ch, "gate-b", "b");
        cache.put(UUID.randomUUID(), "other", "c");

        cache.evictAll(ch);

        assertThat(cache.get(ch, "gate-a")).isEmpty();
        assertThat(cache.get(ch, "gate-b")).isEmpty();
    }

    @Test
    void put_overwrites() {
        UUID ch = UUID.randomUUID();
        cache.put(ch, "gate", "v1");
        cache.put(ch, "gate", "v2");
        assertThat(cache.get(ch, "gate")).contains("v2");
    }
}
