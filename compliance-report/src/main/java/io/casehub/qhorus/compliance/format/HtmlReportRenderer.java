package io.casehub.qhorus.compliance.format;

import io.casehub.qhorus.compliance.model.AttributionNode;
import io.casehub.qhorus.compliance.model.AttributionReport;
import io.casehub.qhorus.compliance.model.ChannelObligationSummary;
import io.casehub.qhorus.compliance.model.JudgmentAttributionReport;
import io.casehub.qhorus.compliance.model.JudgmentFulfillmentReport;
import io.casehub.qhorus.compliance.model.ObligationReport;
import io.casehub.qhorus.compliance.model.ReportFormat;
import io.casehub.qhorus.compliance.model.ViolationEntry;
import io.casehub.qhorus.compliance.model.ViolationReport;
import jakarta.enterprise.context.ApplicationScoped;

import java.nio.charset.StandardCharsets;

@ApplicationScoped
public class HtmlReportRenderer implements ReportRenderer {

    private static final String CSS = """
            body { font-family: sans-serif; margin: 2em; }
            table { border-collapse: collapse; width: 100%; margin-bottom: 2em; }
            th, td { border: 1px solid #ccc; padding: 0.5em; text-align: left; }
            th { background: #f5f5f5; }
            h1, h2 { margin-top: 1.5em; }
            @media print { body { margin: 1cm; } }
            """;

    @Override
    public String contentType() {
        return "text/html";
    }

    @Override
    public byte[] render(Object report) {
        String html = switch (report) {
            case AttributionReport r -> renderAttribution(r);
            case ObligationReport r -> renderObligation(r);
            case ViolationReport r -> renderViolation(r);
            case JudgmentAttributionReport r -> renderJudgmentAttribution(r);
            case JudgmentFulfillmentReport r -> renderJudgmentFulfillment(r);
            default -> renderGeneric(report);
        };
        return html.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public boolean supports(ReportFormat format) {
        return format == ReportFormat.HTML;
    }

    private String renderAttribution(AttributionReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append(header("Attribution Report — " + esc(report.correlationId())));
        sb.append("<p>Outcome: ").append(esc(report.outcome()))
          .append(" | Channels: ").append(report.channelCount())
          .append(" | Duration: ").append(report.totalDurationMs() != null ? report.totalDurationMs() + "ms" : "N/A")
          .append("</p>\n");
        sb.append("<table><thead><tr>");
        sb.append("<th>Entry ID</th><th>Channel</th><th>Type</th><th>Actor</th>");
        sb.append("<th>Timestamp</th><th>Depth</th><th>Trust Score</th><th>Verdict</th>");
        sb.append("</tr></thead><tbody>\n");
        for (AttributionNode n : report.nodes()) {
            sb.append("<tr>");
            sb.append("<td>").append(esc(n.entryId())).append("</td>");
            sb.append("<td>").append(esc(n.channelName())).append("</td>");
            sb.append("<td>").append(esc(n.messageType())).append("</td>");
            sb.append("<td>").append(esc(n.actorId())).append("</td>");
            sb.append("<td>").append(esc(n.occurredAt())).append("</td>");
            sb.append("<td>").append(n.depth()).append("</td>");
            sb.append("<td>").append(n.trustScoreAtTime() != null ? n.trustScoreAtTime() : "").append("</td>");
            sb.append("<td>").append(esc(n.attestationVerdict())).append("</td>");
            sb.append("</tr>\n");
        }
        sb.append("</tbody></table>\n");
        sb.append(footer());
        return sb.toString();
    }

    private String renderObligation(ObligationReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append(header("Obligation Fulfillment Report"));
        sb.append("<p>Period: ").append(report.from()).append(" — ").append(report.to())
          .append(" | Overall Rate: ").append(String.format("%.1f%%", report.overallFulfillmentRate() * 100))
          .append("</p>\n");
        sb.append("<h2>Channels</h2>\n<table><thead><tr>");
        sb.append("<th>Channel</th><th>Total</th><th>Fulfilled</th><th>Failed</th>");
        sb.append("<th>Declined</th><th>Open</th><th>Rate</th>");
        sb.append("</tr></thead><tbody>\n");
        for (ChannelObligationSummary c : report.channels()) {
            sb.append("<tr>");
            sb.append("<td>").append(esc(c.channelName())).append("</td>");
            sb.append("<td>").append(c.total()).append("</td>");
            sb.append("<td>").append(c.fulfilled()).append("</td>");
            sb.append("<td>").append(c.failed()).append("</td>");
            sb.append("<td>").append(c.declined()).append("</td>");
            sb.append("<td>").append(c.stillOpen()).append("</td>");
            sb.append("<td>").append(String.format("%.1f%%", c.fulfillmentRate() * 100)).append("</td>");
            sb.append("</tr>\n");
        }
        sb.append("</tbody></table>\n");
        sb.append(footer());
        return sb.toString();
    }

    private String renderViolation(ViolationReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append(header("Violation Report — " + esc(report.channelName())));
        sb.append("<p>Blocked: ").append(report.totalBlocked())
          .append(" | Advisory: ").append(report.totalAdvisory())
          .append("</p>\n");
        sb.append("<table><thead><tr>");
        sb.append("<th>Timestamp</th><th>Sender</th><th>Mode</th><th>Action</th>");
        sb.append("</tr></thead><tbody>\n");
        for (ViolationEntry v : report.violations()) {
            sb.append("<tr>");
            sb.append("<td>").append(v.occurredAt() != null ? v.occurredAt().toString() : "").append("</td>");
            sb.append("<td>").append(esc(v.sender())).append("</td>");
            sb.append("<td>").append(esc(v.enforcementMode())).append("</td>");
            sb.append("<td>").append(esc(v.action())).append("</td>");
            sb.append("</tr>\n");
        }
        sb.append("</tbody></table>\n");
        sb.append(footer());
        return sb.toString();
    }


    private String renderJudgmentAttribution(JudgmentAttributionReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append(header("Judgment Attribution Report — " + esc(report.judgmentId())));
        sb.append("<p>Type: ").append(esc(report.judgmentType()))
          .append(" | Outcome: ").append(esc(report.verificationOutcome()))
          .append(" | Duration: ").append(report.totalDurationMs()).append("ms</p>");
        sb.append("<h2>Judgment Events</h2>");
        sb.append("<table><tr><th>Event</th><th>Actor</th><th>Time</th><th>Evidence Quality</th><th>Outcome</th><th>Trust</th></tr>");
        for (var e : report.events()) {
            sb.append("<tr>");
            sb.append("<td>").append(esc(e.eventKind())).append("</td>");
            sb.append("<td>").append(esc(e.actorId())).append("</td>");
            sb.append("<td>").append(e.occurredAt() != null ? esc(e.occurredAt().toString()) : "").append("</td>");
            sb.append("<td>").append(e.evidenceQuality() != null ? e.evidenceQuality() : "").append("</td>");
            sb.append("<td>").append(e.verificationOutcome() != null ? esc(e.verificationOutcome()) : "").append("</td>");
            sb.append("<td>").append(e.trustScoreAtTime() != null ? e.trustScoreAtTime() : "").append("</td>");
            sb.append("</tr>");
        }
        sb.append("</table>");
        sb.append(footer());
        return sb.toString();
    }

    private String renderJudgmentFulfillment(JudgmentFulfillmentReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append(header("Judgment Fulfillment Report"));
        sb.append("<p>Period: ").append(esc(report.from().toString()))
          .append(" to ").append(esc(report.to().toString())).append("</p>");
        sb.append("<p>Total: ").append(report.totalJudgments())
          .append(" | Accepted: ").append(report.accepted())
          .append(" | Rejected: ").append(report.rejected())
          .append(" | Escalated: ").append(report.escalated())
          .append(" | Pending: ").append(report.pending()).append("</p>");
        sb.append("<h2>By Type</h2>");
        sb.append("<table><tr><th>Type</th><th>Total</th><th>Accepted</th><th>Rejected</th><th>Escalated</th><th>Pending</th><th>Rate</th></tr>");
        for (var t : report.byType()) {
            sb.append("<tr>");
            sb.append("<td>").append(esc(t.judgmentType())).append("</td>");
            sb.append("<td>").append(t.total()).append("</td>");
            sb.append("<td>").append(t.accepted()).append("</td>");
            sb.append("<td>").append(t.rejected()).append("</td>");
            sb.append("<td>").append(t.escalated()).append("</td>");
            sb.append("<td>").append(t.pending()).append("</td>");
            sb.append("<td>").append(String.format("%.1f%%", t.acceptanceRate() * 100)).append("</td>");
            sb.append("</tr>");
        }
        sb.append("</table>");
        sb.append("<h2>By Caller</h2>");
        sb.append("<table><tr><th>Actor</th><th>Acceptance Rate</th><th>Avg Response (ms)</th><th>Avg Quality</th><th>Trust</th></tr>");
        for (var c : report.byCaller()) {
            sb.append("<tr>");
            sb.append("<td>").append(esc(c.actorId())).append("</td>");
            sb.append("<td>").append(String.format("%.1f%%", c.acceptanceRate() * 100)).append("</td>");
            sb.append("<td>").append(String.format("%.0f", c.averageResponseTimeMs())).append("</td>");
            sb.append("<td>").append(String.format("%.2f", c.averageEvidenceQuality())).append("</td>");
            sb.append("<td>").append(c.currentTrustScore() != null ? String.format("%.2f", c.currentTrustScore()) : "").append("</td>");
            sb.append("</tr>");
        }
        sb.append("</table>");
        sb.append(footer());
        return sb.toString();
    }

    private String renderGeneric(Object report) {
        StringBuilder sb = new StringBuilder();
        sb.append(header("Compliance Report"));
        sb.append("<pre>").append(esc(report.toString())).append("</pre>\n");
        sb.append(footer());
        return sb.toString();
    }

    private static String header(String title) {
        return "<!DOCTYPE html>\n<html><head><meta charset=\"UTF-8\">\n<title>"
                + esc(title) + "</title>\n<style>" + CSS + "</style>\n</head><body>\n<h1>"
                + esc(title) + "</h1>\n";
    }

    private static String footer() {
        return "</body></html>";
    }

    private static String esc(String value) {
        if (value == null) {return "";}
        return value.replace("&", "&amp;").replace("<", "&lt;")
                    .replace(">", "&gt;").replace("\"", "&quot;");
    }
}
