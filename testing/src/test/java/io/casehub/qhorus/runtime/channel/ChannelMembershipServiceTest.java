package io.casehub.qhorus.runtime.channel;

import io.casehub.qhorus.api.channel.ChannelMembership;
import io.casehub.qhorus.api.channel.MemberRole;
import io.casehub.qhorus.api.channel.UnreadCount;
import io.casehub.qhorus.api.message.Message;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.qhorus.persistence.memory.InMemoryChannelMembershipStore;
import io.casehub.qhorus.persistence.memory.InMemoryMessageStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ChannelMembershipServiceTest {

    private ChannelMembershipService service;
    private InMemoryChannelMembershipStore membershipStore;
    private InMemoryMessageStore messageStore;
    private UUID channelId;

    @BeforeEach
    void setUp() {
        membershipStore = new InMemoryChannelMembershipStore();
        messageStore = new InMemoryMessageStore();
        service = new ChannelMembershipService();
        service.membershipStore = membershipStore;
        service.messageStore = messageStore;
        channelId = UUID.randomUUID();
    }

    @Test
    void join_createsNewMembership() {
        ChannelMembership m = service.join(channelId, "agent-1", MemberRole.PARTICIPANT, "default");
        assertThat(m.memberId()).isEqualTo("agent-1");
        assertThat(m.role()).isEqualTo(MemberRole.PARTICIPANT);
        assertThat(m.joinedAt()).isNotNull();
    }

    @Test
    void join_existingMember_updatesRolePreservesJoinedAt() {
        ChannelMembership first = service.join(channelId, "agent-1", MemberRole.PARTICIPANT, "default");
        Instant originalJoinedAt = first.joinedAt();

        ChannelMembership updated = service.join(channelId, "agent-1", MemberRole.MODERATOR, "default");
        assertThat(updated.role()).isEqualTo(MemberRole.MODERATOR);
        assertThat(updated.joinedAt()).isEqualTo(originalJoinedAt);
    }

    @Test
    void join_initializesLastReadMessageIdToMaxMessageId() {
        messageStore.put(Message.builder().channelId(channelId).sender("alice")
                .messageType(MessageType.STATUS).actorType(ActorType.AGENT).content("msg1").build());
        Message last = messageStore.put(Message.builder().channelId(channelId).sender("alice")
                .messageType(MessageType.STATUS).actorType(ActorType.AGENT).content("msg2").build());

        ChannelMembership m = service.join(channelId, "bob", MemberRole.PARTICIPANT, "default");
        assertThat(m.lastReadMessageId()).isEqualTo(last.id());
    }

    @Test
    void join_emptyChannel_initializesLastReadMessageIdToZero() {
        ChannelMembership m = service.join(channelId, "agent-1", MemberRole.PARTICIPANT, "default");
        assertThat(m.lastReadMessageId()).isEqualTo(0L);
    }

    @Test
    void leave_removesMembership() {
        service.join(channelId, "agent-1", MemberRole.PARTICIPANT, "default");
        service.leave(channelId, "agent-1");
        assertThat(membershipStore.find(channelId, "agent-1")).isEmpty();
    }

    @Test
    void leave_nonMember_noOp() {
        service.leave(channelId, "nobody");
    }

    @Test
    void listMembers_returnsAll() {
        service.join(channelId, "agent-1", MemberRole.PARTICIPANT, "default");
        service.join(channelId, "agent-2", MemberRole.OBSERVER, "default");
        assertThat(service.listMembers(channelId)).hasSize(2);
    }

    @Test
    void markRead_advancesForward() {
        service.join(channelId, "agent-1", MemberRole.PARTICIPANT, "default");
        service.markRead(channelId, "agent-1", 10L);
        assertThat(membershipStore.find(channelId, "agent-1").get().lastReadMessageId()).isEqualTo(10L);
        service.markRead(channelId, "agent-1", 20L);
        assertThat(membershipStore.find(channelId, "agent-1").get().lastReadMessageId()).isEqualTo(20L);
    }

    @Test
    void markRead_regressIsNoOp() {
        service.join(channelId, "agent-1", MemberRole.PARTICIPANT, "default");
        service.markRead(channelId, "agent-1", 20L);
        service.markRead(channelId, "agent-1", 10L);
        assertThat(membershipStore.find(channelId, "agent-1").get().lastReadMessageId()).isEqualTo(20L);
    }

    @Test
    void getUnreadCounts_excludesOwnMessages() {
        service.join(channelId, "bob", MemberRole.PARTICIPANT, "default");
        service.markRead(channelId, "bob", 0L);

        messageStore.put(Message.builder().channelId(channelId).sender("alice")
                .messageType(MessageType.STATUS).actorType(ActorType.AGENT).content("from alice").build());
        messageStore.put(Message.builder().channelId(channelId).sender("bob")
                .messageType(MessageType.STATUS).actorType(ActorType.AGENT).content("from bob").build());

        Map<UUID, UnreadCount> counts = service.getUnreadCounts("bob", "default");
        assertThat(counts).containsKey(channelId);
        assertThat(counts.get(channelId).count()).isEqualTo(1);
    }

    @Test
    void getUnreadCounts_excludesEventMessages() {
        service.join(channelId, "bob", MemberRole.PARTICIPANT, "default");
        service.markRead(channelId, "bob", 0L);

        messageStore.put(Message.builder().channelId(channelId).sender("alice")
                .messageType(MessageType.STATUS).actorType(ActorType.AGENT).content("visible").build());
        messageStore.put(Message.builder().channelId(channelId).sender("system:normaliser")
                .messageType(MessageType.EVENT).actorType(ActorType.SYSTEM).build());

        Map<UUID, UnreadCount> counts = service.getUnreadCounts("bob", "default");
        assertThat(counts.get(channelId).count()).isEqualTo(1);
    }

    @Test
    void getUnreadCounts_multipleChannels() {
        UUID ch2 = UUID.randomUUID();
        service.join(channelId, "bob", MemberRole.PARTICIPANT, "default");
        service.join(ch2, "bob", MemberRole.PARTICIPANT, "default");
        service.markRead(channelId, "bob", 0L);
        service.markRead(ch2, "bob", 0L);

        messageStore.put(Message.builder().channelId(channelId).sender("alice")
                .messageType(MessageType.STATUS).actorType(ActorType.AGENT).content("ch1 msg").build());
        messageStore.put(Message.builder().channelId(ch2).sender("alice")
                .messageType(MessageType.STATUS).actorType(ActorType.AGENT).content("ch2 msg1").build());
        messageStore.put(Message.builder().channelId(ch2).sender("alice")
                .messageType(MessageType.STATUS).actorType(ActorType.AGENT).content("ch2 msg2").build());

        Map<UUID, UnreadCount> counts = service.getUnreadCounts("bob", "default");
        assertThat(counts).hasSize(2);
        assertThat(counts.get(channelId).count()).isEqualTo(1);
        assertThat(counts.get(ch2).count()).isEqualTo(2);
    }
}
