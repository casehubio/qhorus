package io.casehub.qhorus.webhook;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.quarkus.hibernate.orm.PersistenceUnit;

@ApplicationScoped
public class WebhookRegistrationStore {

    @Inject
    @PersistenceUnit("qhorus")
    EntityManager em;

    @Inject
    CurrentPrincipal currentPrincipal;

    public Optional<WebhookRegistrationEntity> findById(UUID id) {
        return em.createQuery(
                        "FROM WebhookRegistrationEntity e WHERE e.id = :id AND e.tenancyId = :tid",
                        WebhookRegistrationEntity.class)
                .setParameter("id", id)
                .setParameter("tid", currentPrincipal.tenancyId())
                .getResultStream().findFirst();
    }

    public List<WebhookRegistrationEntity> findByChannelId(UUID channelId) {
        return em.createQuery(
                        "FROM WebhookRegistrationEntity e WHERE e.channelId = :cid AND e.tenancyId = :tid",
                        WebhookRegistrationEntity.class)
                .setParameter("cid", channelId)
                .setParameter("tid", currentPrincipal.tenancyId())
                .getResultList();
    }

    public List<WebhookRegistrationEntity> findGlobal() {
        return em.createQuery(
                        "FROM WebhookRegistrationEntity e WHERE e.channelId IS NULL AND e.tenancyId = :tid",
                        WebhookRegistrationEntity.class)
                .setParameter("tid", currentPrincipal.tenancyId())
                .getResultList();
    }

    /** Cross-tenant — used only by startup reload. */
    public List<WebhookRegistrationEntity> findAll() {
        return em.createQuery(
                        "FROM WebhookRegistrationEntity e",
                        WebhookRegistrationEntity.class)
                .getResultList();
    }

    @Transactional
    public void save(WebhookRegistrationEntity entity) {
        em.merge(entity);
    }

    @Transactional
    public boolean delete(UUID id) {
        return em.createQuery("DELETE FROM WebhookRegistrationEntity e WHERE e.id = :id AND e.tenancyId = :tid")
                .setParameter("id", id)
                .setParameter("tid", currentPrincipal.tenancyId())
                .executeUpdate() > 0;
    }

    /** Cross-tenant — channel deletion is authoritative. */
    @Transactional
    public void deleteByChannelId(UUID channelId) {
        em.createQuery("DELETE FROM WebhookRegistrationEntity e WHERE e.channelId = :cid")
                .setParameter("cid", channelId)
                .executeUpdate();
    }
}
