package io.casehub.qhorus.runtime.store.jpa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;

import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.runtime.message.Message;
import io.casehub.qhorus.runtime.store.CrossTenantMessageStore;
import io.casehub.qhorus.runtime.store.query.MessageQuery;

/**
 * JPA implementation of {@link CrossTenantMessageStore}.
 * Queries messages across all tenancies with no tenancyId filter applied.
 *
 * <p>Not injected directly — always accessed via {@code @CrossTenant} from
 * {@code CrossTenantProducer}, which enforces the cross-tenant admin guard.
 *
 * <p>Refs #260.
 */
@ApplicationScoped
public class JpaCrossTenantMessageStore implements CrossTenantMessageStore {

    @Override
    public List<Message> scan(MessageQuery q) {
        MessageQueryJpql mq = MessageQueryJpql.from(q);
        String jpql = "FROM Message WHERE " + mq.where()
                + (q.descending() ? " ORDER BY id DESC" : " ORDER BY id ASC");

        if (q.limit() != null) {
            return Message.find(jpql, mq.params()).page(0, q.limit()).list();
        }
        return Message.list(jpql, mq.params());
    }

    @Override
    public long count(MessageQuery q) {
        MessageQueryJpql mq = MessageQueryJpql.from(q);
        return Message.count(mq.where(), mq.params());
    }

    @Override
    public int countByChannel(UUID channelId) {
        return (int) Message.count("channelId", channelId);
    }

    @Override
    public List<String> distinctSendersByChannel(UUID channelId, MessageType excludedType) {
        @SuppressWarnings("unchecked")
        List<String> result = Message.getEntityManager()
                .createQuery("SELECT DISTINCT m.sender FROM Message m "
                        + "WHERE m.channelId = ?1 AND m.messageType != ?2 ORDER BY m.sender")
                .setParameter(1, channelId)
                .setParameter(2, excludedType)
                .getResultList();
        return result;
    }

    @Override
    public Optional<Message> findLastMessage(UUID channelId) {
        return Message.<Message>find("channelId = ?1 ORDER BY id DESC", channelId)
                .page(0, 1)
                .firstResultOptional();
    }
}
