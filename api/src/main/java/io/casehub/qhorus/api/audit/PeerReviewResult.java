package io.casehub.qhorus.api.audit;

import java.util.List;

public record PeerReviewResult(
        int reviewersSent,
        List<ReviewDispatch> reviews,
        String advisory) {

    public record ReviewDispatch(String reviewerId, String correlationId) {}
}
