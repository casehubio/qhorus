package io.casehub.qhorus.runtime.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.casehub.qhorus.api.message.CommitmentState;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.runtime.message.Message;

class A2ATaskStateTest {

    // -----------------------------------------------------------------------
    // fromCommitmentState
    // -----------------------------------------------------------------------

    @Test
    void openIsSubmitted() {
        assertEquals("submitted", A2ATaskState.fromCommitmentState(CommitmentState.OPEN));
    }

    @Test
    void acknowledgedIsWorking() {
        assertEquals("working", A2ATaskState.fromCommitmentState(CommitmentState.ACKNOWLEDGED));
    }

    @Test
    void fulfilledIsCompleted() {
        assertEquals("completed", A2ATaskState.fromCommitmentState(CommitmentState.FULFILLED));
    }

    @Test
    void delegatedIsWorking() {
        assertEquals("working", A2ATaskState.fromCommitmentState(CommitmentState.DELEGATED));
    }

    @Test
    void declinedIsFailed() {
        assertEquals("failed", A2ATaskState.fromCommitmentState(CommitmentState.DECLINED));
    }

    @Test
    void failedIsFailed() {
        assertEquals("failed", A2ATaskState.fromCommitmentState(CommitmentState.FAILED));
    }

    @Test
    void expiredIsFailed() {
        assertEquals("failed", A2ATaskState.fromCommitmentState(CommitmentState.EXPIRED));
    }

    // -----------------------------------------------------------------------
    // fromMessageHistory
    // -----------------------------------------------------------------------

    @Test
    void emptyHistoryIsSubmitted() {
        assertEquals("submitted", A2ATaskState.fromMessageHistory(List.of()));
    }

    @Test
    void lastQueryIsSubmitted() {
        assertEquals("submitted", A2ATaskState.fromMessageHistory(List.of(msg(MessageType.QUERY))));
    }

    @Test
    void lastCommandIsSubmitted() {
        assertEquals("submitted", A2ATaskState.fromMessageHistory(List.of(msg(MessageType.COMMAND))));
    }

    @Test
    void lastEventIsSubmitted() {
        // telemetry event — does not advance task state
        assertEquals("submitted", A2ATaskState.fromMessageHistory(List.of(msg(MessageType.EVENT))));
    }

    @Test
    void lastStatusIsWorking() {
        assertEquals("working", A2ATaskState.fromMessageHistory(List.of(msg(MessageType.STATUS))));
    }

    @Test
    void lastHandoffIsWorking() {
        assertEquals("working", A2ATaskState.fromMessageHistory(List.of(msg(MessageType.HANDOFF))));
    }

    @Test
    void lastResponseIsCompleted() {
        assertEquals("completed", A2ATaskState.fromMessageHistory(List.of(msg(MessageType.RESPONSE))));
    }

    @Test
    void lastDoneIsCompleted() {
        assertEquals("completed", A2ATaskState.fromMessageHistory(List.of(msg(MessageType.DONE))));
    }

    @Test
    void lastFailureIsFailed() {
        assertEquals("failed", A2ATaskState.fromMessageHistory(List.of(msg(MessageType.FAILURE))));
    }

    @Test
    void lastDeclineIsFailed() {
        assertEquals("failed", A2ATaskState.fromMessageHistory(List.of(msg(MessageType.DECLINE))));
    }

    @Test
    void lastMessageDeterminesState() {
        // earlier messages don't matter — only the last one
        assertEquals("working", A2ATaskState.fromMessageHistory(
                List.of(msg(MessageType.QUERY), msg(MessageType.STATUS))));
    }

    private static Message msg(MessageType type) {
        Message m = new Message();
        m.messageType = type;
        return m;
    }
}
