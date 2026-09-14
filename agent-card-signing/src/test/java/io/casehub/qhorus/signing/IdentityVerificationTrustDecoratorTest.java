package io.casehub.qhorus.signing;

import io.casehub.ledger.api.spi.TrustScoreSource;
import io.casehub.qhorus.api.instance.ExternalAgentBinding;
import io.casehub.qhorus.api.instance.VerificationStatus;
import io.casehub.qhorus.api.store.ExternalAgentBindingStore;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IdentityVerificationTrustDecoratorTest {

    private static final double FLOOR_SCORE = 0.6;
    private static final double WEIGHT = 0.15;

    @Test
    void globalScoreIncludesBoostForVerifiedAgent() {
        TrustScoreSource delegate = mock(TrustScoreSource.class);
        when(delegate.globalScore("agent-1")).thenReturn(OptionalDouble.of(0.7));

        ExternalAgentBindingStore store = mock(ExternalAgentBindingStore.class);
        when(store.findByInstanceId("agent-1")).thenReturn(Optional.of(verifiedBinding("agent-1")));

        var decorator = new IdentityVerificationTrustDecorator(delegate, store, FLOOR_SCORE, WEIGHT);
        OptionalDouble score = decorator.globalScore("agent-1");

        assertThat(score).isPresent();
        assertThat(score.getAsDouble()).isCloseTo(0.79, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    void globalScoreUnchangedForUnverifiedAgent() {
        TrustScoreSource delegate = mock(TrustScoreSource.class);
        when(delegate.globalScore("agent-2")).thenReturn(OptionalDouble.of(0.7));

        ExternalAgentBindingStore store = mock(ExternalAgentBindingStore.class);
        when(store.findByInstanceId("agent-2")).thenReturn(Optional.empty());

        var decorator = new IdentityVerificationTrustDecorator(delegate, store, FLOOR_SCORE, WEIGHT);
        OptionalDouble score = decorator.globalScore("agent-2");

        assertThat(score).isPresent();
        assertThat(score.getAsDouble()).isEqualTo(0.7);
    }

    @Test
    void globalScoreClampedAtOne() {
        TrustScoreSource delegate = mock(TrustScoreSource.class);
        when(delegate.globalScore("agent-3")).thenReturn(OptionalDouble.of(0.95));

        ExternalAgentBindingStore store = mock(ExternalAgentBindingStore.class);
        when(store.findByInstanceId("agent-3")).thenReturn(Optional.of(verifiedBinding("agent-3")));

        var decorator = new IdentityVerificationTrustDecorator(delegate, store, FLOOR_SCORE, WEIGHT);
        OptionalDouble score = decorator.globalScore("agent-3");

        assertThat(score.getAsDouble()).isLessThanOrEqualTo(1.0);
    }

    @Test
    void globalScoreNoBoostForFailedVerification() {
        TrustScoreSource delegate = mock(TrustScoreSource.class);
        when(delegate.globalScore("agent-4")).thenReturn(OptionalDouble.of(0.7));

        ExternalAgentBindingStore store = mock(ExternalAgentBindingStore.class);
        when(store.findByInstanceId("agent-4")).thenReturn(Optional.of(failedBinding("agent-4")));

        var decorator = new IdentityVerificationTrustDecorator(delegate, store, FLOOR_SCORE, WEIGHT);
        OptionalDouble score = decorator.globalScore("agent-4");

        assertThat(score.getAsDouble()).isEqualTo(0.7);
    }

    @Test
    void dimensionScoreReturnsFloorForVerifiedAgent() {
        TrustScoreSource delegate = mock(TrustScoreSource.class);
        ExternalAgentBindingStore store = mock(ExternalAgentBindingStore.class);
        when(store.findByInstanceId("agent-1")).thenReturn(Optional.of(verifiedBinding("agent-1")));

        var decorator = new IdentityVerificationTrustDecorator(delegate, store, FLOOR_SCORE, WEIGHT);
        OptionalDouble score = decorator.dimensionScore("agent-1", "identity-verification");

        assertThat(score).isPresent();
        assertThat(score.getAsDouble()).isEqualTo(FLOOR_SCORE);
    }

    @Test
    void dimensionScoreEmptyForUnknownAgent() {
        TrustScoreSource delegate = mock(TrustScoreSource.class);
        ExternalAgentBindingStore store = mock(ExternalAgentBindingStore.class);
        when(store.findByInstanceId("unknown")).thenReturn(Optional.empty());

        var decorator = new IdentityVerificationTrustDecorator(delegate, store, FLOOR_SCORE, WEIGHT);
        OptionalDouble score = decorator.dimensionScore("unknown", "identity-verification");

        assertThat(score).isEmpty();
    }

    @Test
    void dimensionScoreDelegatesToBaseForOtherDimensions() {
        TrustScoreSource delegate = mock(TrustScoreSource.class);
        when(delegate.dimensionScore("agent-1", "quality")).thenReturn(OptionalDouble.of(0.8));

        ExternalAgentBindingStore store = mock(ExternalAgentBindingStore.class);
        var decorator = new IdentityVerificationTrustDecorator(delegate, store, FLOOR_SCORE, WEIGHT);

        assertThat(decorator.dimensionScore("agent-1", "quality").getAsDouble()).isEqualTo(0.8);
    }

    @Test
    void allDimensionScoresIncludesIdentityForVerifiedAgent() {
        TrustScoreSource delegate = mock(TrustScoreSource.class);
        when(delegate.allDimensionScores("agent-1")).thenReturn(Map.of("quality", 0.8));

        ExternalAgentBindingStore store = mock(ExternalAgentBindingStore.class);
        when(store.findByInstanceId("agent-1")).thenReturn(Optional.of(verifiedBinding("agent-1")));

        var decorator = new IdentityVerificationTrustDecorator(delegate, store, FLOOR_SCORE, WEIGHT);
        Map<String, Double> scores = decorator.allDimensionScores("agent-1");

        assertThat(scores).containsEntry("quality", 0.8);
        assertThat(scores).containsEntry("identity-verification", FLOOR_SCORE);
    }

    private static ExternalAgentBinding verifiedBinding(String instanceId) {
        return new ExternalAgentBinding(UUID.randomUUID(), instanceId, "https://example.com",
                null, "1.0", Instant.now(), VerificationStatus.VERIFIED, Instant.now(), "kid-1");
    }

    private static ExternalAgentBinding failedBinding(String instanceId) {
        return new ExternalAgentBinding(UUID.randomUUID(), instanceId, "https://example.com",
                null, "1.0", Instant.now(), VerificationStatus.FAILED, null, null);
    }
}
