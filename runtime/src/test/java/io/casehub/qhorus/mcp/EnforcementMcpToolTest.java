package io.casehub.qhorus.mcp;

import io.casehub.qhorus.api.channel.ChannelDetail;
import io.casehub.qhorus.api.channel.EnforcementMode;
import io.casehub.qhorus.runtime.mcp.QhorusMcpTools;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.TestTransaction;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@QuarkusTest
class EnforcementMcpToolTest {

    @Inject
    QhorusMcpTools tools;

    @Test
    @TestTransaction
    void setEnforcementModeAndRetrieve() {
        ChannelDetail ch = tools.createChannel("enforce-test-1", null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        tools.setEnforcementMode("enforce-test-1", "BLOCKING");
        @SuppressWarnings("unchecked")
        Map<String, Object> enforcement = tools.getChannelEnforcement("enforce-test-1");
        assertThat(enforcement.get("enforcement_mode")).isEqualTo("BLOCKING");
        assertThat((java.util.List<?>) enforcement.get("enforcement_exclusions")).isEmpty();
    }

    @Test
    @TestTransaction
    void setEnforcementExclusionsAndRetrieve() {
        tools.createChannel("enforce-test-2", null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        tools.setEnforcementExclusions("enforce-test-2", "TYPE_POLICY,CORRELATION_INTEGRITY");
        @SuppressWarnings("unchecked")
        Map<String, Object> enforcement = tools.getChannelEnforcement("enforce-test-2");
        @SuppressWarnings("unchecked")
        java.util.List<String> exclusions = (java.util.List<String>) enforcement.get("enforcement_exclusions");
        assertThat(exclusions).containsExactly("TYPE_POLICY", "CORRELATION_INTEGRITY");
    }

    @Test
    @TestTransaction
    void getChannelEnforcementIncludesAvailableSources() {
        tools.createChannel("enforce-test-3", null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        @SuppressWarnings("unchecked")
        Map<String, Object> enforcement = tools.getChannelEnforcement("enforce-test-3");
        @SuppressWarnings("unchecked")
        java.util.List<String> sources = (java.util.List<String>) enforcement.get("available_sources");
        assertThat(sources).contains("TYPE_POLICY", "CORRELATION_INTEGRITY");
    }

    @Test
    @TestTransaction
    void invalidModeThrows() {
        tools.createChannel("enforce-test-4", null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        assertThatThrownBy(() -> tools.setEnforcementMode("enforce-test-4", "INVALID"))
                .isInstanceOf(io.quarkiverse.mcp.server.ToolCallException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid enforcement mode");
    }
}
