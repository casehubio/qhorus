package io.casehub.qhorus.compliance.model;

import java.time.Instant;

public record JudgmentEvent(
    String eventKind,
    String actorId,
    Instant occurredAt,
    Double evidenceQuality,
    String verificationOutcome,
    String escalationReason,
    Double trustScoreAtTime,
    Long durationMs
) {}
