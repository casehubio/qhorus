package io.casehub.qhorus.api.store;

import io.casehub.qhorus.api.message.Commitment;

import java.time.Instant;
import java.util.UUID;

public interface CommitmentStore extends CommitmentReader {

    Commitment save(Commitment commitment);

    void deleteById(UUID commitmentId);

    /**
     * Delete all commitments for the given channel. Called by delete_channel before channel deletion.
     */
    long deleteAll(UUID channelId);

    long deleteExpiredBefore(Instant cutoff);
}
