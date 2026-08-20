package io.casehub.qhorus.runtime.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.casehub.qhorus.api.message.CommitmentState;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.message.Message;

class A2ATaskStateMapperTest {

    // -----------------------------------------------------------------------
    // fromCommitmentState
    // -----------------------------------------------------------------------

    @Test
    void openIsSubmitted() {
        assertEquals("submitted", A2ATaskStateMapper.fromCommitmentState(CommitmentState.OPEN));
    }

    @Test
    void acknowledgedIsWorking() {
        assertEquals("working", A2ATaskStateMapper.fromCommitmentState(CommitmentState.ACKNOWLEDGED));
    }

    @Test
    void fulfilledIsCompleted() {
        assertEquals("completed", A2ATaskStateMapper.fromCommitmentState(CommitmentState.FULFILLED));
    }

    @Test
    void delegatedIsWorking() {
        assertEquals("working", A2ATaskStateMapper.fromCommitmentState(CommitmentState.DELEGATED));
    }

    @Test
    void declinedIsCancelled() {
        // DECLINE is an explicit agent refusal, not an infrastructure failure. Refs #147.
        assertEquals("canceled", A2ATaskStateMapper.fromCommitmentState(CommitmentState.DECLINED));
    }

    @Test
    void failedIsFailed() {
        assertEquals("failed", A2ATaskStateMapper.fromCommitmentState(CommitmentState.FAILED));
    }

    @Test
    void expiredIsFailed() {
        assertEquals("failed", A2ATaskStateMapper.fromCommitmentState(CommitmentState.EXPIRED));
    }

    // -----------------------------------------------------------------------
    // fromMessageHistory
    // -----------------------------------------------------------------------

    @Test
    void emptyHistoryIsSubmitted() {
        assertEquals("submitted", A2ATaskStateMapper.fromMessageHistory(List.of()));
    }

    @Test
    void lastQueryIsSubmitted() {
        assertEquals("submitted", A2ATaskStateMapper.fromMessageHistory(List.of(msg(MessageType.QUERY))));
    }

    @Test
    void lastCommandIsSubmitted() {
        assertEquals("submitted", A2ATaskStateMapper.fromMessageHistory(List.of(msg(MessageType.COMMAND))));
    }

    @Test
    void lastEventIsSubmitted() {
        // telemetry event — does not advance task state
        assertEquals("submitted", A2ATaskStateMapper.fromMessageHistory(List.of(msg(MessageType.EVENT))));
    }

    @Test
    void lastStatusIsWorking() {
        assertEquals("working", A2ATaskStateMapper.fromMessageHistory(List.of(msg(MessageType.STATUS))));
    }

    @Test
    void lastHandoffIsWorking() {
        assertEquals("working", A2ATaskStateMapper.fromMessageHistory(List.of(msg(MessageType.HANDOFF))));
    }

    @Test
    void lastResponseIsCompleted() {
        assertEquals("completed", A2ATaskStateMapper.fromMessageHistory(List.of(msg(MessageType.RESPONSE))));
    }

    @Test
    void lastDoneIsCompleted() {
        assertEquals("completed", A2ATaskStateMapper.fromMessageHistory(List.of(msg(MessageType.DONE))));
    }

    @Test
    void lastFailureIsFailed() {
        assertEquals("failed", A2ATaskStateMapper.fromMessageHistory(List.of(msg(MessageType.FAILURE))));
    }

    @Test
    void lastDeclineIsCancelled() {
        // DECLINE is an explicit refusal → "canceled", not "failed". Refs #147.
        assertEquals("canceled", A2ATaskStateMapper.fromMessageHistory(List.of(msg(MessageType.DECLINE))));
    }

    @Test
    void maxPriority_orderDoesNotMatter_maxPriorityWins() {
        // Max-priority wins regardless of message order: STATUS (priority 1) beats QUERY (priority 0)
        assertEquals("working", A2ATaskStateMapper.fromMessageHistory(
                List.of(msg(MessageType.QUERY), msg(MessageType.STATUS))));
    }

    // -----------------------------------------------------------------------
    // Max-priority resolution (prevent state regression)
    // -----------------------------------------------------------------------

    @Test
    void maxPriority_doneBeforeQuery_returnsCompleted() {
        // Regression test: DONE (priority 4) should win even if QUERY comes after
        assertEquals("completed", A2ATaskStateMapper.fromMessageHistory(
                List.of(msg(MessageType.DONE), msg(MessageType.QUERY))));
    }

    @Test
    void maxPriority_responseBeforeCommand_returnsCompleted() {
        // RESPONSE (priority 4) before COMMAND (priority 0)
        assertEquals("completed", A2ATaskStateMapper.fromMessageHistory(
                List.of(msg(MessageType.RESPONSE), msg(MessageType.COMMAND))));
    }

    @Test
    void maxPriority_failureBeforeQuery_returnsFailed() {
        // FAILURE (priority 3) beats QUERY (priority 0)
        assertEquals("failed", A2ATaskStateMapper.fromMessageHistory(
                List.of(msg(MessageType.FAILURE), msg(MessageType.QUERY))));
    }

    @Test
    void maxPriority_declineBeforeStatus_returnsCancelled() {
        // DECLINE (priority 2) beats STATUS (priority 1) → "canceled". Refs #147.
        assertEquals("canceled", A2ATaskStateMapper.fromMessageHistory(
                List.of(msg(MessageType.DECLINE), msg(MessageType.STATUS))));
    }

    @Test
    void maxPriority_failureAndDecline_returnsFailedNotCancelled() {
        // FAILURE (priority 3) beats DECLINE (priority 2) → "failed"
        assertEquals("failed", A2ATaskStateMapper.fromMessageHistory(
                List.of(msg(MessageType.FAILURE), msg(MessageType.DECLINE))));
    }

    @Test
    void maxPriority_statusBeforeQuery_returnsWorking() {
        // STATUS (priority 1) beats QUERY (priority 0)
        assertEquals("working", A2ATaskStateMapper.fromMessageHistory(
                List.of(msg(MessageType.STATUS), msg(MessageType.QUERY))));
    }

    @Test
    void maxPriority_handoffBeforeCommand_returnsWorking() {
        // HANDOFF (priority 1) beats COMMAND (priority 0)
        assertEquals("working", A2ATaskStateMapper.fromMessageHistory(
                List.of(msg(MessageType.HANDOFF), msg(MessageType.COMMAND))));
    }

    @Test
    void maxPriority_noRegression_terminalStatesWin() {
        // DONE (priority 4) wins over earlier FAILURE (priority 3) — no regression
        assertEquals("completed", A2ATaskStateMapper.fromMessageHistory(
                List.of(msg(MessageType.FAILURE), msg(MessageType.DONE))));
    }

    @Test
    void maxPriority_multipleHighPriority_returnsHighestPriority() {
        // Multiple messages: QUERY, STATUS, FAILURE, RESPONSE — RESPONSE (4) wins
        assertEquals("completed", A2ATaskStateMapper.fromMessageHistory(
                List.of(
                        msg(MessageType.QUERY),
                        msg(MessageType.STATUS),
                        msg(MessageType.FAILURE),
                        msg(MessageType.RESPONSE))));
    }

    // -----------------------------------------------------------------------
    // fromMessageType (new — used by SSE streaming)
    // -----------------------------------------------------------------------

    @Test
    void fromMessageType_done_returnsCompleted() {
        assertEquals("completed", A2ATaskStateMapper.fromMessageType(MessageType.DONE));
    }

    @Test
    void fromMessageType_failure_returnsFailed() {
        assertEquals("failed", A2ATaskStateMapper.fromMessageType(MessageType.FAILURE));
    }

    @Test
    void fromMessageType_decline_returnsCancelled() {
        assertEquals("canceled", A2ATaskStateMapper.fromMessageType(MessageType.DECLINE));
    }

    @Test
    void fromMessageType_status_returnsWorking() {
        assertEquals("working", A2ATaskStateMapper.fromMessageType(MessageType.STATUS));
    }

    @Test
    void fromMessageType_response_returnsWorking() {
        assertEquals("working", A2ATaskStateMapper.fromMessageType(MessageType.RESPONSE));
    }

    @Test
    void fromMessageType_handoff_returnsWorking() {
        assertEquals("working", A2ATaskStateMapper.fromMessageType(MessageType.HANDOFF));
    }

    @Test
    void fromMessageType_query_returnsWorking() {
        assertEquals("working", A2ATaskStateMapper.fromMessageType(MessageType.QUERY));
    }

    @Test
    void fromMessageType_command_returnsWorking() {
        assertEquals("working", A2ATaskStateMapper.fromMessageType(MessageType.COMMAND));
    }

    @Test
    void fromMessageType_event_returnsWorking() {
        // EVENT is non-task telemetry — default arm, same as other non-terminal types
        assertEquals("working", A2ATaskStateMapper.fromMessageType(MessageType.EVENT));
    }

    // -----------------------------------------------------------------------
    // TERMINAL_TYPES constant
    // -----------------------------------------------------------------------

    @Test
    void terminalTypes_containsDoneFailureDecline() {
        assertThat(A2ATaskStateMapper.TERMINAL_TYPES)
                .containsExactlyInAnyOrder(MessageType.DONE, MessageType.FAILURE, MessageType.DECLINE);
    }

    @Test
    void terminalTypes_doesNotContainStatusOrResponse() {
        assertThat(A2ATaskStateMapper.TERMINAL_TYPES)
                .doesNotContain(MessageType.STATUS, MessageType.RESPONSE, MessageType.QUERY);
    }

    // -----------------------------------------------------------------------
    // TERMINAL_STATES constant
    // -----------------------------------------------------------------------

    @Test
    void terminalStates_containsAllThreeTerminalStrings() {
        assertThat(A2ATaskStateMapper.TERMINAL_STATES)
                .containsExactlyInAnyOrder("completed", "failed", "canceled");
    }

    // -----------------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------------

    private static Message msg(final MessageType type) {
        return Message.builder().messageType(type).build();
    }
}
