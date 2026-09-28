package io.casehub.qhorus.runtime.api.core;

import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.channel.Space;
import io.casehub.qhorus.api.channel.SpaceCreateRequest;
import io.casehub.qhorus.runtime.channel.SpaceService;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class SpaceCore {

    private final SpaceService spaceService;

    public SpaceCore(SpaceService spaceService) {
        this.spaceService = spaceService;
    }

    public List<Space> listRoots() {
        return spaceService.listRoots();
    }

    public Optional<Space> get(String id) {
        return spaceService.findById(parseUuid(id));
    }

    public List<Space> children(String id) {
        return spaceService.listChildren(parseUuid(id));
    }

    public Space create(SpaceCreateRequest request) {
        return spaceService.create(request);
    }

    public Optional<Space> update(String id, SpaceUpdateRequest request) {
        UUID uuid = parseUuid(id);
        if (request.name() != null) {
            spaceService.rename(uuid, request.name());
        }
        if (request.description() != null) {
            spaceService.updateDescription(uuid, request.description());
        }
        if (request.parentSpaceId() != null) {
            spaceService.moveSpace(uuid, request.parentSpaceId());
        }
        return spaceService.findById(uuid);
    }

    public void delete(String id) {
        spaceService.delete(parseUuid(id));
    }

    public List<Channel> channelsInSpace(String id) {
        return spaceService.listChannels(parseUuid(id));
    }

    private static UUID parseUuid(String id) {
        try { return UUID.fromString(id); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("Invalid UUID: " + id); }
    }
}
