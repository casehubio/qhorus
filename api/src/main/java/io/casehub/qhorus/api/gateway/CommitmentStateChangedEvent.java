package io.casehub.qhorus.api.gateway;

import io.casehub.qhorus.api.message.Commitment;
import io.casehub.qhorus.api.message.CommitmentState;

import java.util.UUID;

public record CommitmentStateChangedEvent(
        UUID channelId,
        Commitment commitment,
        CommitmentState previousState) {}
