package io.casehub.qhorus.slack;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import io.quarkus.hibernate.orm.PersistenceUnit;

@ApplicationScoped
public class SlackThreadCacheStore {

    @Inject
    @PersistenceUnit("qhorus")
    EntityManager em;

    /** Forward lookup: (channelId, correlationId) → threadTs. Used in post(). */
    public Optional<String> findThreadTs(UUID channelId, String correlationId) {
        return em.createQuery(
                "SELECT c.threadTs FROM SlackThreadCache c WHERE c.id.channelId = :ch AND c.id.correlationId = :corr",
                String.class)
                .setParameter("ch", channelId)
                .setParameter("corr", correlationId)
                .getResultStream().findFirst();
    }

    /** Reverse lookup: (channelId, threadTs) → correlationId. Used for inbound thread-reply routing. */
    public Optional<String> findCorrelationId(UUID channelId, String threadTs) {
        return em.createQuery(
                "SELECT c.id.correlationId FROM SlackThreadCache c WHERE c.id.channelId = :ch AND c.threadTs = :ts",
                String.class)
                .setParameter("ch", channelId)
                .setParameter("ts", threadTs)
                .getResultStream().findFirst();
    }

    /** Bulk load all entries for a channel — used at channel init to populate in-memory cache. */
    public List<SlackThreadCache> findByChannelId(UUID channelId) {
        return em.createQuery(
                "FROM SlackThreadCache c WHERE c.id.channelId = :ch",
                SlackThreadCache.class)
                .setParameter("ch", channelId)
                .getResultList();
    }

    @Transactional
    public void save(UUID channelId, String correlationId, String threadTs) {
        SlackThreadCache entry = new SlackThreadCache();
        entry.id = new SlackThreadCacheId(channelId, correlationId);
        entry.threadTs = threadTs;
        entry.createdAt = Instant.now();
        em.merge(entry);
    }

    /** Evict on terminal commitment state (DONE / FAILURE / DECLINE). */
    @Transactional
    public void delete(UUID channelId, String correlationId) {
        em.createQuery(
                "DELETE FROM SlackThreadCache c WHERE c.id.channelId = :ch AND c.id.correlationId = :corr")
                .setParameter("ch", channelId)
                .setParameter("corr", correlationId)
                .executeUpdate();
    }

    /** Channel deletion cleanup — removes all thread cache rows for a channel. */
    @Transactional
    public void deleteAllByChannelId(UUID channelId) {
        em.createQuery("DELETE FROM SlackThreadCache c WHERE c.id.channelId = :ch")
                .setParameter("ch", channelId)
                .executeUpdate();
    }

    /** TTL eviction — removes entries older than threshold. Returns row count deleted. */
    @Transactional
    public int deleteOlderThan(Instant threshold) {
        return em.createQuery("DELETE FROM SlackThreadCache c WHERE c.createdAt < :threshold")
                .setParameter("threshold", threshold)
                .executeUpdate();
    }
}
