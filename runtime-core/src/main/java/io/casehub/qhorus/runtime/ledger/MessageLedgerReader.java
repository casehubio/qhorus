package io.casehub.qhorus.runtime.ledger;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MessageLedgerReader {

    List<MessageLedgerEntry> findAncestorChainCrossChannel(UUID entryId, String tenancyId);

    List<MessageLedgerEntry> findByCorrelationIdAcrossChannels(String correlationId, int limit, String tenancyId);

    Optional<MessageLedgerEntry> findLatestEntryByActor(String actorId);
}
