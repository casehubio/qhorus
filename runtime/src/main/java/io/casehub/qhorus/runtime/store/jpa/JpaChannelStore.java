package io.casehub.qhorus.runtime.store.jpa;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.qhorus.runtime.channel.Channel;
import io.casehub.qhorus.runtime.store.ChannelStore;
import io.casehub.qhorus.runtime.store.query.ChannelQuery;

@ApplicationScoped
public class JpaChannelStore implements ChannelStore {

    @Inject
    CurrentPrincipal currentPrincipal;

    @Override
    @Transactional
    public Channel put(Channel channel) {
        channel.persistAndFlush();
        return channel;
    }

    @Override
    public Optional<Channel> find(UUID id) {
        return Channel.find("id = ?1 AND tenancyId = ?2", id, currentPrincipal.tenancyId())
                .firstResultOptional();
    }

    @Override
    public Optional<Channel> findByName(String name) {
        return Channel.find("name = ?1 AND tenancyId = ?2", name, currentPrincipal.tenancyId())
                .firstResultOptional();
    }

    @Override
    public List<Channel> scan(ChannelQuery q) {
        StringBuilder jpql = new StringBuilder("FROM Channel WHERE tenancyId = ?1");
        List<Object> params = new ArrayList<>();
        params.add(currentPrincipal.tenancyId());
        int idx = 2;

        if (q.paused() != null) {
            jpql.append(" AND paused = ?").append(idx++);
            params.add(q.paused());
        }
        if (q.semantic() != null) {
            jpql.append(" AND semantic = ?").append(idx++);
            params.add(q.semantic());
        }
        if (q.namePattern() != null) {
            jpql.append(" AND name LIKE ?").append(idx++);
            params.add(q.namePattern().replace("*", "%"));
        }
        if (q.namePrefix() != null) {
            jpql.append(" AND name LIKE ?").append(idx++).append(" ESCAPE '!'");
            params.add(escapeLikePrefix(q.namePrefix()) + "%");
        }

        return Channel.list(jpql.toString(), params.toArray());
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Channel.delete("id = ?1 AND tenancyId = ?2", id, currentPrincipal.tenancyId());
    }

    @Override
    @Transactional
    public void updateLastActivity(UUID channelId) {
        Channel.update("lastActivityAt = ?1 WHERE id = ?2", Instant.now(), channelId);
    }

    @Override
    public List<Channel> findByIds(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return Channel.list("id IN ?1 AND tenancyId = ?2", new ArrayList<>(ids), currentPrincipal.tenancyId());
    }

    private static String escapeLikePrefix(String prefix) {
        return prefix.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
