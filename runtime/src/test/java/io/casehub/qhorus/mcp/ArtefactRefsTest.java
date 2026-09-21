package io.casehub.qhorus.mcp;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.casehub.qhorus.testing.QhorusTestHelper;
import io.casehub.qhorus.testing.QhorusTestHelper.ArtefactDetail;
import io.casehub.qhorus.testing.QhorusTestHelper.CheckResult;
import io.casehub.qhorus.testing.QhorusTestHelper.MessageSummary;
import io.casehub.qhorus.testing.QhorusTestHelper.ArtefactDetail;
import io.casehub.qhorus.api.message.DispatchResult;
import io.casehub.qhorus.api.message.Message;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class ArtefactRefsTest {

    @Inject QhorusTestHelper helper;

    @Inject
    io.casehub.qhorus.api.store.ChannelStore channelStore;

    @Inject
    io.casehub.qhorus.api.store.MessageStore messageStore;

    @Test
    @TestTransaction
    void sendMessageWithArtefactRefsReturnsThemInCheckMessages() {
        helper.createChannel("arefs-ch-1", "Test", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        String uuid1 = helper.shareArtefact("aref-d1", "d", "alice", "content", false, true).artefactId().toString();
        String uuid2 = helper.shareArtefact("aref-d2", "d", "alice", "content", false, true).artefactId().toString();

        helper.sendMessage("arefs-ch-1", "alice", "status", "message with refs", null, null, null, List.of(uuid1, uuid2), null, null, null, null, null);

        CheckResult result = helper.checkMessages("arefs-ch-1", 0L, 10, null, null, null);

        assertEquals(1, result.size());
        var refs = result.get(0).artefactRefs();
        assertEquals(2, refs.size());
        assertTrue(refs.stream().anyMatch(r -> r.uri().equals(uuid1)));
        assertTrue(refs.stream().anyMatch(r -> r.uri().equals(uuid2)));
    }

    @Test
    @TestTransaction
    void sendMessageWithNoArtefactRefsHasEmptyListInMessageSummary() {
        helper.createChannel("arefs-ch-2", "Test", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        helper.sendMessage("arefs-ch-2", "alice", "status", "no refs", null, null, null, null, null, null, null, null, null);

        CheckResult result = helper.checkMessages("arefs-ch-2", 0L, 10, null, null, null);

        var refs = result.get(0).artefactRefs();
        assertNotNull(refs, "artefactRefs must be an empty list, never null");
        assertTrue(refs.isEmpty());
    }

    @Test
    @TestTransaction
    void sendMessageWithEmptyArtefactRefsListHasEmptyListInSummary() {
        helper.createChannel("arefs-ch-3", "Test", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        helper.sendMessage("arefs-ch-3", "alice", "status", "empty refs", null, null, null, List.of(), null, null, null, null, null);

        CheckResult result = helper.checkMessages("arefs-ch-3", 0L, 10, null, null, null);
        assertTrue(result.get(0).artefactRefs().isEmpty());
    }

    @Test
    @TestTransaction
    void sendMessageResultIncludesArtefactRefs() {
        helper.createChannel("arefs-ch-4", "Test", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        String uuid = helper.shareArtefact("aref-d4", "d", "alice", "content", false, true).artefactId().toString();

        DispatchResult result = helper.sendMessage("arefs-ch-4", "alice", "status", "with ref", null, null, null, List.of(uuid), null, null, null, null, null);

        assertNotNull(result.artefactRefs());
        assertEquals(1, result.artefactRefs().size());
        assertEquals(uuid, result.artefactRefs().get(0).uri());
    }

    @Test
    @TestTransaction
    void artefactRefsAppearsInGetReplies() {
        helper.createChannel("arefs-ch-5", "Test", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        String uuid = helper.shareArtefact("aref-d5", "d", "alice", "content", false, true).artefactId().toString();
        DispatchResult request = helper.sendMessage("arefs-ch-5", "alice", "query", "Question?", null, null, null, null, null, null, null, null, null);
        helper.sendMessage("arefs-ch-5", "bob", "response", "Answer with artefact", null, request.correlationId(), request.messageId(), List.of(uuid), null, null, null, null, null);

        List<MessageSummary> replies = helper.getReplies(request.messageId(), null, null, null);

        assertEquals(1, replies.size());
        assertTrue(replies.get(0).artefactRefs().stream().anyMatch(r -> r.uri().equals(uuid)));
    }

    @Test
    @TestTransaction
    void artefactRefsAppearsInSearchMessages() {
        helper.createChannel("arefs-ch-6", "Test", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        String uuid = helper.shareArtefact("aref-d6", "d", "alice", "content", false, true).artefactId().toString();
        helper.sendMessage("arefs-ch-6", "alice", "status", "analysis complete", null, null, null, List.of(uuid), null, null, null, null, null);

        List<MessageSummary> results = helper.searchMessages("analysis", null, 10, null);

        assertEquals(1, results.size());
        assertTrue(results.get(0).artefactRefs().stream().anyMatch(r -> r.uri().equals(uuid)));
    }

    @Test
    @TestTransaction
    void nullArtefactRefsStoredAsNullNotEmptyString() {
        helper.createChannel("arefs-ch-7", "Test", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        helper.sendMessage("arefs-ch-7", "alice", "status", "no refs", null, null, null, null, null, null, null, null, null);

        // Verify the artefactRefs is null, not an empty list
        java.util.UUID chId = channelStore.findByName("arefs-ch-7").orElseThrow().id();
        Message msg = messageStore.scan(io.casehub.qhorus.api.store.query.MessageQuery.builder()
                .channelId(chId).limit(1).build()).get(0);
        assertNull(msg.artefactRefs(), "artefactRefs should be null when no refs provided");
    }
}
