package io.casehub.qhorus.runtime.message;

import io.casehub.qhorus.api.message.Commitment;
import io.casehub.qhorus.api.message.MessageDispatch;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.store.CommitmentStore;
import io.casehub.qhorus.api.store.MessageStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class CorrelationIntegrityChecker {

    private static final Set<MessageType> TERMINAL_TYPES = Set.of(
            MessageType.DONE, MessageType.FAILURE, MessageType.DECLINE,
            MessageType.RESPONSE, MessageType.HANDOFF);

    @Inject
    CommitmentStore commitmentStore;

    @Inject
    MessageStore messageStore;

    public List<String> check(MessageDispatch dispatch, UUID channelId) {
        List<String> advisories = new ArrayList<>();
        checkInReplyTo(dispatch, channelId, advisories);
        checkObligationIntegrity(dispatch, channelId, advisories);
        return List.copyOf(advisories);
    }

    private void checkInReplyTo(MessageDispatch dispatch, UUID channelId,
                                List<String> advisories) {
        if (dispatch.inReplyTo() == null) return;
        var parent = messageStore.find(dispatch.inReplyTo());
        if (parent.isEmpty()) {
            advisories.add("inReplyTo references non-existent message ID " + dispatch.inReplyTo());
        } else if (!parent.get().channelId().equals(channelId)) {
            advisories.add("inReplyTo references message in different channel");
        }
    }

    private void checkObligationIntegrity(MessageDispatch dispatch, UUID channelId,
                                          List<String> advisories) {
        if (dispatch.correlationId() == null) return;
        if (!TERMINAL_TYPES.contains(dispatch.type())) return;

        var commitment = commitmentStore.findByCorrelationId(dispatch.correlationId());
        if (commitment.isEmpty()) return;

        Commitment c = commitment.get();

        if (c.messageType() == MessageType.COMMAND && dispatch.type() == MessageType.RESPONSE) {
            advisories.add("RESPONSE used to resolve COMMAND obligation — expected DONE/FAILURE/DECLINE");
        }
        if (c.messageType() == MessageType.QUERY
                && (dispatch.type() == MessageType.DONE || dispatch.type() == MessageType.FAILURE)) {
            advisories.add(dispatch.type() + " used to resolve QUERY obligation — expected RESPONSE/DECLINE");
        }

        if (c.obligor() != null
                && !dispatch.sender().equals(c.obligor())
                && (c.delegatedTo() == null || !dispatch.sender().equals(c.delegatedTo()))) {
            advisories.add("Sender '" + dispatch.sender() + "' is not the obligor ('"
                    + c.obligor() + "') for correlationId '" + dispatch.correlationId() + "'");
        }

        if (!c.channelId().equals(channelId)) {
            advisories.add("Resolving obligation from different channel (obligation channel: "
                    + c.channelId() + ", dispatch channel: " + channelId + ")");
        }
    }
}
