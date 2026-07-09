package io.casehub.qhorus.runtime.message;

import static org.assertj.core.api.Assertions.*;

import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.smallrye.mutiny.Uni;

import org.junit.jupiter.api.Test;

/**
 * Verifies the Mutiny operator ordering pattern for reactive tracing: onFailure before onTermination.
 *
 * <p>In Mutiny, operators closer to the upstream fire first. For tracing spans:
 * <ol>
 *   <li>onFailure records the error while the span is still active</li>
 *   <li>onTermination ends the span</li>
 * </ol>
 *
 * <p>Reversing this order silently loses errors — mutations to an ended span are no-ops.
 */
class ReactiveTracingTest {

    @Test
    void onFailure_before_onTermination_records_error_before_span_end() {
        // ── Setup: in-memory span exporter ────────────────────────────────────
        final var exporter = InMemorySpanExporter.create();
        final var tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
        final var tracer = tracerProvider.get("test");

        // ── Execute: failing Uni with correct operator ordering ───────────────
        final var span = tracer.spanBuilder("test.operation").startSpan();
        final var scope = span.makeCurrent();

        final var result = Uni.createFrom().<String>failure(new IllegalStateException("test error"))
                .onFailure().invoke(t -> {
                    // Fires first — span still active, error is recorded
                    span.setStatus(StatusCode.ERROR);
                    span.recordException(t);
                })
                .onTermination().invoke(() -> {
                    // Fires second — closes scope and ends span
                    scope.close();
                    span.end();
                });

        // Trigger the failure path
        result.onFailure().recoverWithNull().await().indefinitely();

        // ── Verify: span captured ERROR status before it ended ────────────────
        final var spans = exporter.getFinishedSpanItems();
        assertThat(spans).hasSize(1);

        final SpanData spanData = spans.get(0);
        assertThat(spanData.getName()).isEqualTo("test.operation");
        assertThat(spanData.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
        assertThat(spanData.getEvents()).anySatisfy(event ->
                assertThat(event.getName()).isEqualTo("exception"));
    }

    @Test
    void reversed_order_loses_error_status() {
        // ── Setup: in-memory span exporter ────────────────────────────────────
        final var exporter = InMemorySpanExporter.create();
        final var tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
        final var tracer = tracerProvider.get("test");

        // ── Execute: failing Uni with WRONG operator ordering ─────────────────
        final var span = tracer.spanBuilder("test.reversed").startSpan();
        final var scope = span.makeCurrent();

        final var result = Uni.createFrom().<String>failure(new IllegalStateException("test error"))
                .onTermination().invoke(() -> {
                    // Fires first — ends the span
                    scope.close();
                    span.end();
                })
                .onFailure().invoke(t -> {
                    // Fires second — span already ended, mutations are no-ops
                    span.setStatus(StatusCode.ERROR);
                    span.recordException(t);
                });

        // Trigger the failure path
        result.onFailure().recoverWithNull().await().indefinitely();

        // ── Verify: span did NOT capture ERROR status (ended before error recorded) ──
        final var spans = exporter.getFinishedSpanItems();
        assertThat(spans).hasSize(1);

        final SpanData spanData = spans.get(0);
        assertThat(spanData.getName()).isEqualTo("test.reversed");
        // ERROR status was NOT captured because the span was already ended
        assertThat(spanData.getStatus().getStatusCode()).isEqualTo(StatusCode.UNSET);
        // Exception event was NOT captured for the same reason
        assertThat(spanData.getEvents()).isEmpty();
    }
}
