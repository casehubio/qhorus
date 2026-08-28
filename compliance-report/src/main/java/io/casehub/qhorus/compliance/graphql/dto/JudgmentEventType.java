package io.casehub.qhorus.compliance.graphql.dto;

import io.casehub.qhorus.compliance.model.JudgmentEvent;
import org.eclipse.microprofile.graphql.Type;

import java.time.Instant;

@Type("JudgmentEvent")
public record JudgmentEventType(
        String eventKind, String actorId, Instant occurredAt,
        Double evidenceQuality, String verificationOutcome, String escalationReason,
        Double trustScoreAtTime, Long durationMs, String reasoning) {

    public static JudgmentEventType from(JudgmentEvent e) {
        return new JudgmentEventType(e.eventKind(), e.actorId(), e.occurredAt(),
                e.evidenceQuality(), e.verificationOutcome(), e.escalationReason(),
                e.trustScoreAtTime(), e.durationMs(), e.reasoning());
    }
}
