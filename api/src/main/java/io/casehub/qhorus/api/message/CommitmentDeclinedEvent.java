package io.casehub.qhorus.api.message;

import java.util.UUID;

/**
 * CDI event fired when a commitment transitions to {@link CommitmentState#DECLINED}.
 *
 * <p>Consumers observe this for scope-calibration signals — e.g. trust dimension tracking
 * ("Does the agent correctly DECLINE work outside its capability?").
 *
 * <p>Refs qhorus#251.
 *
 * @param commitmentId  the UUID of the declined commitment
 * @param correlationId correlationId of the original COMMAND/QUERY
 * @param channelId     channel on which the commitment was tracked
 * @param obligor       the agent that declined (sender of the DECLINE message)
 * @param requester     the original requester (sender of the COMMAND/QUERY)
 */
public record CommitmentDeclinedEvent(
        UUID commitmentId,
        String correlationId,
        UUID channelId,
        String obligor,
        String requester) {}
