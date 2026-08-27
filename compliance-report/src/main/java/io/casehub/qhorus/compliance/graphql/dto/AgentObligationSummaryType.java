package io.casehub.qhorus.compliance.graphql.dto;

import io.casehub.qhorus.compliance.model.AgentObligationSummary;
import org.eclipse.microprofile.graphql.Type;

@Type("AgentObligationSummary")
public record AgentObligationSummaryType(
        String actorId,
        int total,
        int fulfilled,
        int failed,
        int declined,
        int delegated,
        int stillOpen,
        int stalled,
        double fulfillmentRate,
        Double currentTrustScore) {

    public static AgentObligationSummaryType from(AgentObligationSummary s) {
        return new AgentObligationSummaryType(
                s.actorId(), s.total(), s.fulfilled(), s.failed(),
                s.declined(), s.delegated(), s.stillOpen(), s.stalled(),
                s.fulfillmentRate(), s.currentTrustScore());
    }
}
