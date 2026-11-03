package io.casehub.qhorus.api.channel;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VisibilityManager {

    FindOrCreateResult getOrCreateDirect(String participantA, String participantB);

    List<Channel> findDirectChannelsFor(String participantId);

    Channel createGroupChannel(UUID groupId, String groupName,
                               List<String> memberIds, String coordinatorId);

    Optional<Channel> findGroupChannel(UUID groupId);
}
