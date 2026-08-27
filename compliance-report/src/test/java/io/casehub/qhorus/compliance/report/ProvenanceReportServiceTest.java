package io.casehub.qhorus.compliance.report;

import io.casehub.qhorus.compliance.model.ProvenanceReport;
import io.casehub.qhorus.runtime.ledger.CausalGraphService;
import io.casehub.qhorus.runtime.ledger.CausalGraphService.CausalGraph;
import io.casehub.qhorus.runtime.ledger.CausalGraphService.GraphEdge;
import io.casehub.qhorus.runtime.ledger.CausalGraphService.GraphNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProvenanceReportServiceTest {

    static final String TENANCY = "test-tenant";
    static final String CORR_ID = "corr-" + UUID.randomUUID();

    @Mock CausalGraphService causalGraphService;

    ProvenanceReportService service;

    @BeforeEach
    void setUp() {
        service = new ProvenanceReportService();
        service.causalGraphService = causalGraphService;
    }

    @Test
    @SuppressWarnings("unchecked")
    void generate_producesProvJsonLdFromCausalGraph() {
        UUID e1 = UUID.randomUUID();
        UUID ch1 = UUID.randomUUID();
        GraphNode node = new GraphNode(
                e1.toString(), ch1.toString(), "channel-1",
                "COMMAND", "agent-1", "2026-08-27T10:00:00Z",
                "do X", null, 0);
        CausalGraph graph = new CausalGraph(
                CORR_ID, e1.toString(), 1, List.of("channel-1"),
                null, "OPEN", false,
                List.of(node), List.of());

        when(causalGraphService.buildGraph(CORR_ID, 200, TENANCY)).thenReturn(graph);

        ProvenanceReport report = service.generate(CORR_ID, 200, TENANCY);

        assertThat(report.correlationId()).isEqualTo(CORR_ID);
        assertThat(report.provJsonLd()).containsKey("@context");
        assertThat(report.provJsonLd()).containsKey("agent");
        assertThat(report.provJsonLd()).containsKey("activity");
        var agents = (Map<String, Object>) report.provJsonLd().get("agent");
        assertThat(agents).containsKey("ledger:actor/agent-1");
        assertThat(report.schemaVersion()).isEqualTo(1);
    }

    @Test
    void generate_emptyGraph_returnsEmptyProvDocument() {
        CausalGraph empty = new CausalGraph(
                CORR_ID, null, 0, List.of(), null, "OPEN",
                false, List.of(), List.of());
        when(causalGraphService.buildGraph(CORR_ID, 200, TENANCY)).thenReturn(empty);

        ProvenanceReport report = service.generate(CORR_ID, 200, TENANCY);

        assertThat(report.correlationId()).isEqualTo(CORR_ID);
        assertThat(report.provJsonLd()).containsKey("@context");
        assertThat((Map<?, ?>) report.provJsonLd().get("agent")).isEmpty();
    }
}
