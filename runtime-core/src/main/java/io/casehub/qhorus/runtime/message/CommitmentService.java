package io.casehub.qhorus.runtime.message;

import io.casehub.qhorus.api.message.Commitment;
import io.casehub.qhorus.api.message.CommitmentDeclinedEvent;
import io.casehub.qhorus.api.message.CommitmentExpiredEvent;
import io.casehub.qhorus.api.message.CommitmentState;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.store.CommitmentStore;
import io.casehub.qhorus.runtime.config.QhorusTracingConfig;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class CommitmentService {

    private static final Logger LOG = Logger.getLogger(CommitmentService.class);

    CommitmentStore store;
    Consumer<CommitmentDeclinedEvent> declinedConsumer;
    Consumer<CommitmentExpiredEvent> expiredConsumer;
    Supplier<Tracer> tracerSupplier;
    QhorusTracingConfig tracingConfig;


    CommitmentService() {}

    public CommitmentService(CommitmentStore store,
                             Consumer<CommitmentDeclinedEvent> declinedConsumer,
                             Consumer<CommitmentExpiredEvent> expiredConsumer,
                             Supplier<Tracer> tracerSupplier,
                             QhorusTracingConfig tracingConfig) {
        this.store = store;
        this.declinedConsumer = declinedConsumer;
        this.expiredConsumer = expiredConsumer;
        this.tracerSupplier = tracerSupplier;
        this.tracingConfig = tracingConfig;
    }

    @Transactional
    public Commitment open(UUID commitmentId, String correlationId, UUID channelId,
                           MessageType type, String requester, String obligor, Instant expiresAt) {
        return open(commitmentId, correlationId, channelId, type, requester, obligor,
                    expiresAt, null, null);
    }

    @Transactional
    public Commitment open(UUID commitmentId, String correlationId, UUID channelId,
                           MessageType type, String requester, String obligor,
                           Instant expiresAt, String tenancyId, String capabilityTag) {
        Span span = null;
        if (tracingConfig.enabled() && tracingConfig.commitments() && tracerSupplier != null) {
            span = tracerSupplier.get().spanBuilder("qhorus.commitment.open")
                                 .setSpanKind(SpanKind.INTERNAL)
                                 .startSpan();
            span.setAttribute("qhorus.commitment.id", commitmentId.toString());
            span.setAttribute("qhorus.commitment.correlation_id", correlationId);
            span.setAttribute("qhorus.commitment.to_state", "OPEN");
            span.setAttribute("qhorus.commitment.obligor", obligor != null ? obligor : "");
            span.setAttribute("qhorus.channel.id", channelId.toString());
        }
        try {
            Commitment c = Commitment.builder()
                                     .id(commitmentId)
                                     .correlationId(correlationId)
                                     .channelId(channelId)
                                     .messageType(type)
                                     .requester(requester)
                                     .obligor(obligor)
                                     .expiresAt(expiresAt)
                                     .tenancyId(tenancyId)
                                     .capabilityTag(capabilityTag)
                                     .state(CommitmentState.OPEN)
                                     .build();
            return store.save(c);
        } catch (Exception e) {
            if (span != null) {
                span.setStatus(StatusCode.ERROR);
                span.recordException(e);
            }
            throw e;
        } finally {
            if (span != null) { span.end(); }
        }
    }

    @Transactional
    public Optional<Commitment> acknowledge(String correlationId) {
        if (correlationId == null || correlationId.isBlank()) return Optional.empty();
        Span span = startSpan("qhorus.commitment.acknowledge");
        try {
            return store.findByCorrelationId(correlationId)
                    .filter(c -> c.state().isActive())
                    .map(c -> {
                        setSpanAttrs(span, c, correlationId, "ACKNOWLEDGED");
                        return store.save(c.toBuilder()
                                .state(CommitmentState.ACKNOWLEDGED)
                                .acknowledgedAt(c.acknowledgedAt() == null ? Instant.now() : c.acknowledgedAt())
                                .build());
                    });
        } catch (Exception e) {
            recordError(span, e);
            throw e;
        } finally {
            endSpan(span);
        }
    }

    @Transactional
    public Optional<Commitment> fulfill(String correlationId) {
        if (correlationId == null || correlationId.isBlank()) return Optional.empty();
        Span span = startSpan("qhorus.commitment.fulfill");
        try {
            return store.findByCorrelationId(correlationId)
                    .filter(c -> c.state().isActive())
                    .map(c -> {
                        setSpanAttrs(span, c, correlationId, "FULFILLED");
                        return store.save(c.toBuilder()
                                .state(CommitmentState.FULFILLED)
                                .resolvedAt(Instant.now())
                                .build());
                    });
        } catch (Exception e) {
            recordError(span, e);
            throw e;
        } finally {
            endSpan(span);
        }
    }

    @Transactional
    public Optional<Commitment> decline(String correlationId) {
        if (correlationId == null || correlationId.isBlank()) return Optional.empty();
        Span span = startSpan("qhorus.commitment.decline");
        try {
            return store.findByCorrelationId(correlationId)
                    .filter(c -> c.state().isActive())
                    .map(c -> {
                        setSpanAttrs(span, c, correlationId, "DECLINED");
                        Commitment saved = store.save(c.toBuilder()
                                .state(CommitmentState.DECLINED)
                                .resolvedAt(Instant.now())
                                .build());
                        declinedConsumer.accept(new CommitmentDeclinedEvent(
                                saved.id(), saved.correlationId(), saved.channelId(),
                                saved.obligor(), saved.requester()));
                        return saved;
                    });
        } catch (Exception e) {
            recordError(span, e);
            throw e;
        } finally {
            endSpan(span);
        }
    }

    @Transactional
    public Optional<Commitment> fail(String correlationId) {
        if (correlationId == null || correlationId.isBlank()) return Optional.empty();
        Span span = startSpan("qhorus.commitment.fail");
        try {
            return store.findByCorrelationId(correlationId)
                    .filter(c -> c.state().isActive())
                    .map(c -> {
                        setSpanAttrs(span, c, correlationId, "FAILED");
                        return store.save(c.toBuilder()
                                .state(CommitmentState.FAILED)
                                .resolvedAt(Instant.now())
                                .build());
                    });
        } catch (Exception e) {
            recordError(span, e);
            throw e;
        } finally {
            endSpan(span);
        }
    }

    @Transactional
    public Optional<Commitment> delegate(String correlationId, String delegatedTo) {
        if (correlationId == null || correlationId.isBlank()) return Optional.empty();
        Span span = startSpan("qhorus.commitment.delegate");
        try {
            return store.findByCorrelationId(correlationId)
                    .filter(c -> c.state().isActive())
                    .map(c -> {
                        setSpanAttrs(span, c, correlationId, "DELEGATED");
                        Commitment delegated = store.save(c.toBuilder()
                                .state(CommitmentState.DELEGATED)
                                .delegatedTo(delegatedTo)
                                .resolvedAt(Instant.now())
                                .build());
                        Commitment child = Commitment.builder()
                                .correlationId(correlationId)
                                .channelId(c.channelId())
                                .messageType(c.messageType())
                                .requester(c.requester())
                                .obligor(delegatedTo)
                                .expiresAt(c.expiresAt())
                                .state(CommitmentState.OPEN)
                                .parentCommitmentId(c.id())
                                .tenancyId(c.tenancyId())
                                .capabilityTag(c.capabilityTag())
                                .build();
                        store.save(child);
                        return delegated;
                    });
        } catch (Exception e) {
            recordError(span, e);
            throw e;
        } finally {
            endSpan(span);
        }
    }

    @Transactional
    public int expireOverdue() {
        Span span = null;
        if (tracingConfig.enabled() && tracingConfig.commitments() && tracerSupplier != null) {
            span = tracerSupplier.get().spanBuilder("qhorus.commitment.expire_overdue")
                    .setSpanKind(SpanKind.INTERNAL)
                    .setNoParent()
                    .startSpan();
        }
        final Span finalSpan = span;
        try {
            List<Commitment> overdue = store.findExpiredBefore(Instant.now());
            List<CommitmentExpiredEvent> toFire = new ArrayList<>(overdue.size());
            overdue.forEach(c -> {
                if (finalSpan != null) {
                    finalSpan.addEvent("qhorus.commitment.expired",
                            Attributes.of(
                                    AttributeKey.stringKey("commitment_id"), c.id().toString(),
                                    AttributeKey.stringKey("correlation_id"), c.correlationId(),
                                    AttributeKey.stringKey("obligor"), c.obligor() != null ? c.obligor() : ""));
                }
                store.save(c.toBuilder()
                        .state(CommitmentState.EXPIRED)
                        .resolvedAt(Instant.now())
                        .build());
                toFire.add(new CommitmentExpiredEvent(
                        c.id(), c.correlationId(), c.channelId(), c.obligor(), c.requester(), c.expiresAt()));
            });
            if (finalSpan != null) {
                finalSpan.setAttribute("qhorus.commitment.expired_count", overdue.size());
            }
            toFire.forEach(event -> {
                try {
                    expiredConsumer.accept(event);
                } catch (Exception e) {
                    LOG.warnf(e, "CommitmentExpiredEvent observer failed for commitment %s — continuing", event.commitmentId());
                }
            });
            return overdue.size();
        } catch (Exception e) {
            recordError(span, e);
            throw e;
        } finally {
            endSpan(span);
        }
    }

    @Transactional
    public Optional<Commitment> extendDeadline(String correlationId, Instant newDeadline) {
        if (correlationId == null || correlationId.isBlank()) return Optional.empty();
        Span span = startSpan("qhorus.commitment.extend_deadline");
        try {
            return store.findByCorrelationId(correlationId)
                    .filter(c -> c.state().isActive())
                    .map(c -> {
                        if (span != null) {
                            setSpanAttrs(span, c, correlationId, c.state().name());
                            span.setAttribute("qhorus.commitment.new_deadline", newDeadline.toString());
                        }
                        return store.save(c.toBuilder().expiresAt(newDeadline).build());
                    });
        } catch (Exception e) {
            recordError(span, e);
            throw e;
        } finally {
            endSpan(span);
        }
    }

    public Optional<Commitment> findByCorrelationId(String correlationId) {
        if (correlationId == null || correlationId.isBlank()) return Optional.empty();
        return store.findByCorrelationId(correlationId);
    }

    @Transactional
    public int expireByChannel(UUID channelId) {
        List<Commitment> active = store.findOpenByChannelId(channelId);
        List<CommitmentExpiredEvent> toFire = new ArrayList<>(active.size());
        Instant now = Instant.now();
        active.forEach(c -> {
            if (c.state().isTerminal()) return;
            store.save(c.toBuilder()
                    .state(CommitmentState.EXPIRED)
                    .resolvedAt(now)
                    .expiresAt(now)
                    .build());
            toFire.add(new CommitmentExpiredEvent(
                    c.id(), c.correlationId(), channelId, c.obligor(), c.requester(), now));
        });
        toFire.forEach(event -> {
            try {
                expiredConsumer.accept(event);
            } catch (Exception e) {
                LOG.warnf(e, "CommitmentExpiredEvent observer failed for commitment %s — continuing", event.commitmentId());
            }
        });
        return toFire.size();
    }

    private Span startSpan(String name) {
        if (tracingConfig.enabled() && tracingConfig.commitments() && tracerSupplier != null) {
            return tracerSupplier.get().spanBuilder(name)
                    .setSpanKind(SpanKind.INTERNAL)
                    .startSpan();
        }
        return null;
    }

    private void setSpanAttrs(Span span, Commitment c, String correlationId, String toState) {
        if (span == null) return;
        span.setAttribute("qhorus.commitment.id", c.id().toString());
        span.setAttribute("qhorus.commitment.correlation_id", correlationId);
        span.setAttribute("qhorus.commitment.from_state", c.state().name());
        span.setAttribute("qhorus.commitment.to_state", toState);
        span.setAttribute("qhorus.commitment.obligor", c.obligor() != null ? c.obligor() : "");
        span.setAttribute("qhorus.channel.id", c.channelId().toString());
    }

    private void recordError(Span span, Exception e) {
        if (span != null) {
            span.setStatus(StatusCode.ERROR);
            span.recordException(e);
        }
    }

    private void endSpan(Span span) {
        if (span != null) span.end();
    }
}
