package io.casehub.qhorus.runtime.channel;

import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.channel.ChannelCreateRequest;
import io.casehub.qhorus.api.channel.ChannelMembership;
import io.casehub.qhorus.api.channel.ChannelSemantic;
import io.casehub.qhorus.api.channel.ChannelSlugValidator;
import io.casehub.qhorus.api.channel.ChannelVisibility;
import io.casehub.qhorus.api.channel.FindOrCreateResult;
import io.casehub.qhorus.api.channel.MemberRole;
import io.casehub.qhorus.api.channel.ParticipationPolicyMode;
import io.casehub.qhorus.api.channel.VisibilityManager;
import io.casehub.qhorus.api.store.ChannelMembershipStore;
import io.casehub.qhorus.api.store.ChannelStore;
import io.casehub.qhorus.api.store.query.ChannelQuery;
import io.casehub.platform.api.identity.CurrentPrincipal;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class VisibilityService implements VisibilityManager {

    private final ChannelService channelService;
    private final ChannelMembershipService membershipService;
    private final ChannelMembershipStore membershipStore;
    private final ChannelStore channelStore;
    private final CurrentPrincipal currentPrincipal;

    public VisibilityService(ChannelService channelService,
                             ChannelMembershipService membershipService,
                             ChannelMembershipStore membershipStore,
                             ChannelStore channelStore,
                             CurrentPrincipal currentPrincipal) {
        this.channelService = channelService;
        this.membershipService = membershipService;
        this.membershipStore = membershipStore;
        this.channelStore = channelStore;
        this.currentPrincipal = currentPrincipal;
    }

    @Override
    public FindOrCreateResult getOrCreateDirect(String participantA, String participantB) {
        if (!ChannelSlugValidator.isValidSegment(participantA)) {
            throw new IllegalArgumentException(
                "Participant ID '" + participantA + "' is not a valid slug segment — "
                + "must match [a-z][a-z0-9]*(-[a-z0-9]+)*");
        }
        if (!ChannelSlugValidator.isValidSegment(participantB)) {
            throw new IllegalArgumentException(
                "Participant ID '" + participantB + "' is not a valid slug segment — "
                + "must match [a-z][a-z0-9]*(-[a-z0-9]+)*");
        }
        if (participantA.equals(participantB)) {
            throw new IllegalArgumentException("Cannot create a DIRECT channel with the same participant");
        }

        String first = participantA.compareTo(participantB) < 0 ? participantA : participantB;
        String second = participantA.compareTo(participantB) < 0 ? participantB : participantA;
        String name = "dm/" + first + "/" + second;

        var req = ChannelCreateRequest.builder(name)
                .visibility(ChannelVisibility.DIRECT)
                .semantic(ChannelSemantic.APPEND)
                .participationPolicy(ParticipationPolicyMode.ENFORCE)
                .build();
        FindOrCreateResult result = channelService.findOrCreate(req);
        Channel channel = result.channel();

        if (result.wasCreated()) {
            String tenancyId = currentPrincipal.tenancyId();
            membershipService.join(channel.id(), first, MemberRole.PARTICIPANT, tenancyId);
            membershipService.join(channel.id(), second, MemberRole.PARTICIPANT, tenancyId);
        } else {
            if (channel.visibility() != ChannelVisibility.DIRECT) {
                throw new IllegalStateException(
                    "Channel '" + name + "' exists but is not a DIRECT channel");
            }
        }
        return result;
    }

    @Override
    public List<Channel> findDirectChannelsFor(String participantId) {
        String tenancyId = currentPrincipal.tenancyId();
        List<ChannelMembership> memberships = membershipStore.findByMember(participantId, tenancyId);
        Set<UUID> channelIds = memberships.stream()
                .map(ChannelMembership::channelId)
                .collect(Collectors.toSet());
        if (channelIds.isEmpty()) return List.of();
        return channelStore.findByIds(channelIds).stream()
                .filter(ch -> ch.visibility() == ChannelVisibility.DIRECT)
                .toList();
    }

    @Override
    public Channel createGroupChannel(UUID groupId, String groupName,
                                       List<String> memberIds, String coordinatorId) {
        String name = "group/" + groupName;
        var req = ChannelCreateRequest.builder(name)
                .visibility(ChannelVisibility.PRIVATE)
                .semantic(ChannelSemantic.APPEND)
                .participationPolicy(ParticipationPolicyMode.ENFORCE)
                .boundGroupId(groupId)
                .build();
        FindOrCreateResult result = channelService.findOrCreate(req);
        Channel channel = result.channel();

        if (result.wasCreated()) {
            String tenancyId = currentPrincipal.tenancyId();
            membershipService.join(channel.id(), coordinatorId, MemberRole.MODERATOR, tenancyId);
            for (String memberId : memberIds) {
                if (!memberId.equals(coordinatorId)) {
                    membershipService.join(channel.id(), memberId, MemberRole.PARTICIPANT, tenancyId);
                }
            }
        } else {
            if (channel.boundGroupId() == null) {
                throw new IllegalStateException(
                    "Channel '" + name + "' exists but is not bound to any group — "
                    + "cannot bind to group (requested: " + groupId + ")");
            }
            if (!channel.boundGroupId().equals(groupId)) {
                throw new IllegalStateException(
                    "Channel '" + name + "' is bound to a different group "
                    + "(existing: " + channel.boundGroupId() + ", requested: " + groupId + ")");
            }
        }
        return channel;
    }

    @Override
    public Optional<Channel> findGroupChannel(UUID groupId) {
        return channelStore.scan(ChannelQuery.byBoundGroupId(groupId))
                .stream().findFirst();
    }
}
