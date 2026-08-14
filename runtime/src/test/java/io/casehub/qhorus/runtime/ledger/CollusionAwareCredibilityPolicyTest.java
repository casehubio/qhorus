package io.casehub.qhorus.runtime.ledger;

import io.casehub.ledger.api.model.CredibilityFlag;
import io.casehub.ledger.api.spi.AttestorCredibilityPolicy;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CollusionAwareCredibilityPolicyTest {

    private AgreementCredibilityPolicy base;
    private LedgerEntryRepository ledger;
    private CollusionAwareCredibilityPolicy policy;

    @BeforeEach
    void setUp() {
        base = mock(AgreementCredibilityPolicy.class);
        ledger = mock(LedgerEntryRepository.class);
        policy = new CollusionAwareCredibilityPolicy(base, ledger, 0.8);
    }

    @Test
    void assess_highMutualEndorsement_setsCollusionSuspectFlag() {
        when(base.assessBatch(any())).thenReturn(Map.of(
                "agent-a", new AttestorCredibilityPolicy.CredibilityAssessment(0.9, "good", Set.of())));

        when(ledger.findPeerAttestationPairCounts(any(), any())).thenReturn(
                Map.of("agent-a", Map.of("agent-b", 5L)));

        var result = policy.assessBatch(Set.of("agent-a"));

        assertThat(result.get("agent-a").flags()).contains(CredibilityFlag.COLLUSION_SUSPECT);
        assertThat(result.get("agent-a").weight()).isEqualTo(0.9);
    }

    @Test
    void assess_lowMutualEndorsement_noCollusionFlag() {
        when(base.assessBatch(any())).thenReturn(Map.of(
                "agent-a", new AttestorCredibilityPolicy.CredibilityAssessment(0.9, "good", Set.of())));

        when(ledger.findPeerAttestationPairCounts(any(), any())).thenReturn(
                Map.of("agent-a", Map.of("agent-b", 1L, "agent-c", 5L, "agent-d", 4L)));

        var result = policy.assessBatch(Set.of("agent-a"));

        assertThat(result.get("agent-a").flags()).doesNotContain(CredibilityFlag.COLLUSION_SUSPECT);
    }

    @Test
    void assess_collusionFlagDoesNotChangeWeight() {
        when(base.assessBatch(any())).thenReturn(Map.of(
                "agent-a", new AttestorCredibilityPolicy.CredibilityAssessment(0.95, "excellent", Set.of())));

        when(ledger.findPeerAttestationPairCounts(any(), any())).thenReturn(
                Map.of("agent-a", Map.of("agent-b", 10L)));

        var result = policy.assessBatch(Set.of("agent-a"));

        assertThat(result.get("agent-a").weight()).isEqualTo(0.95);
        assertThat(result.get("agent-a").flags()).contains(CredibilityFlag.COLLUSION_SUSPECT);
    }

    @Test
    void assess_noPairData_noFlag() {
        when(base.assessBatch(any())).thenReturn(Map.of(
                "agent-a", new AttestorCredibilityPolicy.CredibilityAssessment(0.7, "ok", Set.of())));

        when(ledger.findPeerAttestationPairCounts(any(), any())).thenReturn(Map.of());

        var result = policy.assessBatch(Set.of("agent-a"));

        assertThat(result.get("agent-a").flags()).doesNotContain(CredibilityFlag.COLLUSION_SUSPECT);
    }
}
