package io.casehub.qhorus.api.message;

import io.smallrye.mutiny.Uni;

public interface ReactiveMessageDispatcher {
    Uni<DispatchResult> dispatch(MessageDispatch dispatch);
}
