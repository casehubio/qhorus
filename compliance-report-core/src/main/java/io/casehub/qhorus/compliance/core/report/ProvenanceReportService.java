package io.casehub.qhorus.compliance.core.report;

import io.casehub.qhorus.api.compliance.report.ProvenanceReport;
import io.casehub.qhorus.compliance.core.provdm.ProvJsonLdMapper;
import io.casehub.qhorus.runtime.ledger.CausalGraphService;

import java.time.Instant;

public class ProvenanceReportService {

    private final CausalGraphService causalGraphService;

    public ProvenanceReportService(CausalGraphService causalGraphService) {
        this.causalGraphService = causalGraphService;
    }

    public ProvenanceReport generate(String correlationId, int limit, String tenancyId) {
        var graph = causalGraphService.buildGraph(correlationId, limit, tenancyId);
        var provJsonLd = ProvJsonLdMapper.toProvJsonLd(graph);
        return new ProvenanceReport(correlationId, provJsonLd, Instant.now(), 1);
    }
}
