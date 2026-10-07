package io.casehub.qhorus.cache;

import io.casehub.qhorus.api.message.Message;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.store.query.MessageQuery;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ChannelMessageBufferTest {

    private static final UUID CH = UUID.randomUUID();

    private static Message msg(long id, String sender, MessageType type, String content) {
        return new Message(id, CH, sender, type, null, null, content, null,
                null, null, 0, null, null, null, null, null, null, 0, Instant.now());
    }

    @Test
    void addAndQueryReturnsMessages() {
        var buf = new ChannelMessageBuffer(100);
        buf.add(msg(1, "a", MessageType.STATUS, "hello"));
        buf.add(msg(2, "b", MessageType.STATUS, "world"));

        List<Message> result = buf.query(MessageQuery.forChannel(CH));
        assertThat(result).hasSize(2);
        assertThat(result.get(0).id()).isEqualTo(1L);
        assertThat(result.get(1).id()).isEqualTo(2L);
    }

    @Test
    void queryWithAfterIdFilters() {
        var buf = new ChannelMessageBuffer(100);
        buf.add(msg(1, "a", MessageType.STATUS, "one"));
        buf.add(msg(2, "a", MessageType.STATUS, "two"));
        buf.add(msg(3, "a", MessageType.STATUS, "three"));

        List<Message> result = buf.query(MessageQuery.poll(CH, 1L, 50));
        assertThat(result).hasSize(2);
        assertThat(result.get(0).id()).isEqualTo(2L);
    }

    @Test
    void evictionRemovesOldestWhenBounded() {
        var buf = new ChannelMessageBuffer(3);
        buf.add(msg(1, "a", MessageType.STATUS, "one"));
        buf.add(msg(2, "a", MessageType.STATUS, "two"));
        buf.add(msg(3, "a", MessageType.STATUS, "three"));
        buf.add(msg(4, "a", MessageType.STATUS, "four"));

        assertThat(buf.size()).isEqualTo(3);
        assertThat(buf.findById(1L)).isEmpty();
        assertThat(buf.findById(2L)).isPresent();
    }

    @Test
    void unboundedBufferRetainsAll() {
        var buf = new ChannelMessageBuffer(0);
        for (long i = 1; i <= 500; i++) {
            buf.add(msg(i, "a", MessageType.STATUS, "msg-" + i));
        }
        assertThat(buf.size()).isEqualTo(500);
    }

    @Test
    void coversReturnsTrueWhenAfterIdInRange() {
        var buf = new ChannelMessageBuffer(100);
        buf.add(msg(10, "a", MessageType.STATUS, "x"));
        buf.add(msg(20, "a", MessageType.STATUS, "y"));

        assertThat(buf.covers(null)).isTrue();
        assertThat(buf.covers(10L)).isTrue();
        assertThat(buf.covers(15L)).isTrue();
        assertThat(buf.covers(5L)).isFalse();
    }

    @Test
    void coversReturnsFalseWhenEmpty() {
        var buf = new ChannelMessageBuffer(100);
        assertThat(buf.covers(null)).isFalse();
    }

    @Test
    void queryWithLimitRespectsLimit() {
        var buf = new ChannelMessageBuffer(100);
        for (long i = 1; i <= 10; i++) {
            buf.add(msg(i, "a", MessageType.STATUS, "msg"));
        }

        List<Message> result = buf.query(MessageQuery.poll(CH, 0L, 3));
        assertThat(result).hasSize(3);
    }

    @Test
    void queryWithSenderFilter() {
        var buf = new ChannelMessageBuffer(100);
        buf.add(msg(1, "alice", MessageType.STATUS, "hi"));
        buf.add(msg(2, "bob", MessageType.STATUS, "hello"));
        buf.add(msg(3, "alice", MessageType.COMMAND, "do it"));

        MessageQuery q = MessageQuery.builder().channelId(CH).sender("alice").build();
        List<Message> result = buf.query(q);
        assertThat(result).hasSize(2);
        assertThat(result).allMatch(m -> m.sender().equals("alice"));
    }

    @Test
    void findByIdReturnsMessage() {
        var buf = new ChannelMessageBuffer(100);
        buf.add(msg(42, "a", MessageType.STATUS, "found"));

        assertThat(buf.findById(42L)).isPresent();
        assertThat(buf.findById(99L)).isEmpty();
    }

    @Test
    void lastIdReturnsHighestId() {
        var buf = new ChannelMessageBuffer(100);
        assertThat(buf.lastId()).isNull();

        buf.add(msg(5, "a", MessageType.STATUS, "x"));
        buf.add(msg(10, "a", MessageType.STATUS, "y"));
        assertThat(buf.lastId()).isEqualTo(10L);
    }

    @Test
    void nullIdMessageIsIgnored() {
        var buf = new ChannelMessageBuffer(100);
        buf.add(new Message(null, CH, "a", MessageType.STATUS, null, null, "x",
                null, null, null, 0, null, null, null, null, null, null, 0, Instant.now()));
        assertThat(buf.size()).isZero();
    }

    @Test
    void removeDeletesMessageById() {
        var buf = new ChannelMessageBuffer(10);
        buf.add(msg(1, "a", MessageType.STATUS, "one"));
        buf.add(msg(2, "a", MessageType.STATUS, "two"));
        buf.add(msg(3, "a", MessageType.STATUS, "three"));

        buf.remove(2L);

        assertThat(buf.size()).isEqualTo(2);
        assertThat(buf.findById(2L)).isEmpty();
        assertThat(buf.findById(1L)).isPresent();
        assertThat(buf.findById(3L)).isPresent();
    }

    @Test
    void removeNullIsNoOp() {
        var buf = new ChannelMessageBuffer(10);
        buf.add(msg(1, "a", MessageType.STATUS, "one"));

        buf.remove(null);

        assertThat(buf.size()).isEqualTo(1);
    }

    @Test
    void recentMessagesReturnsTailDescending() {
        var buf = new ChannelMessageBuffer(10);
        buf.add(msg(1, "a", MessageType.STATUS, "one"));
        buf.add(msg(2, "a", MessageType.STATUS, "two"));
        buf.add(msg(3, "a", MessageType.STATUS, "three"));
        buf.add(msg(4, "a", MessageType.STATUS, "four"));

        List<Message> recent = buf.recentMessages(2);

        assertThat(recent).hasSize(2);
        assertThat(recent.get(0).id()).isEqualTo(4L);
        assertThat(recent.get(1).id()).isEqualTo(3L);
    }

    @Test
    void recentMessagesReturnsAllWhenLimitExceedsSize() {
        var buf = new ChannelMessageBuffer(10);
        buf.add(msg(1, "a", MessageType.STATUS, "one"));
        buf.add(msg(2, "a", MessageType.STATUS, "two"));

        List<Message> recent = buf.recentMessages(5);

        assertThat(recent).hasSize(2);
    }

}
