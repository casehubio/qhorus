package io.casehub.qhorus.persistence.memory.contract;

import io.casehub.qhorus.api.channel.ChannelMembership;
import io.casehub.qhorus.api.channel.MemberRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

abstract class ChannelMembershipStoreContractTest {

    protected abstract ChannelMembership put(ChannelMembership m);
    protected abstract java.util.Optional<ChannelMembership> find(UUID channelId, String memberId);
    protected abstract java.util.List<ChannelMembership> findByChannel(UUID channelId);
    protected abstract java.util.List<ChannelMembership> findByMember(String memberId, String tenancyId);
    protected abstract void updateRole(UUID channelId, String memberId, MemberRole role);
    protected abstract void updateLastReadMessageId(UUID channelId, String memberId, Long messageId);
    protected abstract boolean delete(UUID channelId, String memberId);
    protected abstract void deleteAll(UUID channelId);

    private UUID channelId;

    @BeforeEach
    void setUp() {
        channelId = UUID.randomUUID();
    }

    @Test
    void putAndFind() {
        var m = new ChannelMembership(null, channelId, "agent-1", MemberRole.PARTICIPANT, "default", Instant.now(), null);
        var saved = put(m);
        assertThat(saved.id()).isNotNull();
        var found = find(channelId, "agent-1");
        assertThat(found).isPresent();
        assertThat(found.get().role()).isEqualTo(MemberRole.PARTICIPANT);
    }

    @Test
    void findByChannel_returnsAll() {
        put(new ChannelMembership(null, channelId, "agent-1", MemberRole.PARTICIPANT, "default", Instant.now(), null));
        put(new ChannelMembership(null, channelId, "agent-2", MemberRole.OBSERVER, "default", Instant.now(), null));
        assertThat(findByChannel(channelId)).hasSize(2);
    }

    @Test
    void findByMember_returnsAcrossChannels() {
        UUID ch2 = UUID.randomUUID();
        put(new ChannelMembership(null, channelId, "agent-1", MemberRole.PARTICIPANT, "default", Instant.now(), null));
        put(new ChannelMembership(null, ch2, "agent-1", MemberRole.OBSERVER, "default", Instant.now(), null));
        assertThat(findByMember("agent-1", "default")).hasSize(2);
    }

    @Test
    void updateRole() {
        put(new ChannelMembership(null, channelId, "agent-1", MemberRole.PARTICIPANT, "default", Instant.now(), null));
        updateRole(channelId, "agent-1", MemberRole.MODERATOR);
        assertThat(find(channelId, "agent-1").get().role()).isEqualTo(MemberRole.MODERATOR);
    }

    @Test
    void updateLastReadMessageId() {
        put(new ChannelMembership(null, channelId, "agent-1", MemberRole.PARTICIPANT, "default", Instant.now(), 0L));
        updateLastReadMessageId(channelId, "agent-1", 42L);
        assertThat(find(channelId, "agent-1").get().lastReadMessageId()).isEqualTo(42L);
    }

    @Test
    void delete_existing() {
        put(new ChannelMembership(null, channelId, "agent-1", MemberRole.PARTICIPANT, "default", Instant.now(), null));
        assertThat(delete(channelId, "agent-1")).isTrue();
        assertThat(find(channelId, "agent-1")).isEmpty();
    }

    @Test
    void delete_nonExistent() {
        assertThat(delete(channelId, "nobody")).isFalse();
    }

    @Test
    void deleteAll() {
        put(new ChannelMembership(null, channelId, "agent-1", MemberRole.PARTICIPANT, "default", Instant.now(), null));
        put(new ChannelMembership(null, channelId, "agent-2", MemberRole.OBSERVER, "default", Instant.now(), null));
        deleteAll(channelId);
        assertThat(findByChannel(channelId)).isEmpty();
    }
}
