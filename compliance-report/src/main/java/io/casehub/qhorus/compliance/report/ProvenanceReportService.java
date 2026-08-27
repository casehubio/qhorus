package io.casehub.qhorus.compliance.report;

import io.casehub.qhorus.compliance.model.ProvenanceReport;
import io.casehub.qhorus.compliance.provdm.ProvJsonLdMapper;
import io.casehub.qhorus.runtime.ledger.CausalGraphService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;

@ApplicationScoped
public class ProvenanceReportService {

    @Inject CausalGraphService causalGraphService;

    public ProvenanceReport generate(String correlationId, int limit, String tenancyId) {
        var graph = causalGraphService.buildGraph(correlationId, limit, tenancyId);
        var provJsonLd = ProvJsonLdMapper.toProvJsonLd(graph);
        return new ProvenanceReport(correlationId, provJsonLd, Instant.now(), 1);
    }
}
