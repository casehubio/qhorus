package io.casehub.qhorus.cache;

import io.casehub.qhorus.api.store.MessageStore;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@TestProfile(CacheCdiWiringTest.EnabledProfile.class)
class CacheCdiWiringTest {

    @Inject Instance<CachingMessageStore> cachingMessageStore;
    @Inject Instance<FullSyncService> fullSyncService;
    @Inject Instance<CachePopulationObserver> cachePopulationObserver;
    @Inject Instance<CacheSyncScheduler> cacheSyncScheduler;

    @Inject MessageStore messageStore;

    @Test
    void all_cache_beans_are_resolvable() {
        assertThat(cachingMessageStore.isResolvable()).isTrue();
        assertThat(fullSyncService.isResolvable()).isTrue();
        assertThat(cachePopulationObserver.isResolvable()).isTrue();
        assertThat(cacheSyncScheduler.isResolvable()).isTrue();
    }

    @Test
    void message_store_is_caching_store() {
        assertThat(ClientProxy.unwrap(messageStore)).isInstanceOf(CachingMessageStore.class);
    }

    public static class EnabledProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "casehub.qhorus.cache.enabled", "true",
                    "quarkus.datasource.qhorus.db-kind", "h2",
                    "quarkus.datasource.qhorus.username", "sa",
                    "quarkus.datasource.qhorus.password", "",
                    "quarkus.datasource.qhorus.jdbc.url", "jdbc:h2:mem:cache_enabled;DB_CLOSE_DELAY=-1",
                    "quarkus.datasource.qhorus.reactive", "false",
                    "quarkus.hibernate-orm.qhorus.database.generation", "drop-and-create"
            );
        }
    }
}
