package io.casehub.qhorus.api.store;

import io.casehub.qhorus.api.message.Reaction;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface ReactionReader {

    List<Reaction> findByMessage(Long messageId);

    Map<Long, List<Reaction>> findByMessages(Collection<Long> messageIds);
}
