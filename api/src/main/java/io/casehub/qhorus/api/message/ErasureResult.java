package io.casehub.qhorus.api.message;

import java.util.UUID;

public record ErasureResult(
    UUID erasedEntryId,
    Long erasedMessageId,
    UUID channelId,
    UUID tombstoneEntryId
) {}
