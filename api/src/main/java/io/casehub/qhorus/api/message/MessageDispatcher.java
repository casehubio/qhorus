package io.casehub.qhorus.api.message;

public interface MessageDispatcher {
    DispatchResult dispatch(MessageDispatch dispatch);

    default DispatchResult broadcast(String capabilityTag, MessageType type, String content,
                                     String sender, String tenancyId) {
        throw new UnsupportedOperationException("broadcast not implemented");
    }
}
