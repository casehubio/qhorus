package io.casehub.qhorus.signing;

import io.casehub.ledger.api.spi.TrustScoreSource;
import io.casehub.qhorus.api.instance.VerificationStatus;
import io.casehub.qhorus.api.store.ExternalAgentBindingStore;
import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

@Decorator
@Priority(1000)
public class IdentityVerificationTrustDecorator implements TrustScoreSource {

    @Inject @Delegate @Any
    TrustScoreSource delegate;

    @Inject
    ExternalAgentBindingStore bindingStore;

    private double floorScore = 0.6;
    private double weight = 0.15;

    @Inject
    void configure(SigningConfig config) {
        this.floorScore = config.trust().verifiedFloorScore();
        this.weight = config.trust().dimensionWeight();
    }

    public IdentityVerificationTrustDecorator() {}

    public IdentityVerificationTrustDecorator(TrustScoreSource delegate,
                                              ExternalAgentBindingStore bindingStore,
                                              double floorScore, double weight) {
        this.delegate = delegate;
        this.bindingStore = bindingStore;
        this.floorScore = floorScore;
        this.weight = weight;
    }

    @Override
    public OptionalDouble globalScore(String actorId) {
        OptionalDouble base = delegate.globalScore(actorId);
        OptionalDouble identity = computeIdentityScore(actorId);
        if (identity.isEmpty()) return base;
        double baseVal = base.orElse(0.0);
        double boost = identity.getAsDouble() * weight;
        return OptionalDouble.of(Math.min(1.0, baseVal + boost));
    }

    @Override
    public OptionalDouble capabilityScore(String actorId, String capabilityTag) {
        return delegate.capabilityScore(actorId, capabilityTag);
    }

    @Override
    public OptionalDouble dimensionScore(String actorId, String dimensionKey) {
        if ("identity-verification".equals(dimensionKey)) {
            return computeIdentityScore(actorId);
        }
        return delegate.dimensionScore(actorId, dimensionKey);
    }

    @Override
    public OptionalDouble capabilityDimensionScore(String actorId, String capabilityTag, String dimensionKey) {
        return delegate.capabilityDimensionScore(actorId, capabilityTag, dimensionKey);
    }

    @Override
    public int decisionCount(String actorId, String capabilityTag) {
        return delegate.decisionCount(actorId, capabilityTag);
    }

    @Override
    public Map<String, Double> allCapabilityScores(String actorId) {
        return delegate.allCapabilityScores(actorId);
    }

    @Override
    public Map<String, Double> allDimensionScores(String actorId) {
        Map<String, Double> scores = new LinkedHashMap<>(delegate.allDimensionScores(actorId));
        computeIdentityScore(actorId).ifPresent(s -> scores.put("identity-verification", s));
        return scores;
    }

    @Override
    public Map<String, Double> qualityScores(String actorId, String capabilityTag) {
        return delegate.qualityScores(actorId, capabilityTag);
    }

    private OptionalDouble computeIdentityScore(String actorId) {
        try {
            return bindingStore.findByInstanceId(actorId)
                    .filter(b -> b.verificationStatus() == VerificationStatus.VERIFIED)
                    .map(b -> OptionalDouble.of(floorScore))
                    .orElse(OptionalDouble.empty());
        } catch (Exception e) {
            return OptionalDouble.empty();
        }
    }
}
