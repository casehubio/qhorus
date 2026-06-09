package io.casehub.qhorus.runtime.store.jpa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;

import io.casehub.qhorus.runtime.channel.Channel;
import io.casehub.qhorus.runtime.store.CrossTenantChannelStore;

/**
 * JPA implementation of {@link CrossTenantChannelStore}.
 * Returns channels across all tenancies with no tenancyId filter applied.
 *
 * <p>Not injected directly — always accessed via {@code @CrossTenant} from
 * {@code CrossTenantProducer}, which enforces the cross-tenant admin guard.
 *
 * <p>Refs #260.
 */
@ApplicationScoped
public class JpaCrossTenantChannelStore implements CrossTenantChannelStore {

    @Override
    public List<Channel> listAll() {
        return Channel.listAll();
    }

    @Override
    public Optional<Channel> findById(UUID id) {
        return Channel.findByIdOptional(id);
    }

    @Override
    public Optional<Channel> findByNameAndTenancy(String name, String tenancyId) {
        return Channel.find("name = ?1 AND tenancyId = ?2", name, tenancyId).firstResultOptional();
    }
}
