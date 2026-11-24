package io.casehub.qhorus.runtime.identity;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.HashMap;
import java.util.Map;

public class PlatformApiKeyProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> config = new HashMap<>();
        config.put("casehub.qhorus.identity.platform-api-keys", "true");
        config.put("quarkus.datasource.qhorus.db-kind", "h2");
        config.put("quarkus.datasource.qhorus.jdbc.url", "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1");
        config.put("quarkus.datasource.qhorus.username", "sa");
        config.put("quarkus.datasource.qhorus.password", "");
        config.put("quarkus.datasource.qhorus.reactive", "false");
        config.put("quarkus.hibernate-orm.qhorus.database.generation", "drop-and-create");
        return config;
    }
}
