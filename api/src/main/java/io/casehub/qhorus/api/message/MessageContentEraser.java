package io.casehub.qhorus.api.message;

import java.util.UUID;

public interface MessageContentEraser {

    ErasureResult erase(UUID ledgerEntryId, String reason);
}
