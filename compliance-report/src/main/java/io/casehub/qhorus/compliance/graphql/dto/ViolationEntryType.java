package io.casehub.qhorus.compliance.graphql.dto;

import io.casehub.qhorus.compliance.model.ViolationEntry;
import org.eclipse.microprofile.graphql.Type;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Type("ViolationEntry")
public record ViolationEntryType(
        Instant occurredAt,
        String sender,
        String messageType,
        String enforcementMode,
        List<String> violationSources,
        List<String> violations,
        String action,
        UUID ledgerEntryId) {

    public static ViolationEntryType from(ViolationEntry e) {
        return new ViolationEntryType(
                e.occurredAt(), e.sender(), e.messageType(), e.enforcementMode(),
                e.violationSources(), e.violations(), e.action(), e.ledgerEntryId());
    }
}
