package io.casehub.qhorus.runtime.api;

import java.util.List;

import io.casehub.qhorus.api.message.CommitmentState;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.runtime.message.Message;

class A2ATaskState {

    static String fromCommitmentState(CommitmentState state) {
        return switch (state) {
            case FULFILLED -> "completed";
            case DELEGATED, ACKNOWLEDGED -> "working"; // DELEGATED: retained for correctness if store behaviour changes
            case FAILED, DECLINED, EXPIRED -> "failed";
            case OPEN -> "submitted";
        };
    }

    static String fromMessageHistory(List<Message> messages) {
        if (messages.isEmpty()) return "submitted";
        return switch (messages.getLast().messageType) {
            case RESPONSE, DONE -> "completed";
            case FAILURE, DECLINE -> "failed";
            case STATUS, HANDOFF -> "working";
            default -> "submitted";
        };
    }
}
