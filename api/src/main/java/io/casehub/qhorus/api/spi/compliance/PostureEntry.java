package io.casehub.qhorus.api.spi.compliance;

import java.time.Instant;

public record PostureEntry(
        String category,
        PostureStatus status,
        String description,
        String evidence,
        Instant checkedAt) {
}
