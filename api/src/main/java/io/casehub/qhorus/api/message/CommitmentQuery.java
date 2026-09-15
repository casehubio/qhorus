package io.casehub.qhorus.api.message;

import java.util.UUID;

public record CommitmentQuery(
    UUID channelId,
    CommitmentState state,
    String obligor,
    String requester,
    Integer offset,
    Integer limit,
    String cursor
) {}
