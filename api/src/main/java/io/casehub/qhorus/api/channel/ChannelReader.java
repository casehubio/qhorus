package io.casehub.qhorus.api.channel;

import io.casehub.qhorus.api.store.query.ChannelQuery;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChannelReader {

    Optional<Channel> findById(UUID id);

    Optional<Channel> findByName(String name);

    List<Channel> findByNamePrefix(String prefix);

    List<Channel> listAll();

    List<Channel> scan(ChannelQuery query);

    List<Channel> findByIds(Collection<UUID> ids);
}
