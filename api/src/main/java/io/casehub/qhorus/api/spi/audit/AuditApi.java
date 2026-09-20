package io.casehub.qhorus.api.spi.audit;

import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.qhorus.api.audit.AttestationSummary;
import io.casehub.qhorus.api.audit.CausalChainEntry;
import io.casehub.qhorus.api.audit.CausalGraph;
import io.casehub.qhorus.api.audit.LedgerEntryView;
import io.casehub.qhorus.api.audit.ObligationChainSummary;
import io.casehub.qhorus.api.audit.ObligationStats;
import io.casehub.qhorus.api.audit.PeerReviewResult;
import io.casehub.qhorus.api.audit.StalledObligation;
import io.casehub.qhorus.api.audit.TelemetrySummary;
import io.casehub.qhorus.api.audit.TimelineEntry;

import java.util.List;
import java.util.UUID;

@McpDomain("audit")
public interface AuditApi {

    @PlatformQuery("Query the immutable audit ledger for a channel")
    List<LedgerEntryView> ledgerEntries(UUID channelId, String typeFilter,
            String sender, String since, Long afterId, String correlationId,
            String sort, Integer limit);

    @PlatformQuery("Return computed enrichment for an obligation by correlation ID")
    ObligationChainSummary obligationChain(UUID channelId, String correlationId);

    @PlatformQuery("Walk causedByEntryId links from a ledger entry to root")
    List<CausalChainEntry> causalChain(UUID channelId, UUID ledgerEntryId);

    @PlatformQuery("Build a cross-channel causal graph for a correlation ID")
    CausalGraph causalGraph(String correlationId, Integer limit);

    @PlatformQuery("Render a causal graph as readable indented text")
    String renderedCausalGraph(String correlationId, Integer limit);

    @PlatformQuery("List COMMAND entries with no terminal resolution older than threshold")
    List<StalledObligation> stalledObligations(UUID channelId,
            Integer olderThanSeconds);

    @PlatformQuery("Return obligation outcome statistics for a channel")
    ObligationStats obligationStats(UUID channelId);

    @PlatformQuery("Aggregate EVENT telemetry grouped by tool name")
    TelemetrySummary telemetrySummary(UUID channelId, String since);

    @PlatformQuery("Return messages and EVENTs interleaved chronologically")
    List<TimelineEntry> channelTimeline(UUID channelId, Long afterId,
            Integer limit);

    @PlatformQuery("Return all ledger entries across channels for a correlation ID")
    List<LedgerEntryView> obligationActivity(String correlationId, Integer limit);

    @PlatformQuery("List all attestations on a ledger entry")
    List<AttestationSummary> attestations(UUID entryId);

    @PlatformMutation("Record a peer attestation (ENDORSED or CHALLENGED)")
    AttestationSummary attest(UUID entryId, String verdict, String evidence);

    @PlatformMutation("Send peer review QUERYs to reviewers for a ledger entry")
    PeerReviewResult requestPeerReview(UUID entryId, List<String> reviewerIds,
            UUID channelId);
}
