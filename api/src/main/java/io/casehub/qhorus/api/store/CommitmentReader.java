package io.casehub.qhorus.api.store;

import io.casehub.qhorus.api.message.Commitment;
import io.casehub.qhorus.api.message.CommitmentState;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommitmentReader {

    Optional<Commitment> findById(UUID commitmentId);

    Optional<Commitment> findByCorrelationId(String correlationId);

    List<Commitment> findAllByCorrelationId(String correlationId);

    List<Commitment> findByIds(Collection<UUID> ids);

    List<Commitment> findOpenByObligor(String obligor, UUID channelId);

    List<Commitment> findOpenByRequester(String requester, UUID channelId);

    List<Commitment> findByState(CommitmentState state, UUID channelId);

    List<Commitment> findByChannel(UUID channelId);

    List<Commitment> findOpenByChannelId(UUID channelId);

    List<Commitment> findExpiredBefore(Instant cutoff);

    List<Commitment> findAllOpen();


    List<Commitment> findByObligorInTenancy(String obligor, String tenancyId);

    default List<Commitment> findOpenByObligor(String obligor) {
        if (obligor == null) { return List.of(); }
        return findAllOpen().stream()
                .filter(c -> obligor.equals(c.obligor()))
                .toList();
    }
}
