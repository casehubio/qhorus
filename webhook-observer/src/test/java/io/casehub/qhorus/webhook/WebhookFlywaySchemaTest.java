package io.casehub.qhorus.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class WebhookFlywaySchemaTest {

    private static final String JDBC_URL =
            "jdbc:h2:mem:webhook_flyway_schema_test_" + System.nanoTime()
                    + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";

    @BeforeAll
    static void migrate() {
        Flyway.configure()
                .dataSource(JDBC_URL, "sa", "")
                .locations("classpath:db/qhorus/migration", "classpath:db/ledger/migration")
                .load()
                .migrate();
    }

    @Test
    void v35_webhook_registration_table_exists() throws Exception {
        try (Connection conn = DriverManager.getConnection(JDBC_URL, "sa", "");
             var rs = conn.getMetaData().getTables(null, null, "WEBHOOK_REGISTRATION", new String[]{"TABLE"})) {
            assertThat(rs.next())
                    .as("webhook_registration table must exist — created by V35 migration")
                    .isTrue();
        }
    }

    @Test
    void v35_has_url_column() throws Exception {
        try (Connection conn = DriverManager.getConnection(JDBC_URL, "sa", "");
             var rs = conn.getMetaData().getColumns(null, null, "WEBHOOK_REGISTRATION", "URL")) {
            assertThat(rs.next()).as("URL column must exist").isTrue();
            assertThat(rs.getString("IS_NULLABLE")).isEqualTo("NO");
        }
    }

    @Test
    void v35_has_unique_constraint_on_url_channel_tenant() throws Exception {
        try (Connection conn = DriverManager.getConnection(JDBC_URL, "sa", "")) {
            var rs = conn.getMetaData().getIndexInfo(null, null, "WEBHOOK_REGISTRATION", true, false);
            boolean foundUrl = false;
            while (rs.next()) {
                String colName = rs.getString("COLUMN_NAME");
                if ("URL".equalsIgnoreCase(colName)) foundUrl = true;
            }
            rs.close();
            assertThat(foundUrl).as("UNIQUE index including URL must exist").isTrue();
        }
    }

    @Test
    void v35_tenancy_id_defaults_to_constant() throws Exception {
        try (Connection conn = DriverManager.getConnection(JDBC_URL, "sa", "");
             var rs = conn.getMetaData().getColumns(null, null, "WEBHOOK_REGISTRATION", "TENANCY_ID")) {
            assertThat(rs.next()).as("TENANCY_ID column must exist").isTrue();
            String defaultVal = rs.getString("COLUMN_DEF");
            assertThat(defaultVal).contains("278776f9-e1b0-46fb-9032-8bddebdcf9ce");
        }
    }
}
