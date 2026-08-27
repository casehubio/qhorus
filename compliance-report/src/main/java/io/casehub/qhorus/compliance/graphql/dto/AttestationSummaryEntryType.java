package io.casehub.qhorus.compliance.graphql.dto;

import io.casehub.qhorus.compliance.model.AttestationSummaryEntry;
import org.eclipse.microprofile.graphql.Type;

import java.time.Instant;
import java.util.UUID;

@Type("AttestationSummaryEntry")
public record AttestationSummaryEntryType(
        UUID entryId,
        String verdict,
        double confidence,
        String attestorId,
        Instant occurredAt) {

    public static AttestationSummaryEntryType from(AttestationSummaryEntry e) {
        return new AttestationSummaryEntryType(e.entryId(), e.verdict(), e.confidence(), e.attestorId(), e.occurredAt());
    }
}
