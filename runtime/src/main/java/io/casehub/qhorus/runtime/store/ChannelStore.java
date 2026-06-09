package io.casehub.qhorus.runtime.store;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.casehub.qhorus.runtime.channel.Channel;
import io.casehub.qhorus.runtime.store.query.ChannelQuery;

public interface ChannelStore {
    Channel put(Channel channel);

    Optional<Channel> find(UUID id);

    Optional<Channel> findByName(String name);

    List<Channel> scan(ChannelQuery query);

    void delete(UUID id);

    /**
     * Issues a targeted UPDATE setting {@code lastActivityAt = now()} for {@code channelId},
     * filtered by {@code tenancyId} as defense-in-depth against cross-tenant mutation.
     * Does NOT load or re-attach the channel entity — avoids detached-entity issues when
     * the channel was loaded pre-transaction.
     *
     * @param tenancyId the tenant that owns the channel — passed explicitly by the caller
     *                  so this method is safe to call from scheduler/watchdog threads that
     *                  have no request-scoped {@code CurrentPrincipal}
     */
    void updateLastActivity(UUID channelId, String tenancyId);

    /**
     * Batch lookup of channels by ID set. Returns only channels that exist — missing IDs are silently omitted.
     * Empty collection input returns an empty list without querying the store.
     *
     * <p>Default uses N individual {@link #find(UUID)} calls — JPA-backed implementations
     * should override with a single {@code WHERE id IN (:ids)} query.
     */
    default List<Channel> findByIds(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return ids.stream()
                .map(this::find)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }
}
