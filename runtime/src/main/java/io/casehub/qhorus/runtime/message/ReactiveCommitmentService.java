package io.casehub.qhorus.runtime.message;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import io.casehub.qhorus.api.message.Commitment;
import io.casehub.qhorus.api.message.CommitmentState;
import io.casehub.qhorus.api.message.MessageDispatch;
import io.casehub.qhorus.api.store.ReactiveCommitmentStore;
import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;

@IfBuildProperty(name = "casehub.qhorus.reactive.enabled", stringValue = "true")
@ApplicationScoped
public class ReactiveCommitmentService {

    @Inject
    ReactiveCommitmentStore store;

    @Inject
    Instance<io.opentelemetry.api.trace.Tracer> tracerInstance;

    @Inject
    io.casehub.qhorus.runtime.config.QhorusTracingConfig tracingConfig;

    public Uni<Optional<Commitment>> acknowledge(final String correlationId) {
        return transition(correlationId, CommitmentState.ACKNOWLEDGED, "qhorus.commitment.acknowledge", c ->
                c.toBuilder().state(CommitmentState.ACKNOWLEDGED)
                        .acknowledgedAt(c.acknowledgedAt() == null ? Instant.now() : c.acknowledgedAt())
                        .build());
    }

    public Uni<Optional<Commitment>> fulfill(final String correlationId) {
        return transition(correlationId, CommitmentState.FULFILLED, "qhorus.commitment.fulfill", c ->
                c.toBuilder().state(CommitmentState.FULFILLED).resolvedAt(Instant.now()).build());
    }

    public Uni<Optional<Commitment>> decline(final String correlationId) {
        return transition(correlationId, CommitmentState.DECLINED, "qhorus.commitment.decline", c ->
                c.toBuilder().state(CommitmentState.DECLINED).resolvedAt(Instant.now()).build());
    }

    public Uni<Optional<Commitment>> fail(final String correlationId) {
        return transition(correlationId, CommitmentState.FAILED, "qhorus.commitment.fail", c ->
                c.toBuilder().state(CommitmentState.FAILED).resolvedAt(Instant.now()).build());
    }

    public Uni<Optional<Commitment>> delegate(final String correlationId,
                                              final String delegatedTo) {
        if (correlationId == null || correlationId.isBlank()) {
            return Uni.createFrom().item(Optional.empty());
        }

        // ── Start tracing span ────────────────────────────────────────────────
        io.opentelemetry.api.trace.Span span = null;
        io.opentelemetry.context.Scope scope = null;
        if (tracingConfig.enabled() && tracingConfig.commitments() && tracerInstance.isResolvable()) {
            span = tracerInstance.get().spanBuilder("qhorus.commitment.delegate")
                    .setSpanKind(io.opentelemetry.api.trace.SpanKind.INTERNAL)
                    .startSpan();
            scope = span.makeCurrent();
        }
        final io.opentelemetry.api.trace.Span finalSpan = span;
        final io.opentelemetry.context.Scope finalScope = scope;

        return Panache.withTransaction("qhorus", () ->
            store.findByCorrelationId(correlationId).flatMap(opt -> {
                if (opt.isEmpty() || opt.get().state().isTerminal()) {
                    return Uni.createFrom().item(Optional.<Commitment>empty());
                }
                final Commitment c = opt.get();
                if (finalSpan != null) {
                    finalSpan.setAttribute("qhorus.commitment.id", c.id().toString());
                    finalSpan.setAttribute("qhorus.commitment.correlation_id", correlationId);
                    finalSpan.setAttribute("qhorus.commitment.from_state", c.state().name());
                    finalSpan.setAttribute("qhorus.commitment.to_state", "DELEGATED");
                    finalSpan.setAttribute("qhorus.commitment.obligor", c.obligor() != null ? c.obligor() : "");
                    finalSpan.setAttribute("qhorus.commitment.delegated_to", delegatedTo);
                    finalSpan.setAttribute("qhorus.channel.id", c.channelId().toString());
                }
                Commitment delegated = c.toBuilder()
                        .state(CommitmentState.DELEGATED)
                        .delegatedTo(delegatedTo)
                        .resolvedAt(Instant.now())
                        .build();
                return store.save(delegated).flatMap(saved -> {
                    Commitment child = Commitment.builder()
                            .correlationId(correlationId)
                            .channelId(c.channelId())
                            .messageType(c.messageType())
                            .requester(c.requester())
                            .obligor(delegatedTo)
                            .expiresAt(c.expiresAt())
                            .state(CommitmentState.OPEN)
                            .parentCommitmentId(c.id())
                            .build();
                    return store.save(child).map(ignored -> Optional.of(saved));
                });
            })
        )
        .onFailure().invoke(t -> {
            if (finalSpan != null) {
                finalSpan.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR);
                finalSpan.recordException(t);
            }
        })
        .onTermination().invoke(() -> {
            if (finalScope != null) finalScope.close();
            if (finalSpan != null) finalSpan.end();
        });
    }

    public Uni<Integer> expireOverdue() {
        // ── Start tracing span (root span, no parent) ─────────────────────────
        io.opentelemetry.api.trace.Span span = null;
        io.opentelemetry.context.Scope scope = null;
        if (tracingConfig.enabled() && tracingConfig.commitments() && tracerInstance.isResolvable()) {
            span = tracerInstance.get().spanBuilder("qhorus.commitment.expire_overdue")
                    .setSpanKind(io.opentelemetry.api.trace.SpanKind.INTERNAL)
                    .setNoParent()
                    .startSpan();
            scope = span.makeCurrent();
        }
        final io.opentelemetry.api.trace.Span finalSpan = span;
        final io.opentelemetry.context.Scope finalScope = scope;

        return Panache.withTransaction("qhorus", () ->
            store.findExpiredBefore(Instant.now()).flatMap(overdue -> {
                if (overdue.isEmpty()) {
                    return Uni.createFrom().item(0);
                }
                if (finalSpan != null) {
                    finalSpan.setAttribute("qhorus.commitment.expired_count", overdue.size());
                }
                final List<Uni<Commitment>> saves = overdue.stream().map(c -> {
                    Commitment expired = c.toBuilder()
                            .state(CommitmentState.EXPIRED)
                            .resolvedAt(Instant.now())
                            .build();
                    return store.save(expired);
                }).toList();
                return Uni.join().all(saves).andFailFast().map(List::size);
            })
        )
        .onFailure().invoke(t -> {
            if (finalSpan != null) {
                finalSpan.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR);
                finalSpan.recordException(t);
            }
        })
        .onTermination().invoke(() -> {
            if (finalScope != null) finalScope.close();
            if (finalSpan != null) finalSpan.end();
        });
    }

    public Uni<Optional<Commitment>> extendDeadline(final String correlationId,
                                                    final Instant newDeadline) {
        if (correlationId == null || correlationId.isBlank()) {
            return Uni.createFrom().item(Optional.empty());
        }

        // ── Start tracing span ────────────────────────────────────────────────
        io.opentelemetry.api.trace.Span span = null;
        io.opentelemetry.context.Scope scope = null;
        if (tracingConfig.enabled() && tracingConfig.commitments() && tracerInstance.isResolvable()) {
            span = tracerInstance.get().spanBuilder("qhorus.commitment.extend_deadline")
                    .setSpanKind(io.opentelemetry.api.trace.SpanKind.INTERNAL)
                    .startSpan();
            scope = span.makeCurrent();
        }
        final io.opentelemetry.api.trace.Span finalSpan = span;
        final io.opentelemetry.context.Scope finalScope = scope;

        return Panache.withTransaction("qhorus", () ->
            store.findByCorrelationId(correlationId).flatMap(opt -> {
                if (opt.isEmpty() || opt.get().state().isTerminal()) {
                    return Uni.createFrom().item(Optional.<Commitment>empty());
                }
                final Commitment c = opt.get();
                if (finalSpan != null) {
                    finalSpan.setAttribute("qhorus.commitment.id", c.id().toString());
                    finalSpan.setAttribute("qhorus.commitment.correlation_id", correlationId);
                    finalSpan.setAttribute("qhorus.channel.id", c.channelId().toString());
                }
                Commitment updated = c.toBuilder().expiresAt(newDeadline).build();
                return store.save(updated).map(Optional::of);
            })
        )
        .onFailure().invoke(t -> {
            if (finalSpan != null) {
                finalSpan.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR);
                finalSpan.recordException(t);
            }
        })
        .onTermination().invoke(() -> {
            if (finalScope != null) finalScope.close();
            if (finalSpan != null) finalSpan.end();
        });
    }

    public Uni<Optional<Commitment>> findByCorrelationId(final String correlationId) {
        if (correlationId == null || correlationId.isBlank()) {
            return Uni.createFrom().item(Optional.empty());
        }
        return store.findByCorrelationId(correlationId);
    }

    @SuppressWarnings("unused")
    Uni<Void> updateState(final MessageDispatch dispatch, final UUID commitmentId) {
        final String correlationId = dispatch.correlationId();
        if (correlationId == null) {
            return Uni.createFrom().voidItem();
        }
        return switch (dispatch.type()) {
            case STATUS -> acknowledge(correlationId).replaceWithVoid();
            case RESPONSE, DONE -> fulfill(correlationId).replaceWithVoid();
            case DECLINE -> decline(correlationId).replaceWithVoid();
            case FAILURE -> fail(correlationId).replaceWithVoid();
            case HANDOFF -> delegate(correlationId, dispatch.target()).replaceWithVoid();
            default -> Uni.createFrom().voidItem();
        };
    }

    private Uni<Optional<Commitment>> transition(final String correlationId,
                                                 final CommitmentState target,
                                                 final String spanName,
                                                 final java.util.function.UnaryOperator<Commitment> update) {
        if (correlationId == null || correlationId.isBlank()) {
            return Uni.createFrom().item(Optional.empty());
        }

        // ── Start tracing span ────────────────────────────────────────────────
        io.opentelemetry.api.trace.Span span = null;
        io.opentelemetry.context.Scope scope = null;
        if (tracingConfig.enabled() && tracingConfig.commitments() && tracerInstance.isResolvable()) {
            span = tracerInstance.get().spanBuilder(spanName)
                    .setSpanKind(io.opentelemetry.api.trace.SpanKind.INTERNAL)
                    .startSpan();
            scope = span.makeCurrent();
        }
        final io.opentelemetry.api.trace.Span finalSpan = span;
        final io.opentelemetry.context.Scope finalScope = scope;

        return Panache.withTransaction("qhorus", () ->
            store.findByCorrelationId(correlationId).flatMap(opt -> {
                if (opt.isEmpty() || opt.get().state().isTerminal()) {
                    return Uni.createFrom().item(Optional.<Commitment>empty());
                }
                final Commitment c = opt.get();
                if (finalSpan != null) {
                    finalSpan.setAttribute("qhorus.commitment.id", c.id().toString());
                    finalSpan.setAttribute("qhorus.commitment.correlation_id", correlationId);
                    finalSpan.setAttribute("qhorus.commitment.from_state", c.state().name());
                    finalSpan.setAttribute("qhorus.commitment.to_state", target.name());
                    finalSpan.setAttribute("qhorus.commitment.obligor", c.obligor() != null ? c.obligor() : "");
                    finalSpan.setAttribute("qhorus.channel.id", c.channelId().toString());
                }
                Commitment updated = update.apply(c);
                return store.save(updated).map(Optional::of);
            })
        )
        .onFailure().invoke(t -> {
            if (finalSpan != null) {
                finalSpan.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR);
                finalSpan.recordException(t);
            }
        })
        .onTermination().invoke(() -> {
            if (finalScope != null) finalScope.close();
            if (finalSpan != null) finalSpan.end();
        });
    }
}
