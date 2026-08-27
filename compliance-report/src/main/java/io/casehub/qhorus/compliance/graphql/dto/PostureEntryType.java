package io.casehub.qhorus.compliance.graphql.dto;

import io.casehub.qhorus.api.spi.compliance.PostureEntry;
import io.casehub.qhorus.api.spi.compliance.PostureStatus;
import org.eclipse.microprofile.graphql.Type;

import java.time.Instant;

@Type("PostureEntry")
public record PostureEntryType(
        String category,
        PostureStatus status,
        String description,
        String evidence,
        Instant checkedAt) {

    public static PostureEntryType from(PostureEntry e) {
        return new PostureEntryType(e.category(), e.status(), e.description(), e.evidence(), e.checkedAt());
    }
}
