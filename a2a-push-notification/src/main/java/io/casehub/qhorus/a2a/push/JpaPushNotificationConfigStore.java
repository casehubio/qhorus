package io.casehub.qhorus.a2a.push;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import io.casehub.qhorus.api.a2a.PushNotificationConfig;
import io.casehub.qhorus.api.store.CrossTenantPushNotificationConfigStore;
import io.casehub.qhorus.api.store.PushNotificationConfigStore;
import io.quarkus.hibernate.orm.PersistenceUnit;

@ApplicationScoped
public class JpaPushNotificationConfigStore
        implements PushNotificationConfigStore, CrossTenantPushNotificationConfigStore {

    @Inject
    @PersistenceUnit("qhorus")
    EntityManager em;

    @Override
    @Transactional
    public void put(PushNotificationConfig config) {
        em.merge(PushNotificationConfigEntity.fromDomain(config));
    }

    @Override
    public Optional<PushNotificationConfig> findById(UUID id) {
        PushNotificationConfigEntity e = em.find(PushNotificationConfigEntity.class, id);
        return e == null ? Optional.empty() : Optional.of(e.toDomain());
    }

    @Override
    public List<PushNotificationConfig> findByTaskId(String taskId) {
        return em.createQuery(
                "SELECT e FROM PushNotificationConfig e WHERE e.taskId = :taskId",
                PushNotificationConfigEntity.class)
            .setParameter("taskId", taskId)
            .getResultList()
            .stream()
            .map(PushNotificationConfigEntity::toDomain)
            .toList();
    }

    @Override
    public List<PushNotificationConfig> findByChannelId(UUID channelId) {
        return em.createQuery(
                "SELECT e FROM PushNotificationConfig e WHERE e.channelId = :channelId",
                PushNotificationConfigEntity.class)
            .setParameter("channelId", channelId)
            .getResultList()
            .stream()
            .map(PushNotificationConfigEntity::toDomain)
            .toList();
    }

    @Override
    public Set<String> activeTaskIds() {
        return em.createQuery(
                "SELECT DISTINCT e.taskId FROM PushNotificationConfig e", String.class)
            .getResultList()
            .stream()
            .collect(Collectors.toSet());
    }

    @Override
    public List<PushNotificationConfig> findExpired(Instant threshold) {
        return em.createQuery(
                "SELECT e FROM PushNotificationConfig e WHERE COALESCE(e.lastPushedAt, e.createdAt) < :threshold",
                PushNotificationConfigEntity.class)
            .setParameter("threshold", threshold)
            .getResultList()
            .stream()
            .map(PushNotificationConfigEntity::toDomain)
            .toList();
    }

    @Override
    @Transactional
    public void updateLastPushedAt(UUID id, Instant pushedAt) {
        PushNotificationConfigEntity e = em.find(PushNotificationConfigEntity.class, id);
        if (e != null) {
            e.lastPushedAt = pushedAt;
            em.merge(e);
        }
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        em.createQuery("DELETE FROM PushNotificationConfig e WHERE e.id = :id")
            .setParameter("id", id)
            .executeUpdate();
    }

    @Override
    @Transactional
    public void deleteByTaskId(String taskId) {
        em.createQuery("DELETE FROM PushNotificationConfig e WHERE e.taskId = :taskId")
            .setParameter("taskId", taskId)
            .executeUpdate();
    }
}
