package io.casehub.qhorus.api.audit;

import java.util.List;
import java.util.UUID;

public interface ReviewerProvider {

    List<String> resolve(UUID channelId, List<String> explicitReviewers,
            UUID entryId, String tenancyId);
}
