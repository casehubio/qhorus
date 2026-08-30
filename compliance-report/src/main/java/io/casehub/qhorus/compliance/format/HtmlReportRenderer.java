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

    private static final String PDF_PAGE_CSS = """
            @page {
                margin: 2cm;
                @top-center {
                    content: element(running-header);
                }
                @bottom-left {
                    content: element(running-footer);
                }
                @bottom-right {
                    content: "Page " counter(page) " of " counter(pages);
                    font-size: 9pt;
                    font-family: 'Liberation Sans', sans-serif;
                }
            }
            .pdf-header {
                position: running(running-header);
                font-size: 9pt;
                font-family: 'Liberation Sans', sans-serif;
                color: #666;
                text-align: center;
            }
            .pdf-footer {
                position: running(running-footer);
                font-size: 8pt;
                font-family: 'Liberation Sans', sans-serif;
                color: #999;
            }
            code, tt, .mono {
                font-family: 'Liberation Mono', monospace;
            }
            """;

    @Override
    public String contentType() {
        return "text/html";
    }

    @Override
    public byte[] render(Object report) {
        String title = titleFor(report);
        String body = bodyFor(report);
        String html = header(title) + "<h1>" + esc(title) + "</h1>\n" + body + footer();
        return html.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public boolean supports(ReportFormat format) {
        return format == ReportFormat.HTML;
    }

    public String renderForPdf(Object report, PdfDocumentMetadata metadata) {
        String title = titleFor(report);
        String body = bodyFor(report);
        return pdfHeader(title, metadata) + "<h1>" + esc(title) + "</h1>\n" + body + footer();
    }

    private String titleFor(Object report) {
        return switch (report) {
            case AttributionReport r -> "Attribution Report — " + r.correlationId();
            case ObligationReport ignored -> "Obligation Fulfillment Report";
            case ViolationReport r -> "Violation Report — " + r.channelName();
            case JudgmentAttributionReport r -> "Judgment Attribution Report — " + r.judgmentId();
            case JudgmentFulfillmentReport ignored -> "Judgment Fulfillment Report";
            default -> "Compliance Report";
        };
    }

    private String bodyFor(Object report) {
        return switch (report) {
            case AttributionReport r -> bodyAttribution(r);
            case ObligationReport r -> bodyObligation(r);
            case ViolationReport r -> bodyViolation(r);
            case JudgmentAttributionReport r -> bodyJudgmentAttribution(r);
            case JudgmentFulfillmentReport r -> bodyJudgmentFulfillment(r);
            default -> "<pre>" + esc(report.toString()) + "</pre>\n";
        };
    }

    private String bodyAttribution(AttributionReport report) {
        StringBuilder sb = new StringBuilder();
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
        return sb.toString();
    }

    private String bodyObligation(ObligationReport report) {
        StringBuilder sb = new StringBuilder();
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
        return sb.toString();
    }

    private String bodyViolation(ViolationReport report) {
        StringBuilder sb = new StringBuilder();
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
        return sb.toString();
    }

    private String bodyJudgmentAttribution(JudgmentAttributionReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("<p>Type: ").append(esc(report.judgmentType()))
          .append(" | Outcome: ").append(esc(report.verificationOutcome()))
          .append(" | Duration: ").append(report.totalDurationMs()).append("ms</p>");
        sb.append("<h2>Judgment Events</h2>");
        sb.append("<table><tr><th>Event</th><th>Actor</th><th>Time</th><th>Evidence Quality</th><th>Outcome</th><th>Trust</th><th>Reasoning</th></tr>");
        for (var e : report.events()) {
            sb.append("<tr>");
            sb.append("<td>").append(esc(e.eventKind())).append("</td>");
            sb.append("<td>").append(esc(e.actorId())).append("</td>");
            sb.append("<td>").append(e.occurredAt() != null ? esc(e.occurredAt().toString()) : "").append("</td>");
            sb.append("<td>").append(e.evidenceQuality() != null ? e.evidenceQuality() : "").append("</td>");
            sb.append("<td>").append(e.verificationOutcome() != null ? esc(e.verificationOutcome()) : "").append("</td>");
            sb.append("<td>").append(e.trustScoreAtTime() != null ? e.trustScoreAtTime() : "").append("</td>");
            sb.append("<td>").append(e.reasoning() != null ? esc(e.reasoning()) : "").append("</td>");
            sb.append("</tr>");
        }
        sb.append("</table>");
        return sb.toString();
    }

    private String bodyJudgmentFulfillment(JudgmentFulfillmentReport report) {
        StringBuilder sb = new StringBuilder();
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
        return sb.toString();
    }

    private static String header(String title) {
        return "<!DOCTYPE html>\n<html><head><meta charset=\"UTF-8\"/>\n<title>"
                + esc(title) + "</title>\n<style>" + CSS + "</style>\n</head><body>\n";
    }

    private static String pdfHeader(String title, PdfDocumentMetadata metadata) {
        String reportType = metadata.reportType() != null ? esc(metadata.reportType()) : "";
        String timestamp = metadata.createdAt() != null ? metadata.createdAt().toString() : "";
        String tenant = metadata.tenancyId() != null ? "Tenant: " + esc(metadata.tenancyId()) : "";

        return "<!DOCTYPE html>\n<html><head><meta charset=\"UTF-8\"/>\n<title>"
                + esc(title) + "</title>\n<style>" + CSS + "\n" + PDF_PAGE_CSS
                + "</style>\n</head><body>\n"
                + "<div class=\"pdf-header\">" + reportType + " — " + timestamp + "</div>\n"
                + "<div class=\"pdf-footer\">" + tenant + "</div>\n";
    }

    private static String footer() {
        return "</body></html>";
    }

    static String esc(String value) {
        if (value == null) {return "";}
        return value.replace("&", "&amp;").replace("<", "&lt;")
                    .replace(">", "&gt;").replace("\"", "&quot;");
    }
}
