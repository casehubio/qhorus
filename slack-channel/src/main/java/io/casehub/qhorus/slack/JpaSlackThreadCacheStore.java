package io.casehub.qhorus.slack;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import io.casehub.qhorus.slack.core.SlackThreadCache;
import io.casehub.qhorus.slack.core.SlackThreadCacheId;
import io.casehub.qhorus.slack.core.SlackThreadCacheStore;
import io.quarkus.hibernate.orm.PersistenceUnit;

@ApplicationScoped
public class JpaSlackThreadCacheStore implements SlackThreadCacheStore {

    @Inject
    @PersistenceUnit("qhorus")
    EntityManager em;

    @Override
    public Optional<String> findThreadTs(UUID channelId, String correlationId) {
        return em.createQuery(
                "SELECT c.threadTs FROM SlackThreadCache c WHERE c.id.channelId = :ch AND c.id.correlationId = :corr",
                String.class)
                .setParameter("ch", channelId)
                .setParameter("corr", correlationId)
                .getResultStream().findFirst();
    }

    @Override
    public Optional<String> findCorrelationId(UUID channelId, String threadTs) {
        return em.createQuery(
                "SELECT c.id.correlationId FROM SlackThreadCache c WHERE c.id.channelId = :ch AND c.threadTs = :ts",
                String.class)
                .setParameter("ch", channelId)
                .setParameter("ts", threadTs)
                .getResultStream().findFirst();
    }

    @Override
    public List<SlackThreadCache> findByChannelId(UUID channelId) {
        return em.createQuery(
                "FROM SlackThreadCache c WHERE c.id.channelId = :ch",
                SlackThreadCache.class)
                .setParameter("ch", channelId)
                .getResultList();
    }

    @Override
    @Transactional
    public void save(UUID channelId, String correlationId, String threadTs) {
        SlackThreadCache entry = new SlackThreadCache();
        entry.id = new SlackThreadCacheId(channelId, correlationId);
        entry.threadTs = threadTs;
        entry.createdAt = Instant.now();
        em.merge(entry);
    }

    @Override
    @Transactional
    public void delete(UUID channelId, String correlationId) {
        em.createQuery(
                "DELETE FROM SlackThreadCache c WHERE c.id.channelId = :ch AND c.id.correlationId = :corr")
                .setParameter("ch", channelId)
                .setParameter("corr", correlationId)
                .executeUpdate();
    }

    @Override
    @Transactional
    public void deleteAllByChannelId(UUID channelId) {
        em.createQuery("DELETE FROM SlackThreadCache c WHERE c.id.channelId = :ch")
                .setParameter("ch", channelId)
                .executeUpdate();
    }

    @Override
    @Transactional
    public int deleteOlderThan(Instant threshold) {
        return em.createQuery("DELETE FROM SlackThreadCache c WHERE c.createdAt < :threshold")
                .setParameter("threshold", threshold)
                .executeUpdate();
    }
}
