package io.casehub.qhorus.playbook.bridge;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import io.quarkus.hibernate.orm.PersistenceUnit;

@ApplicationScoped
public class JpaProtocolDefinitionStore implements ProtocolDefinitionStore {

    @Inject
    @PersistenceUnit("qhorus")
    EntityManager em;

    @Override
    public Optional<ProtocolDefinition> findByChannelAndProtocol(UUID channelId, String protocolName) {
        return em.createQuery(
                        "FROM ProtocolDefinition WHERE channelId = :cid AND protocolName = :name",
                        ProtocolDefinitionEntity.class)
                .setParameter("cid", channelId)
                .setParameter("name", protocolName)
                .getResultStream()
                .findFirst()
                .map(ProtocolDefinitionEntity::toDomain);
    }

    @Override
    public List<ProtocolDefinition> findByChannel(UUID channelId) {
        return em.createQuery(
                        "FROM ProtocolDefinition WHERE channelId = :cid",
                        ProtocolDefinitionEntity.class)
                .setParameter("cid", channelId)
                .getResultList()
                .stream()
                .map(ProtocolDefinitionEntity::toDomain)
                .toList();
    }

    @Override
    public boolean existsByProtocolName(String protocolName) {
        return em.createQuery(
                        "SELECT COUNT(e) FROM ProtocolDefinition e WHERE e.protocolName = :name",
                        Long.class)
                .setParameter("name", protocolName)
                .getSingleResult() > 0;
    }

    @Override
    @Transactional
    public void put(ProtocolDefinition definition) {
        ProtocolDefinitionEntity existing = em.createQuery(
                        "FROM ProtocolDefinition WHERE channelId = :cid AND protocolName = :name",
                        ProtocolDefinitionEntity.class)
                .setParameter("cid", definition.channelId())
                .setParameter("name", definition.protocolName())
                .getResultStream()
                .findFirst()
                .orElse(null);

        if (existing != null) {
            existing.yamlContent = definition.yamlContent();
        } else {
            em.persist(ProtocolDefinitionEntity.fromDomain(definition));
        }
    }

    @Override
    @Transactional
    public void delete(UUID channelId, String protocolName) {
        em.createQuery("DELETE FROM ProtocolDefinition WHERE channelId = :cid AND protocolName = :name")
                .setParameter("cid", channelId)
                .setParameter("name", protocolName)
                .executeUpdate();
    }

    @Override
    @Transactional
    public void deleteByChannel(UUID channelId) {
        em.createQuery("DELETE FROM ProtocolDefinition WHERE channelId = :cid")
                .setParameter("cid", channelId)
                .executeUpdate();
    }
}
