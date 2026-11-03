package io.casehub.qhorus.runtime.channel;

import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.channel.ChannelCreateRequest;
import io.casehub.qhorus.api.channel.ChannelMembership;
import io.casehub.qhorus.api.channel.ChannelSemantic;
import io.casehub.qhorus.api.channel.ChannelVisibility;
import io.casehub.qhorus.api.channel.FindOrCreateResult;
import io.casehub.qhorus.api.channel.MemberRole;
import io.casehub.qhorus.api.store.ChannelMembershipStore;
import io.casehub.qhorus.api.store.ChannelStore;
import io.casehub.qhorus.api.store.query.ChannelQuery;
import io.casehub.platform.api.identity.CurrentPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VisibilityServiceTest {

    private VisibilityService service;
    private ChannelService channelService;
    private ChannelMembershipService membershipService;
    private ChannelMembershipStore membershipStore;
    private ChannelStore channelStore;
    private CurrentPrincipal currentPrincipal;

    @BeforeEach
    void setUp() {
        channelService = mock(ChannelService.class);
        membershipService = mock(ChannelMembershipService.class);
        membershipStore = mock(ChannelMembershipStore.class);
        channelStore = mock(ChannelStore.class);
        currentPrincipal = mock(CurrentPrincipal.class);
        when(currentPrincipal.tenancyId()).thenReturn("default");
        service = new VisibilityService(channelService, membershipService,
                membershipStore, channelStore, currentPrincipal);
    }

    @Test
    void getOrCreateDirect_createsWithCanonicalNameOrdering() {
        Channel ch = directChannel("dm/alice/bob");
        when(channelService.findOrCreate(any())).thenReturn(new FindOrCreateResult(ch, true));

        FindOrCreateResult result = service.getOrCreateDirect("bob", "alice");

        assertThat(result.channel().name()).isEqualTo("dm/alice/bob");
        ArgumentCaptor<ChannelCreateRequest> captor = ArgumentCaptor.forClass(ChannelCreateRequest.class);
        verify(channelService).findOrCreate(captor.capture());
        assertThat(captor.getValue().name()).isEqualTo("dm/alice/bob");
        assertThat(captor.getValue().visibility()).isEqualTo(ChannelVisibility.DIRECT);
        assertThat(captor.getValue().semantic()).isEqualTo(ChannelSemantic.APPEND);
    }

    @Test
    void getOrCreateDirect_joinsBothParticipantsOnCreate() {
        Channel ch = directChannel("dm/alice/bob");
        when(channelService.findOrCreate(any())).thenReturn(new FindOrCreateResult(ch, true));

        service.getOrCreateDirect("alice", "bob");

        verify(membershipService).join(ch.id(), "alice", MemberRole.PARTICIPANT, "default");
        verify(membershipService).join(ch.id(), "bob", MemberRole.PARTICIPANT, "default");
    }

    @Test
    void getOrCreateDirect_existingDirectChannel_skipsJoin() {
        Channel ch = directChannel("dm/alice/bob");
        when(channelService.findOrCreate(any())).thenReturn(new FindOrCreateResult(ch, false));

        service.getOrCreateDirect("alice", "bob");

        verify(membershipService, never()).join(any(), any(), any(), any());
    }

    @Test
    void getOrCreateDirect_existingNonDirectChannel_throws() {
        Channel ch = Channel.builder("dm/alice/bob")
                .id(UUID.randomUUID())
                .semantic(ChannelSemantic.APPEND)
                .visibility(ChannelVisibility.PRIVATE)
                .tenancyId("default")
                .createdAt(Instant.now())
                .lastActivityAt(Instant.now())
                .build();
        when(channelService.findOrCreate(any())).thenReturn(new FindOrCreateResult(ch, false));

        assertThatThrownBy(() -> service.getOrCreateDirect("alice", "bob"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not a DIRECT channel");
    }

    @Test
    void getOrCreateDirect_sameParticipant_throws() {
        assertThatThrownBy(() -> service.getOrCreateDirect("alice", "alice"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("same participant");
    }

    @Test
    void getOrCreateDirect_invalidSlugSegment_throws() {
        assertThatThrownBy(() -> service.getOrCreateDirect("Alice", "bob"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a valid slug segment");
    }

    @Test
    void getOrCreateDirect_invalidSlugSegmentSecond_throws() {
        assertThatThrownBy(() -> service.getOrCreateDirect("alice", "Bob!"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a valid slug segment");
    }

    @Test
    void findDirectChannelsFor_filtersToDirectOnly() {
        UUID directId = UUID.randomUUID();
        UUID privateId = UUID.randomUUID();
        when(membershipStore.findByMember("alice", "default")).thenReturn(List.of(
                new ChannelMembership(1L, directId, "alice", MemberRole.PARTICIPANT, "default", Instant.now(), 0L),
                new ChannelMembership(2L, privateId, "alice", MemberRole.PARTICIPANT, "default", Instant.now(), 0L)));
        when(channelStore.findByIds(Set.of(directId, privateId))).thenReturn(List.of(
                directChannel("dm/alice/bob", directId),
                Channel.builder("team-chat")
                        .id(privateId)
                        .semantic(ChannelSemantic.APPEND)
                        .visibility(ChannelVisibility.PRIVATE)
                        .tenancyId("default")
                        .createdAt(Instant.now())
                        .lastActivityAt(Instant.now())
                        .build()));

        List<Channel> result = service.findDirectChannelsFor("alice");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).visibility()).isEqualTo(ChannelVisibility.DIRECT);
    }

    @Test
    void findDirectChannelsFor_noMemberships_returnsEmpty() {
        when(membershipStore.findByMember("nobody", "default")).thenReturn(List.of());
        assertThat(service.findDirectChannelsFor("nobody")).isEmpty();
    }

    @Test
    void createGroupChannel_createsWithBoundGroupId() {
        UUID groupId = UUID.randomUUID();
        Channel ch = Channel.builder("group/engineering")
                .id(UUID.randomUUID())
                .semantic(ChannelSemantic.APPEND)
                .visibility(ChannelVisibility.PRIVATE)
                .boundGroupId(groupId)
                .tenancyId("default")
                .createdAt(Instant.now())
                .lastActivityAt(Instant.now())
                .build();
        when(channelService.findOrCreate(any())).thenReturn(new FindOrCreateResult(ch, true));

        Channel result = service.createGroupChannel(groupId, "engineering",
                List.of("alice", "bob", "carol"), "alice");

        assertThat(result.boundGroupId()).isEqualTo(groupId);
        verify(membershipService).join(ch.id(), "alice", MemberRole.MODERATOR, "default");
        verify(membershipService).join(ch.id(), "bob", MemberRole.PARTICIPANT, "default");
        verify(membershipService).join(ch.id(), "carol", MemberRole.PARTICIPANT, "default");
    }

    @Test
    void createGroupChannel_coordinatorNotDuplicated() {
        UUID groupId = UUID.randomUUID();
        Channel ch = groupChannel("group/team", groupId);
        when(channelService.findOrCreate(any())).thenReturn(new FindOrCreateResult(ch, true));

        service.createGroupChannel(groupId, "team", List.of("alice", "bob"), "alice");

        verify(membershipService).join(ch.id(), "alice", MemberRole.MODERATOR, "default");
        verify(membershipService).join(ch.id(), "bob", MemberRole.PARTICIPANT, "default");
    }

    @Test
    void createGroupChannel_existingWithDifferentGroupId_throws() {
        UUID groupId1 = UUID.randomUUID();
        UUID groupId2 = UUID.randomUUID();
        Channel ch = groupChannel("group/team", groupId1);
        when(channelService.findOrCreate(any())).thenReturn(new FindOrCreateResult(ch, false));

        assertThatThrownBy(() ->
                service.createGroupChannel(groupId2, "team", List.of("alice"), "alice"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bound to a different group");
    }

    @Test
    void createGroupChannel_existingWithNoGroupId_throws() {
        Channel ch = Channel.builder("group/team")
                .id(UUID.randomUUID())
                .semantic(ChannelSemantic.APPEND)
                .visibility(ChannelVisibility.PRIVATE)
                .tenancyId("default")
                .createdAt(Instant.now())
                .lastActivityAt(Instant.now())
                .build();
        when(channelService.findOrCreate(any())).thenReturn(new FindOrCreateResult(ch, false));

        assertThatThrownBy(() ->
                service.createGroupChannel(UUID.randomUUID(), "team", List.of("alice"), "alice"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not bound to any group");
    }

    @Test
    void findGroupChannel_found() {
        UUID groupId = UUID.randomUUID();
        Channel ch = groupChannel("group/team", groupId);
        when(channelStore.scan(any(ChannelQuery.class))).thenReturn(List.of(ch));

        Optional<Channel> result = service.findGroupChannel(groupId);
        assertThat(result).isPresent();
        assertThat(result.get().boundGroupId()).isEqualTo(groupId);
    }

    @Test
    void findGroupChannel_notFound() {
        when(channelStore.scan(any(ChannelQuery.class))).thenReturn(List.of());
        assertThat(service.findGroupChannel(UUID.randomUUID())).isEmpty();
    }

    private Channel directChannel(String name) {
        return directChannel(name, UUID.randomUUID());
    }

    private Channel directChannel(String name, UUID id) {
        return Channel.builder(name)
                .id(id)
                .semantic(ChannelSemantic.APPEND)
                .visibility(ChannelVisibility.DIRECT)
                .tenancyId("default")
                .createdAt(Instant.now())
                .lastActivityAt(Instant.now())
                .build();
    }

    private Channel groupChannel(String name, UUID groupId) {
        return Channel.builder(name)
                .id(UUID.randomUUID())
                .semantic(ChannelSemantic.APPEND)
                .visibility(ChannelVisibility.PRIVATE)
                .boundGroupId(groupId)
                .tenancyId("default")
                .createdAt(Instant.now())
                .lastActivityAt(Instant.now())
                .build();
    }
}
