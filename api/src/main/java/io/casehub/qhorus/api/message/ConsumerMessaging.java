package io.casehub.qhorus.api.message;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConsumerMessaging extends MessageDispatcher {

    List<Message> history(UUID channelId, long afterId, int limit);

    List<Message> history(UUID channelId, long afterId, int limit, boolean includeEvents);

    List<Message> historyBySender(UUID channelId, long afterId, int limit,
                                   String sender, boolean includeEvents);

    Optional<Message> findById(Long messageId);

    Optional<Message> findByCorrelationId(String correlationId);

    List<Message> findAllByCorrelationId(String correlationId);
}
