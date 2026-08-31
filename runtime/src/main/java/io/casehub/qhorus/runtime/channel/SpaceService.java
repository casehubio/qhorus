package io.casehub.qhorus.runtime.channel;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.channel.Space;
import io.casehub.qhorus.api.channel.SpaceCreateRequest;
import io.casehub.qhorus.api.event.ChannelMutationEvent;
import io.casehub.qhorus.api.store.ChannelStore;
import io.casehub.qhorus.api.store.SpaceStore;
import io.casehub.qhorus.api.store.query.ChannelQuery;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
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
    @Inject
    Event<ChannelMutationEvent> mutationEvent;


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
        Space created = spaceStore.put(space);
        mutationEvent.fire(new ChannelMutationEvent.SpaceCreated(created.id(), created.name(), created.tenancyId()));
        return created;}

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
        mutationEvent.fire(new ChannelMutationEvent.SpaceDeleted(spaceId));}

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
        Space result = spaceStore.put(updated);
        mutationEvent.fire(new ChannelMutationEvent.SpaceRenamed(spaceId, newName));
        return result;}

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
        return moveChannelToSpace(channelId, spaceId, null);
    }

    @Transactional
    public Channel moveChannelToSpace(UUID channelId, UUID spaceId, Integer position) {
        Channel channel = channelStore.find(channelId)
                                      .orElseThrow(() -> new IllegalArgumentException("Channel not found: " + channelId));
        if (spaceId != null) {
            Space space = spaceStore.find(spaceId)
                                    .orElseThrow(() -> new IllegalArgumentException("Space not found: " + spaceId));
            if (!channel.tenancyId().equals(space.tenancyId())) {
                throw new IllegalArgumentException("Cannot move channel to space in different tenancy");
            }
        }

        UUID    oldSpaceId = channel.spaceId();
        boolean sameSpace  = java.util.Objects.equals(oldSpaceId, spaceId);

        if (sameSpace && position != null) {
            List<Channel> current = querySiblings(spaceId).stream()
                                                          .sorted(java.util.Comparator.comparing(Channel::displayOrder,
                                                                                                 java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
                                                          .toList();
            int currentIndex = -1;
            for (int i = 0; i < current.size(); i++) {
                if (current.get(i).id().equals(channelId)) {
                    currentIndex = i;
                    break;
                }
            }
            if (currentIndex == position) {return channel;}
        }

        List<Channel> siblings = querySiblings(spaceId).stream()
                                                       .filter(ch -> !ch.id().equals(channelId))
                                                       .sorted(java.util.Comparator.comparing(Channel::displayOrder,
                                                                                              java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
                                                       .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));

        if (position != null && position >= 0 && position <= siblings.size()) {
            siblings.add(position, channel);
        } else {
            siblings.add(channel);
        }

        for (int i = 0; i < siblings.size(); i++) {
            Channel sib     = siblings.get(i);
            Channel updated = sib.toBuilder().spaceId(spaceId).displayOrder(i).build();
            if (!updated.equals(sib)) {
                channelStore.put(updated);
            }
        }

        if (!sameSpace) {
            List<Channel> sourceSiblings = querySiblings(oldSpaceId).stream()
                                                                    .filter(ch -> !ch.id().equals(channelId))
                                                                    .sorted(java.util.Comparator.comparing(Channel::displayOrder,
                                                                                                           java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
                                                                    .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
            for (int i = 0; i < sourceSiblings.size(); i++) {
                Channel sib     = sourceSiblings.get(i);
                Channel updated = sib.toBuilder().displayOrder(i).build();
                if (!updated.equals(sib)) {
                    channelStore.put(updated);
                }
            }
        }

        Channel result = channelStore.find(channelId).orElseThrow();
        mutationEvent.fire(new ChannelMutationEvent.ChannelMoved(channelId, oldSpaceId, spaceId));
        return result;
    }

    private List<Channel> querySiblings(UUID spaceId) {
        return spaceId != null
               ? channelStore.scan(ChannelQuery.bySpaceId(spaceId))
               : channelStore.scan(ChannelQuery.topLevel());
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
