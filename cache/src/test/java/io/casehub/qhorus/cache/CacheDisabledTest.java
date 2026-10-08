package io.casehub.qhorus.cache;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@TestProfile(CacheDisabledTest.DisabledProfile.class)
class CacheDisabledTest {

    @Inject Instance<CachingMessageStore> cachingMessageStore;
    @Inject Instance<FullSyncService> fullSyncService;
    @Inject Instance<CacheSyncScheduler> cacheSyncScheduler;

    @Test
    void cache_beans_not_resolvable_when_disabled() {
        assertThat(cachingMessageStore.isResolvable()).isFalse();
        assertThat(fullSyncService.isResolvable()).isFalse();
        assertThat(cacheSyncScheduler.isResolvable()).isFalse();
    }

    public static class DisabledProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "quarkus.datasource.qhorus.db-kind", "h2",
                    "quarkus.datasource.qhorus.username", "sa",
                    "quarkus.datasource.qhorus.password", "",
                    "quarkus.datasource.qhorus.jdbc.url", "jdbc:h2:mem:cache_disabled;DB_CLOSE_DELAY=-1",
                    "quarkus.datasource.qhorus.reactive", "false",
                    "quarkus.hibernate-orm.qhorus.database.generation", "drop-and-create"
            );
        }
    }
}
