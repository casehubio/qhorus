package io.casehub.qhorus.api.spi;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProtocolEvaluationEventTest {

    @Test
    void constructsWithAllFields() {
        var violations = List.of(new DispatchAdvisory("REQUEST_RESPONSE", Severity.WARNING,
                "3 open queries", Map.of("count", 3), SuggestedAction.LOG));
        var event = new ProtocolEvaluationEvent(UUID.randomUUID(), "test-channel", "tenant-1",
                violations, ProtocolEvaluationEvent.EnforcementOutcome.ALLOWED);
        assertThat(event.violations()).hasSize(1);
        assertThat(event.enforcementOutcome()).isEqualTo(ProtocolEvaluationEvent.EnforcementOutcome.ALLOWED);
        assertThat(event.channelName()).isEqualTo("test-channel");
        assertThat(event.tenancyId()).isEqualTo("tenant-1");
    }

    @Test
    void nullChannelIdThrows() {
        assertThatThrownBy(() -> new ProtocolEvaluationEvent(null, "ch", "t",
                List.of(), ProtocolEvaluationEvent.EnforcementOutcome.ALLOWED))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullChannelNameThrows() {
        assertThatThrownBy(() -> new ProtocolEvaluationEvent(UUID.randomUUID(), null, "t",
                List.of(), ProtocolEvaluationEvent.EnforcementOutcome.ALLOWED))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullOutcomeThrows() {
        assertThatThrownBy(() -> new ProtocolEvaluationEvent(UUID.randomUUID(), "ch", "t",
                List.of(), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullViolationsDefaultsToEmptyList() {
        var event = new ProtocolEvaluationEvent(UUID.randomUUID(), "ch", "t",
                null, ProtocolEvaluationEvent.EnforcementOutcome.ALLOWED);
        assertThat(event.violations()).isEmpty();
    }

    @Test
    void nullTenancyIdIsAllowed() {
        var event = new ProtocolEvaluationEvent(UUID.randomUUID(), "ch", null,
                List.of(), ProtocolEvaluationEvent.EnforcementOutcome.BLOCKED);
        assertThat(event.tenancyId()).isNull();
    }

    @Test
    void violationsAreImmutableCopy() {
        var mutable = new java.util.ArrayList<DispatchAdvisory>();
        mutable.add(new DispatchAdvisory("SRC", Severity.WARNING, "msg", Map.of(), SuggestedAction.LOG));
        var event = new ProtocolEvaluationEvent(UUID.randomUUID(), "ch", "t",
                mutable, ProtocolEvaluationEvent.EnforcementOutcome.ALLOWED);
        mutable.add(new DispatchAdvisory("SRC2", Severity.CRITICAL, "msg2", Map.of(), SuggestedAction.ESCALATE));
        assertThat(event.violations()).hasSize(1);
    }
}
