package io.casehub.qhorus.playbook.bridge;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.quarkus.hibernate.orm.PersistenceUnit;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class JpaProtocolDefinitionStore implements ProtocolDefinitionStore {

    @Inject
    @PersistenceUnit("qhorus")
    EntityManager em;
    @Inject
    CurrentPrincipal currentPrincipal;


    @Override
    public Optional<ProtocolDefinition> findByChannelAndProtocol(UUID channelId, String protocolName) {
        return em.createQuery(
                         "FROM ProtocolDefinition WHERE channelId = :cid AND protocolName = :name AND tenancyId = :tid",
                         ProtocolDefinitionEntity.class)
                 .setParameter("cid", channelId)
                 .setParameter("name", protocolName)
                 .setParameter("tid", currentPrincipal.tenancyId())
                 .getResultStream()
                 .findFirst()
                 .map(ProtocolDefinitionEntity::toDomain);
    }

    @Override
    public List<ProtocolDefinition> findByChannel(UUID channelId) {
        return em.createQuery(
                         "FROM ProtocolDefinition WHERE channelId = :cid AND tenancyId = :tid",
                         ProtocolDefinitionEntity.class)
                 .setParameter("cid", channelId)
                 .setParameter("tid", currentPrincipal.tenancyId())
                 .getResultList()
                 .stream()
                 .map(ProtocolDefinitionEntity::toDomain)
                 .toList();
    }

    @Override
    public boolean existsByProtocolName(String protocolName) {
        return em.createQuery(
                         "SELECT COUNT(e) FROM ProtocolDefinition e WHERE e.protocolName = :name AND e.tenancyId = :tid",
                         Long.class)
                 .setParameter("name", protocolName)
                 .setParameter("tid", currentPrincipal.tenancyId())
                 .getSingleResult() > 0;
    }

    @Override
    @Transactional
    public void put(ProtocolDefinition definition) {
        ProtocolDefinitionEntity existing = em.createQuery(
                                                      "FROM ProtocolDefinition WHERE channelId = :cid AND protocolName = :name AND tenancyId = :tid",
                                                      ProtocolDefinitionEntity.class)
                                              .setParameter("cid", definition.channelId())
                                              .setParameter("name", definition.protocolName())
                                              .setParameter("tid", currentPrincipal.tenancyId())
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
        em.createQuery("DELETE FROM ProtocolDefinition WHERE channelId = :cid AND protocolName = :name AND tenancyId = :tid")
          .setParameter("cid", channelId)
          .setParameter("name", protocolName)
          .setParameter("tid", currentPrincipal.tenancyId())
          .executeUpdate();
    }

    @Override
    @Transactional
    public void deleteByChannel(UUID channelId) {
        em.createQuery("DELETE FROM ProtocolDefinition WHERE channelId = :cid AND tenancyId = :tid")
          .setParameter("cid", channelId)
          .setParameter("tid", currentPrincipal.tenancyId())
          .executeUpdate();
    }
}
