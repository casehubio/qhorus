package io.casehub.qhorus.runtime.channel;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.channel.Space;
import io.casehub.qhorus.api.channel.SpaceCreateRequest;
import io.casehub.qhorus.api.store.ChannelStore;
import io.casehub.qhorus.api.store.SpaceStore;
import io.casehub.qhorus.api.store.query.ChannelQuery;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class SpaceService {

    static final int MAX_DEPTH = 10;

    @Inject
    SpaceStore spaceStore;

    @Inject
    ChannelStore channelStore;

    @Inject
    CurrentPrincipal currentPrincipal;

    @Transactional
    public Space create(SpaceCreateRequest request) {
        if (request.parentSpaceId() != null) {
            Space parent = spaceStore.find(request.parentSpaceId())
                                     .orElseThrow(() -> new IllegalArgumentException(
                                             "Parent space not found: " + request.parentSpaceId()));
            int depth = computeDepth(parent) + 1;
            if (depth >= MAX_DEPTH) {
                throw new IllegalStateException(
                        "Maximum nesting depth (" + MAX_DEPTH + ") exceeded");
            }
        }
        List<Space> sameName = spaceStore.findByName(request.name());
        boolean duplicate = sameName.stream().anyMatch(s ->
                                                               java.util.Objects.equals(s.parentSpaceId(), request.parentSpaceId()));
        if (duplicate) {
            throw new IllegalArgumentException(
                    "Space name '" + request.name() + "' already exists under the same parent");
        }
        Space space = new Space(UUID.randomUUID(), request.name(), request.description(),
                                request.parentSpaceId(), currentPrincipal.tenancyId(), Instant.now());
        return spaceStore.put(space);}

    public Optional<Space> findById(UUID id) {
        return spaceStore.find(id);
    }

    public Optional<Space> findByName(String name) {
        List<Space> matches = spaceStore.findByName(name);
        if (matches.isEmpty()) {return Optional.empty();}
        if (matches.size() == 1) {return Optional.of(matches.get(0));}
        throw new IllegalStateException(
                "Ambiguous space name '" + name + "' — " + matches.size()
                + " matches. Use UUID instead.");
    }

    public List<Space> findByIds(java.util.Collection<UUID> ids) {
        return spaceStore.findByIds(ids);
    }


    public List<Space> listChildren(UUID parentSpaceId) {
        return spaceStore.listByParent(parentSpaceId);
    }

    public List<Space> listRoots() {
        return spaceStore.listRoots();
    }

    public List<Channel> listChannels(UUID spaceId) {
        return channelStore.scan(ChannelQuery.bySpaceId(spaceId));
    }

    @Transactional
    public void delete(UUID spaceId) {
        spaceStore.find(spaceId)
                  .orElseThrow(() -> new IllegalArgumentException("Space not found: " + spaceId));
        if (spaceStore.hasChildren(spaceId)) {
            throw new IllegalStateException(
                    "Cannot delete space with child spaces: " + spaceId);
        }
        if (channelStore.hasChannelsInSpace(spaceId)) {
            throw new IllegalStateException(
                    "Cannot delete space with channels: " + spaceId);
        }
        spaceStore.delete(spaceId);
    }

    @Transactional
    public Space rename(UUID spaceId, String newName) {
        if (newName == null || newName.isBlank()) {
            throw new IllegalArgumentException("Space name must not be blank");
        }
        newName = newName.trim();
        if (newName.length() > 200) {
            throw new IllegalArgumentException("Space name exceeds 200 chars");
        }
        Space space = spaceStore.find(spaceId)
                                .orElseThrow(() -> new IllegalArgumentException("Space not found: " + spaceId));
        Space updated = new Space(space.id(), newName, space.description(),
                                  space.parentSpaceId(), space.tenancyId(), space.createdAt());
        return spaceStore.put(updated);
    }

    @Transactional
    public Space updateDescription(UUID spaceId, String newDescription) {
        Space space = spaceStore.find(spaceId)
                                .orElseThrow(() -> new IllegalArgumentException("Space not found: " + spaceId));
        String desc = newDescription != null ? newDescription.trim() : null;
        Space updated = new Space(space.id(), space.name(), desc,
                                  space.parentSpaceId(), space.tenancyId(), space.createdAt());
        return spaceStore.put(updated);
    }

    @Transactional
    public Space moveSpace(UUID spaceId, UUID newParentSpaceId) {
        Space space = spaceStore.find(spaceId)
                                .orElseThrow(() -> new IllegalArgumentException("Space not found: " + spaceId));
        if (newParentSpaceId != null) {
            if (newParentSpaceId.equals(spaceId)) {
                throw new IllegalArgumentException("Cannot move space into itself");
            }
            Space newParent = spaceStore.find(newParentSpaceId)
                                        .orElseThrow(() -> new IllegalArgumentException(
                                                "Target parent space not found: " + newParentSpaceId));
            UUID ancestor = newParent.parentSpaceId();
            int  walked   = 0;
            while (ancestor != null && walked < MAX_DEPTH) {
                if (ancestor.equals(spaceId)) {
                    throw new IllegalArgumentException(
                            "Moving space " + spaceId + " under " + newParentSpaceId
                            + " would create a cycle");
                }
                ancestor = spaceStore.find(ancestor).map(Space::parentSpaceId).orElse(null);
                walked++;
            }
            int parentDepth  = computeDepth(newParent);
            int subtreeDepth = computeSubtreeDepth(spaceId);
            if (parentDepth + 1 + subtreeDepth > MAX_DEPTH) {
                throw new IllegalStateException(
                        "Moving space would exceed maximum nesting depth (" + MAX_DEPTH + ")");
            }
        }
        Space updated = new Space(space.id(), space.name(), space.description(),
                                  newParentSpaceId, space.tenancyId(), space.createdAt());
        return spaceStore.put(updated);
    }

    @Transactional
    public Channel moveChannelToSpace(UUID channelId, UUID spaceId) {
        Channel channel = channelStore.find(channelId)
                                      .orElseThrow(() -> new IllegalArgumentException("Channel not found: " + channelId));
        if (spaceId != null) {
            Space space = spaceStore.find(spaceId)
                                    .orElseThrow(() -> new IllegalArgumentException("Space not found: " + spaceId));
            if (!channel.tenancyId().equals(space.tenancyId())) {
                throw new IllegalArgumentException(
                        "Cannot move channel to space in different tenancy");
            }
        }
        return channelStore.put(channel.toBuilder().spaceId(spaceId).build());
    }

    private int computeDepth(Space space) {
        int  depth    = 0;
        UUID parentId = space.parentSpaceId();
        while (parentId != null && depth < MAX_DEPTH) {
            parentId = spaceStore.find(parentId).map(Space::parentSpaceId).orElse(null);
            depth++;
        }
        return depth;
    }

    private int computeSubtreeDepth(UUID spaceId) {
        List<Space> children = spaceStore.listByParent(spaceId);
        if (children.isEmpty()) {return 0;}
        int maxChildDepth = 0;
        for (Space child : children) {
            maxChildDepth = Math.max(maxChildDepth, computeSubtreeDepth(child.id()));
        }
        return 1 + maxChildDepth;
    }
}
