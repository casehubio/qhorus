package io.casehub.qhorus.compliance.format;

import io.casehub.qhorus.compliance.model.AgentObligationSummary;
import io.casehub.qhorus.compliance.model.AttributionNode;
import io.casehub.qhorus.compliance.model.AttributionReport;
import io.casehub.qhorus.compliance.model.ChannelObligationSummary;
import io.casehub.qhorus.compliance.model.JudgmentAttributionReport;
import io.casehub.qhorus.compliance.model.JudgmentFulfillmentReport;
import io.casehub.qhorus.compliance.model.ObligationReport;
import io.casehub.qhorus.compliance.model.ReportFormat;
import io.casehub.qhorus.compliance.model.TrustHistoryReport;
import io.casehub.qhorus.compliance.model.ViolationEntry;
import io.casehub.qhorus.compliance.model.ViolationReport;
import jakarta.enterprise.context.ApplicationScoped;

import java.nio.charset.StandardCharsets;

@ApplicationScoped
public class CsvReportRenderer implements ReportRenderer {

    @Override
    public String contentType() {
        return "text/csv";
    }

    @Override
    public byte[] render(Object report) {
        String csv = switch (report) {
            case AttributionReport r -> renderAttribution(r);
            case ObligationReport r -> renderObligation(r);
            case ViolationReport r -> renderViolation(r);
            case TrustHistoryReport r -> renderTrustHistory(r);
            case JudgmentAttributionReport r -> renderJudgmentAttribution(r);
            case JudgmentFulfillmentReport r -> renderJudgmentFulfillment(r);
            default -> throw new IllegalArgumentException("Unsupported report type for CSV: " + report.getClass().getSimpleName());
        };
        return csv.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public boolean supports(ReportFormat format) {
        return format == ReportFormat.CSV;
    }

    private String renderAttribution(AttributionReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("entryId,channel,messageType,actorId,timestamp,depth,trustScore,verdict,algorithmRef,confidenceScore\n");
        for (AttributionNode n : report.nodes()) {
            sb.append(escape(n.entryId())).append(',');
            sb.append(escape(n.channelName())).append(',');
            sb.append(escape(n.messageType())).append(',');
            sb.append(escape(n.actorId())).append(',');
            sb.append(escape(n.occurredAt())).append(',');
            sb.append(n.depth()).append(',');
            sb.append(n.trustScoreAtTime() != null ? n.trustScoreAtTime() : "").append(',');
            sb.append(escape(n.attestationVerdict())).append(',');
            sb.append(escape(n.algorithmRef())).append(',');
            sb.append(n.confidenceScore() != null ? n.confidenceScore() : "");
            sb.append('\n');
        }
        return sb.toString();
    }

    private String renderObligation(ObligationReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("channelId,channelName,total,fulfilled,failed,declined,delegated,stillOpen,stalled,fulfillmentRate\n");
        for (ChannelObligationSummary c : report.channels()) {
            sb.append(escape(c.channelId().toString())).append(',');
            sb.append(escape(c.channelName())).append(',');
            sb.append(c.total()).append(',');
            sb.append(c.fulfilled()).append(',');
            sb.append(c.failed()).append(',');
            sb.append(c.declined()).append(',');
            sb.append(c.delegated()).append(',');
            sb.append(c.stillOpen()).append(',');
            sb.append(c.stalled()).append(',');
            sb.append(c.fulfillmentRate());
            sb.append('\n');
        }
        sb.append("\nactorId,total,fulfilled,failed,declined,delegated,stillOpen,stalled,fulfillmentRate,trustScore\n");
        for (AgentObligationSummary a : report.agents()) {
            sb.append(escape(a.actorId())).append(',');
            sb.append(a.total()).append(',');
            sb.append(a.fulfilled()).append(',');
            sb.append(a.failed()).append(',');
            sb.append(a.declined()).append(',');
            sb.append(a.delegated()).append(',');
            sb.append(a.stillOpen()).append(',');
            sb.append(a.stalled()).append(',');
            sb.append(a.fulfillmentRate()).append(',');
            sb.append(a.currentTrustScore() != null ? a.currentTrustScore() : "");
            sb.append('\n');
        }
        return sb.toString();
    }

    private String renderViolation(ViolationReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("occurredAt,sender,messageType,enforcementMode,action,ledgerEntryId\n");
        for (ViolationEntry v : report.violations()) {
            sb.append(escape(v.occurredAt() != null ? v.occurredAt().toString() : "")).append(',');
            sb.append(escape(v.sender())).append(',');
            sb.append(escape(v.messageType())).append(',');
            sb.append(escape(v.enforcementMode())).append(',');
            sb.append(escape(v.action())).append(',');
            sb.append(escape(v.ledgerEntryId() != null ? v.ledgerEntryId().toString() : ""));
            sb.append('\n');
        }
        return sb.toString();
    }

    private String renderTrustHistory(TrustHistoryReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("actorId,currentScore\n");
        for (var actor : report.actors()) {
            sb.append(escape(actor.actorId())).append(',');
            sb.append(actor.currentScore() != null ? actor.currentScore() : "");
            sb.append('\n');
        }
        return sb.toString();
    }


    private String renderJudgmentAttribution(JudgmentAttributionReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("eventKind,actorId,occurredAt,evidenceQuality,verificationOutcome,escalationReason,trustScore,durationMs\n");
        for (var e : report.events()) {
            sb.append(String.join(",",
                                  escape(e.eventKind()), escape(e.actorId()),
                                  escape(e.occurredAt() != null ? e.occurredAt().toString() : ""),
                                  e.evidenceQuality() != null ? String.valueOf(e.evidenceQuality()) : "",
                                  escape(e.verificationOutcome() != null ? e.verificationOutcome() : ""),
                                  escape(e.escalationReason() != null ? e.escalationReason() : ""),
                                  e.trustScoreAtTime() != null ? String.valueOf(e.trustScoreAtTime()) : "",
                                  e.durationMs() != null ? String.valueOf(e.durationMs()) : ""
                                 )).append("\n");
        }
        return sb.toString();
    }

    private String renderJudgmentFulfillment(JudgmentFulfillmentReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("judgmentType,total,accepted,rejected,escalated,pending,acceptanceRate,avgResponseTimeMs,avgEvidenceQuality\n");
        for (var t : report.byType()) {
            sb.append(String.join(",",
                                  escape(t.judgmentType()), String.valueOf(t.total()),
                                  String.valueOf(t.accepted()), String.valueOf(t.rejected()),
                                  String.valueOf(t.escalated()), String.valueOf(t.pending()),
                                  String.valueOf(t.acceptanceRate()),
                                  String.valueOf(t.averageResponseTimeMs()),
                                  String.valueOf(t.averageEvidenceQuality())
                                 )).append("\n");
        }
        sb.append("\nactorId,total,accepted,rejected,escalated,pending,acceptanceRate,avgResponseTimeMs,avgEvidenceQuality,trustScore\n");
        for (var c : report.byCaller()) {
            sb.append(String.join(",",
                                  escape(c.actorId()), String.valueOf(c.total()),
                                  String.valueOf(c.accepted()), String.valueOf(c.rejected()),
                                  String.valueOf(c.escalated()), String.valueOf(c.pending()),
                                  String.valueOf(c.acceptanceRate()),
                                  String.valueOf(c.averageResponseTimeMs()),
                                  String.valueOf(c.averageEvidenceQuality()),
                                  c.currentTrustScore() != null ? String.valueOf(c.currentTrustScore()) : ""
                                 )).append("\n");
        }
        return sb.toString();
    }

    static String escape(String value) {
        if (value == null) return "";
        if (value.indexOf(',') >= 0 || value.indexOf('"') >= 0 || value.indexOf('\n') >= 0) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
