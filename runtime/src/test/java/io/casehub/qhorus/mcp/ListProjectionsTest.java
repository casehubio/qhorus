package io.casehub.qhorus.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.casehub.qhorus.runtime.mcp.QhorusMcpTools;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Tests for {@code list_projections} MCP tool.
 * Refs qhorus#240.
 */
@QuarkusTest
class ListProjectionsTest {

    @Inject QhorusMcpTools tools;

    @Test
    void listProjections_returnsSortedList() {
        List<String> names = tools.listProjections();

        assertThat(names).isSortedAccordingTo(String::compareTo);
    }

    @Test
    void listProjections_returnsListNotNull() {
        List<String> names = tools.listProjections();

        assertThat(names).isNotNull();
    }
}
