package io.casehub.qhorus.cluster;

import io.casehub.qhorus.api.message.ConsumerMessaging;
import io.casehub.qhorus.api.message.DispatchResult;
import io.casehub.qhorus.api.message.Message;
import io.casehub.qhorus.api.message.MessageDispatch;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class RoutingConsumerMessaging implements ConsumerMessaging {

    private final WriteRoutingDecorator router;
    private final ConsumerMessaging queries;

    public RoutingConsumerMessaging(WriteRoutingDecorator router, ConsumerMessaging queries) {
        this.router = router;
        this.queries = queries;
    }

    @Override
    public DispatchResult dispatch(MessageDispatch dispatch) {
        return router.dispatch(dispatch);
    }

    @Override
    public List<Message> history(UUID channelId, long afterId, int limit) {
        return queries.history(channelId, afterId, limit);
    }

    @Override
    public List<Message> history(UUID channelId, long afterId, int limit, boolean includeEvents) {
        return queries.history(channelId, afterId, limit, includeEvents);
    }

    @Override
    public List<Message> historyBySender(UUID channelId, long afterId, int limit,
                                          String sender, boolean includeEvents) {
        return queries.historyBySender(channelId, afterId, limit, sender, includeEvents);
    }

    @Override
    public Optional<Message> findById(Long messageId) {
        return queries.findById(messageId);
    }

    @Override
    public Optional<Message> findByCorrelationId(String correlationId) {
        return queries.findByCorrelationId(correlationId);
    }

    @Override
    public List<Message> findAllByCorrelationId(String correlationId) {
        return queries.findAllByCorrelationId(correlationId);
    }
}
