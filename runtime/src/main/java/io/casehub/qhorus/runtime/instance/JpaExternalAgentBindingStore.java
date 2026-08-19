package io.casehub.qhorus.runtime.instance;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import io.casehub.qhorus.api.instance.ExternalAgentBinding;
import io.casehub.qhorus.api.store.ExternalAgentBindingStore;
import io.quarkus.hibernate.orm.PersistenceUnit;

@ApplicationScoped
public class JpaExternalAgentBindingStore implements ExternalAgentBindingStore {

    @Inject
    @PersistenceUnit("qhorus")
    EntityManager em;

    @Override
    @Transactional
    public void put(ExternalAgentBinding binding) {
        ExternalAgentBindingEntity entity = ExternalAgentBindingEntity.fromDomain(binding);
        em.merge(entity);
    }

    @Override
    public Optional<ExternalAgentBinding> findByInstanceId(String instanceId) {
        return em.createQuery(
                "SELECT e FROM ExternalAgentBinding e WHERE e.instanceId = :instanceId",
                ExternalAgentBindingEntity.class)
            .setParameter("instanceId", instanceId)
            .getResultStream()
            .findFirst()
            .map(ExternalAgentBindingEntity::toDomain);
    }

    @Override
    public List<ExternalAgentBinding> findAll() {
        return em.createQuery("SELECT e FROM ExternalAgentBinding e", ExternalAgentBindingEntity.class)
            .getResultList()
            .stream()
            .map(ExternalAgentBindingEntity::toDomain)
            .toList();
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        em.createQuery("DELETE FROM ExternalAgentBinding e WHERE e.id = :id")
            .setParameter("id", id)
            .executeUpdate();
    }

    @Override
    @Transactional
    public void deleteByInstanceId(String instanceId) {
        em.createQuery("DELETE FROM ExternalAgentBinding e WHERE e.instanceId = :instanceId")
            .setParameter("instanceId", instanceId)
            .executeUpdate();
    }
}
