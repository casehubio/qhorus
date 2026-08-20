package io.casehub.qhorus.runtime.api;

import io.casehub.qhorus.api.message.CommitmentState;
import io.casehub.qhorus.api.message.Message;
import io.casehub.qhorus.api.message.MessageType;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

class A2ATaskStateMapper {

    static final Set<MessageType> TERMINAL_TYPES =
            Set.of(MessageType.DONE, MessageType.FAILURE, MessageType.DECLINE);

    static final Set<String> TERMINAL_STATES = Set.of("completed", "failed", "canceled");

    static String fromCommitmentState(final CommitmentState state) {
        return switch (state) {
            case FULFILLED -> "completed";
            case DELEGATED, ACKNOWLEDGED -> "working";
            case FAILED, EXPIRED -> "failed";
            case DECLINED -> "canceled";
            case OPEN -> "submitted";
        };
    }

    static String fromMessageType(final MessageType type) {
        return switch (type) {
            case DONE    -> "completed";
            case FAILURE -> "failed";
            case DECLINE -> "canceled";
            default      -> "working";
        };
    }

    static String fromMessageHistory(final List<Message> messages) {
        if (messages.isEmpty()) return "submitted";
        return messages.stream()
                .map(m -> statePriority(m.messageType()))
                .max(Comparator.naturalOrder())
                .map(A2ATaskStateMapper::fromPriority)
                .orElse("submitted");
    }

    private static int statePriority(final MessageType t) {
        return switch (t) {
            case DONE, RESPONSE -> 4;
            case FAILURE        -> 3;
            case DECLINE        -> 2;
            case STATUS, HANDOFF -> 1;
            default             -> 0;
        };
    }

    private static String fromPriority(final int p) {
        return switch (p) {
            case 4 -> "completed";
            case 3 -> "failed";
            case 2 -> "canceled";
            case 1 -> "working";
            default -> "submitted";
        };
    }
}
