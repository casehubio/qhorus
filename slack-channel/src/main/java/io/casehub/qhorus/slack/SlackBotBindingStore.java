package io.casehub.qhorus.slack;

import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import io.quarkus.hibernate.orm.PersistenceUnit;

@ApplicationScoped
public class SlackBotBindingStore {

    @Inject
    @PersistenceUnit("qhorus")
    EntityManager em;

    public Optional<SlackBotBinding> findByChannelId(UUID channelId) {
        return em.createQuery(
                "FROM SlackBotBinding b WHERE b.channelId = :id", SlackBotBinding.class)
                .setParameter("id", channelId)
                .getResultStream().findFirst();
    }

    @Transactional
    public void save(SlackBotBinding binding) {
        em.merge(binding);
    }

    @Transactional
    public void deleteByChannelId(UUID channelId) {
        em.createQuery("DELETE FROM SlackBotBinding b WHERE b.channelId = :id")
                .setParameter("id", channelId)
                .executeUpdate();
    }
}
