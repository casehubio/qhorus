package io.casehub.qhorus.runtime.channel;

import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.channel.ChannelMembership;
import io.casehub.qhorus.api.channel.ChannelSemantic;
import io.casehub.qhorus.api.channel.ChannelVisibility;
import io.casehub.qhorus.api.channel.MemberRole;
import io.casehub.qhorus.persistence.memory.InMemoryChannelMembershipStore;
import io.casehub.qhorus.persistence.memory.InMemoryChannelStore;
import io.casehub.qhorus.persistence.memory.InMemoryMessageStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChannelMembershipServiceDirectGuardTest {

    private ChannelMembershipService service;
    private InMemoryChannelMembershipStore membershipStore;
    private InMemoryChannelStore channelStore;

    @BeforeEach
    void setUp() {
        membershipStore = new InMemoryChannelMembershipStore();
        channelStore = new InMemoryChannelStore();
        service = new ChannelMembershipService(
                membershipStore, new InMemoryMessageStore(), null, channelStore);
    }

    @Test
    void join_directChannel_allowsFirstTwoMembers() {
        Channel ch = createDirectChannel();
        ChannelMembership m1 = service.join(ch.id(), "alice", MemberRole.PARTICIPANT, "default");
        ChannelMembership m2 = service.join(ch.id(), "bob", MemberRole.PARTICIPANT, "default");
        assertThat(m1.memberId()).isEqualTo("alice");
        assertThat(m2.memberId()).isEqualTo("bob");
    }

    @Test
    void join_directChannel_rejectsThirdMember() {
        Channel ch = createDirectChannel();
        service.join(ch.id(), "alice", MemberRole.PARTICIPANT, "default");
        service.join(ch.id(), "bob", MemberRole.PARTICIPANT, "default");

        assertThatThrownBy(() ->
                service.join(ch.id(), "carol", MemberRole.PARTICIPANT, "default"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DIRECT")
                .hasMessageContaining("exactly 2 members");
    }

    @Test
    void join_directChannel_existingMemberRoleUpdate_allowed() {
        Channel ch = createDirectChannel();
        service.join(ch.id(), "alice", MemberRole.PARTICIPANT, "default");
        service.join(ch.id(), "bob", MemberRole.PARTICIPANT, "default");

        ChannelMembership updated = service.join(ch.id(), "alice", MemberRole.MODERATOR, "default");
        assertThat(updated.role()).isEqualTo(MemberRole.MODERATOR);
    }

    @Test
    void leave_directChannel_throws() {
        Channel ch = createDirectChannel();
        service.join(ch.id(), "alice", MemberRole.PARTICIPANT, "default");

        assertThatThrownBy(() -> service.leave(ch.id(), "alice"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot leave a DIRECT channel");
    }

    @Test
    void leave_nonDirectChannel_succeeds() {
        Channel ch = createChannel(ChannelVisibility.PRIVATE);
        service.join(ch.id(), "alice", MemberRole.PARTICIPANT, "default");
        service.leave(ch.id(), "alice");
        assertThat(membershipStore.find(ch.id(), "alice")).isEmpty();
    }

    @Test
    void leave_publicChannel_succeeds() {
        Channel ch = createChannel(ChannelVisibility.PUBLIC);
        service.join(ch.id(), "alice", MemberRole.PARTICIPANT, "default");
        service.leave(ch.id(), "alice");
        assertThat(membershipStore.find(ch.id(), "alice")).isEmpty();
    }

    @Test
    void leave_nullVisibilityChannel_succeeds() {
        Channel ch = createChannel(null);
        service.join(ch.id(), "alice", MemberRole.PARTICIPANT, "default");
        service.leave(ch.id(), "alice");
        assertThat(membershipStore.find(ch.id(), "alice")).isEmpty();
    }

    @Test
    void join_nonDirectChannel_allowsUnlimitedMembers() {
        Channel ch = createChannel(ChannelVisibility.PRIVATE);
        for (int i = 0; i < 5; i++) {
            service.join(ch.id(), "agent-" + i, MemberRole.PARTICIPANT, "default");
        }
        assertThat(service.listMembers(ch.id())).hasSize(5);
    }

    @Test
    void join_noChannelStore_skipsGuard() {
        var serviceNoStore = new ChannelMembershipService(
                membershipStore, new InMemoryMessageStore(), null);
        Channel ch = createDirectChannel();
        serviceNoStore.join(ch.id(), "alice", MemberRole.PARTICIPANT, "default");
        serviceNoStore.join(ch.id(), "bob", MemberRole.PARTICIPANT, "default");
        serviceNoStore.join(ch.id(), "carol", MemberRole.PARTICIPANT, "default");
        assertThat(membershipStore.findByChannel(ch.id())).hasSize(3);
    }

    private Channel createDirectChannel() {
        return createChannel(ChannelVisibility.DIRECT);
    }

    private Channel createChannel(ChannelVisibility visibility) {
        Instant now = Instant.now();
        Channel ch = Channel.builder("test-ch-" + UUID.randomUUID().toString().substring(0, 8))
                .id(UUID.randomUUID())
                .semantic(ChannelSemantic.APPEND)
                .visibility(visibility)
                .tenancyId("default")
                .createdAt(now)
                .lastActivityAt(now)
                .build();
        return channelStore.put(ch);
    }
}
