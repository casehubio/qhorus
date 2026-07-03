package io.casehub.qhorus.api.message;

public interface MessageDispatcher {
    DispatchResult dispatch(MessageDispatch dispatch);
}
