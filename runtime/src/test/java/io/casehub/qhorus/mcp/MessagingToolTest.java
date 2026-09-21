package io.casehub.qhorus.mcp;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.casehub.qhorus.testing.QhorusTestHelper;
import io.casehub.qhorus.testing.QhorusTestHelper.ArtefactDetail;
import io.casehub.qhorus.testing.QhorusTestHelper.CheckResult;
import io.casehub.qhorus.testing.QhorusTestHelper.MessageSummary;
import io.casehub.qhorus.testing.QhorusTestHelper.WaitResult;
import io.casehub.qhorus.api.message.Message;
import io.casehub.qhorus.api.message.DispatchResult;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class MessagingToolTest {

    @Inject QhorusTestHelper helper;

    // -----------------------------------------------------------------------
    // send_message
    // -----------------------------------------------------------------------

    @Test
    @TestTransaction
    void sendMessagePersistsAndReturnsResult() {
        helper.createChannel("msg-ch-1", "Test", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        DispatchResult result = helper.sendMessage("msg-ch-1", "alice", "status", "Hello!", null, null, null, null, null, null, null, null, null);

        assertNotNull(result.messageId());
        assertNotNull(result.channelId()); // channel was created — channelId is non-null
        assertEquals("alice", result.sender());
        assertEquals(io.casehub.qhorus.api.message.MessageType.STATUS, result.type());
    }

    @Test
    @TestTransaction
    void sendMessageRequestAutoGeneratesCorrelationId() {
        helper.createChannel("msg-ch-2", "Test", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        DispatchResult result = helper.sendMessage("msg-ch-2", "alice", "query", "Question?", null, null, null, null, null, null, null, null, null);

        assertNotNull(result.correlationId(),
                "request type with no correlation_id should auto-generate one");
        assertFalse(result.correlationId().isBlank());
    }

    @Test
    @TestTransaction
    void sendMessageWithExplicitCorrelationId() {
        helper.createChannel("msg-ch-3", "Test", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        DispatchResult result = helper.sendMessage("msg-ch-3", "alice", "query", "Ping", null, "my-corr-id", null, null, null, null, null, null, null);

        assertEquals("my-corr-id", result.correlationId());
    }

    @Test
    @TestTransaction
    void sendMessageReplyIncrementsParentReplyCount() {
        helper.createChannel("msg-ch-4", "Test", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        DispatchResult request = helper.sendMessage("msg-ch-4", "alice", "query", "Question?", null, null, null, null, null, null, null, null, null);

        DispatchResult reply = helper.sendMessage("msg-ch-4", "bob", "response", "Answer!", null, request.correlationId(), request.messageId(), null, null, null, null, null, null);

        assertEquals(request.messageId(), reply.inReplyTo());
        assertEquals(1, reply.parentReplyCount(),
                "parentReplyCount should be 1 after first reply");
    }

    @Test
    @TestTransaction
    void sendMessageNonRequestTypeKeepsNullCorrelationId() {
        helper.createChannel("msg-corr-null", "Test", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        DispatchResult result = helper.sendMessage("msg-corr-null", "alice", "status", "working...", null, null, null, null, null, null, null, null, null);

        assertNull(result.correlationId(),
                "status type with no correlation_id should remain null");
    }

    @Test
    @TestTransaction
    void sendMessageNonRequestTypePreservesExplicitCorrelationId() {
        helper.createChannel("msg-corr-ref", "Test", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        var query = helper.sendMessage("msg-corr-ref", "alice", "query", "Question?", null, "ref-corr", null, null, null, null, null, null, null);
        DispatchResult result = helper.sendMessage("msg-corr-ref", "bob", "response", "Answer!", null, "ref-corr", query.messageId(), null, null, null, null, null, null);

        assertEquals("ref-corr", result.correlationId());
    }

    @Test
    @TestTransaction
    void sendMessageToUnknownChannelThrows() {
        assertThrows(Exception.class, () -> helper.sendMessage("no-such-channel", "alice", "status", "Hello", null, null, null, null, null, null, null, null, null));
    }

    // -----------------------------------------------------------------------
    // check_messages
    // -----------------------------------------------------------------------

    @Test
    @TestTransaction
    void checkMessagesReturnsMessagesAfterCursor() {
        helper.createChannel("check-ch-1", "Test", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        DispatchResult m1 = helper.sendMessage("check-ch-1", "alice", "status", "first", null, null, null, null, null, null, null, null, null);
        helper.sendMessage("check-ch-1", "bob", "status", "second", null, null, null, null, null, null, null, null, null);
        helper.sendMessage("check-ch-1", "carol", "status", "third", null, null, null, null, null, null, null, null, null);

        var result = helper.checkMessages("check-ch-1", m1.messageId(), 10, null, null, null);

        assertEquals(2, result.size());
        assertEquals("second", result.get(0).content());
        assertEquals("third", result.get(1).content());
    }

    @Test
    @TestTransaction
    void checkMessagesExcludesEventType() {
        helper.createChannel("check-ch-2", "Test", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        DispatchResult m1 = helper.sendMessage("check-ch-2", "alice", "status", "visible", null, null, null, null, null, null, null, null, null);
        helper.sendMessage("check-ch-2", "system", "event", null, null, null, null, null, null, null, null, null, null);
        helper.sendMessage("check-ch-2", "bob", "status", "also visible", null, null, null, null, null, null, null, null, null);

        var result = helper.checkMessages("check-ch-2", m1.messageId(), 10, null, null, null);

        assertEquals(1, result.size());
        assertEquals("also visible", result.get(0).content());
    }

    @Test
    @TestTransaction
    void checkMessagesFiltersBySender() {
        helper.createChannel("check-ch-3", "Test", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        helper.sendMessage("check-ch-3", "alice", "status", "from alice", null, null, null, null, null, null, null, null, null);
        helper.sendMessage("check-ch-3", "bob", "status", "from bob", null, null, null, null, null, null, null, null, null);

        var result = helper.checkMessages("check-ch-3", 0L, 10, "alice", null, null);

        assertEquals(1, result.size());
        assertEquals("alice", result.get(0).sender());
    }

    // -----------------------------------------------------------------------
    // get_replies
    // -----------------------------------------------------------------------

    @Test
    @TestTransaction
    void getRepliesReturnsDirectReplies() {
        helper.createChannel("replies-ch", "Test", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        DispatchResult request = helper.sendMessage("replies-ch", "alice", "query", "Q?", null, null, null, null, null, null, null, null, null);
        helper.sendMessage("replies-ch", "bob", "response", "A1", null, request.correlationId(), request.messageId(), null, null, null, null, null, null);
        helper.sendMessage("replies-ch", "carol", "response", "A2", null, request.correlationId(), request.messageId(), null, null, null, null, null, null);

        var replies = helper.getReplies(request.messageId(), null, null, null);

        assertEquals(2, replies.size());
    }

    @Test
    @TestTransaction
    void getRepliesReturnsEmptyWhenNoReplies() {
        helper.createChannel("noreplies-ch", "Test", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        DispatchResult msg = helper.sendMessage("noreplies-ch", "alice", "status", "standalone", null, null, null, null, null, null, null, null, null);

        var replies = helper.getReplies(msg.messageId(), null, null, null);

        assertTrue(replies.isEmpty());
    }

    // -----------------------------------------------------------------------
    // search_messages
    // -----------------------------------------------------------------------

    @Test
    @TestTransaction
    void searchMessagesFindsKeywordInContent() {
        helper.createChannel("search-ch-1", "Test", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        helper.sendMessage("search-ch-1", "alice", "status", "Found security vulnerability", null, null, null, null, null, null, null, null, null);
        helper.sendMessage("search-ch-1", "bob", "status", "Performance looks fine", null, null, null, null, null, null, null, null, null);

        var results = helper.searchMessages("security", null, 10, null);

        assertEquals(1, results.size());
        assertTrue(results.get(0).content().contains("security"));
    }

    @Test
    @TestTransaction
    void searchMessagesIsCaseInsensitive() {
        helper.createChannel("search-ch-2", "Test", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        helper.sendMessage("search-ch-2", "alice", "status", "CRITICAL: auth bypass", null, null, null, null, null, null, null, null, null);

        var results = helper.searchMessages("critical", null, 10, null);

        assertEquals(1, results.size());
    }

    @Test
    @TestTransaction
    void searchMessagesExcludesEventType() {
        helper.createChannel("search-ch-3", "Test", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        helper.sendMessage("search-ch-3", "system", "event", null, null, null, null, null, null, null, null, null, null);
        helper.sendMessage("search-ch-3", "alice", "status", "critical user message", null, null, null, null, null, null, null, null, null);

        var results = helper.searchMessages("critical", null, 10, null);

        // EVENT should be excluded
        assertEquals(1, results.size());
        assertEquals("alice", results.get(0).sender());
    }

    @Test
    @TestTransaction
    void searchMessagesWithChannelScope() {
        helper.createChannel("scoped-ch", "Scoped", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        helper.createChannel("other-ch", "Other", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        helper.sendMessage("scoped-ch", "alice", "status", "critical issue found", null, null, null, null, null, null, null, null, null);
        helper.sendMessage("other-ch", "bob", "status", "critical other issue", null, null, null, null, null, null, null, null, null);

        var results = helper.searchMessages("critical", "scoped-ch", 10, null);

        assertEquals(1, results.size(),
                "channel-scoped search should only return messages from the specified channel");
        assertEquals("alice", results.get(0).sender());
    }

    // -----------------------------------------------------------------------
    // check_messages — lastId cursor
    // -----------------------------------------------------------------------

    @Test
    @TestTransaction
    void checkMessagesLastIdIsIdOfLastReturnedMessage() {
        helper.createChannel("lastid-ch", "Test", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        helper.sendMessage("lastid-ch", "alice", "status", "first", null, null, null, null, null, null, null, null, null);
        DispatchResult last = helper.sendMessage("lastid-ch", "bob", "status", "second", null, null, null, null, null, null, null, null, null);

        var result = helper.checkMessages("lastid-ch", 0L, 10, null, null, null);

        assertEquals(last.messageId(), result.lastId(),
                "lastId should be the ID of the last returned message");
    }

    @Test
    @TestTransaction
    void checkMessagesEmptyPollReturnsInputCursorAsLastId() {
        helper.createChannel("empty-poll-ch", "Test", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        helper.sendMessage("empty-poll-ch", "alice", "status", "only message", null, null, null, null, null, null, null, null, null);

        // Poll with afterId beyond all existing messages
        var result = helper.checkMessages("empty-poll-ch", Long.MAX_VALUE - 1, 10, null, null, null);

        assertTrue(result.isEmpty());
        assertEquals(Long.MAX_VALUE - 1, result.lastId(),
                "empty poll should return the input cursor as lastId for stable re-polling");
    }

    @Test
    @TestTransaction
    void sendMessage_acceptsChannelUuid() {
        io.casehub.qhorus.api.channel.ChannelDetail created = helper.createChannel("uuid-msg-test", "Test", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        String uuid = created.channelId().toString();

        DispatchResult result = helper.sendMessage(uuid, "alice", "query", "hello via uuid", null, null, null, null, null, null, null, null, null);

        assertNotNull(result);
    }
}
