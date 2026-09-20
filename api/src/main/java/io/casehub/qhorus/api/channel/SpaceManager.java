package io.casehub.qhorus.api.channel;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpaceManager {

    Space create(SpaceCreateRequest request);

    Optional<Space> findById(UUID id);

    Optional<Space> findByName(String name);

    List<Space> listChildren(UUID parentSpaceId);

    List<Space> listRoots();

    List<Channel> listChannels(UUID spaceId);

    void delete(UUID spaceId);

    Space rename(UUID spaceId, String newName);

    Space updateDescription(UUID spaceId, String description);

    Space moveSpace(UUID spaceId, UUID newParentSpaceId);

    Channel moveChannelToSpace(UUID channelId, UUID spaceId);
}
