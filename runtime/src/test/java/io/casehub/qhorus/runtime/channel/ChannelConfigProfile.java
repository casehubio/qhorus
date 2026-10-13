package io.casehub.qhorus.runtime.channel;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.HashMap;
import java.util.Map;

public class ChannelConfigProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> config = new HashMap<>();
        config.put("casehub.qhorus.channels.it-reviews.semantic", "APPEND");
        config.put("casehub.qhorus.channels.it-reviews.description", "Integration test reviews");
        config.put("casehub.qhorus.channels.it-status.semantic", "BROADCAST");
        config.put("casehub.qhorus.channels.it-status.description", "Integration test status");
        config.put("casehub.qhorus.channels.it-status.allowed-types", "STATUS,EVENT");

        config.put("quarkus.datasource.qhorus.db-kind", "h2");
        config.put("quarkus.datasource.qhorus.jdbc.url", "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1");
        config.put("quarkus.datasource.qhorus.username", "sa");
        config.put("quarkus.datasource.qhorus.password", "");
        config.put("quarkus.datasource.qhorus.reactive", "false");
        config.put("quarkus.hibernate-orm.qhorus.database.generation", "drop-and-create");
        config.put("quarkus.hibernate-orm.qhorus.sql-load-script", "import-qhorus-test.sql");
        return config;
    }
}
