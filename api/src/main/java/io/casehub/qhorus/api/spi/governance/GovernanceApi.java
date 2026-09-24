package io.casehub.qhorus.api.spi.governance;

import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.qhorus.api.message.Commitment;
import io.casehub.qhorus.api.message.CommitmentPage;
import io.casehub.qhorus.api.message.CommitmentQuery;
import io.casehub.qhorus.api.watchdog.Watchdog;

import java.util.List;
import java.util.UUID;

@McpDomain(value = "qhorus/governance", app = "qhorus", summary = "Channel governance — policies, voting, dispute resolution")
public interface GovernanceApi {

    @PlatformQuery("List commitments matching filter criteria")
    CommitmentPage commitments(CommitmentQuery query);

    @PlatformQuery("List non-terminal commitments across all channels")
    List<Commitment> pendingCommitments();

    @PlatformQuery("List commitments involving a specific agent on a channel")
    List<Commitment> myCommitments(UUID channelId, String sender, String role);

    @PlatformQuery("Get commitment by correlationId")
    Commitment commitment(String correlationId);

    @PlatformQuery("List all registered watchdog conditions")
    List<Watchdog> watchdogs();

    @PlatformMutation("Register a watchdog condition")
    Watchdog registerWatchdog(String conditionType, String targetName,
            Integer thresholdSeconds, Integer thresholdCount,
            Integer similarityPct, String notificationChannel,
            String createdBy, String action);

    @PlatformMutation("Delete a watchdog by ID")
    boolean deleteWatchdog(UUID watchdogId);
}
