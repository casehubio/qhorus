package io.casehub.qhorus.api.compliance.report;

import java.time.Instant;

public record JudgmentEvent(
    String eventKind,
    String actorId,
    Instant occurredAt,
    Double evidenceQuality,
    String verificationOutcome,
    String escalationReason,
    Double trustScoreAtTime,
    Long durationMs,
    String reasoning
) {}
