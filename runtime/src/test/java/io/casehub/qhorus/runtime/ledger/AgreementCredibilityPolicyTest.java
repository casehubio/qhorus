package io.casehub.qhorus.runtime.ledger;

import io.casehub.ledger.api.model.AttestationVerdict;
import io.casehub.ledger.api.model.CredibilityFlag;
import io.casehub.ledger.api.model.LedgerAttestation;
import io.casehub.ledger.api.spi.AttestorCredibilityPolicy.CredibilityAssessment;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.platform.api.identity.ActorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgreementCredibilityPolicyTest {

    private LedgerEntryRepository ledger;
    private AgreementCredibilityPolicy policy;

    @BeforeEach
    void setUp() {
        ledger = mock(LedgerEntryRepository.class);
        policy = new AgreementCredibilityPolicy();
        policy.ledger = ledger;
        policy.minDataPoints = 5;
        policy.lowAgreementThreshold = 0.3;
    }

    @Test
    void assess_agreementIncreasesWeight() {
        var peerAttestation = peerAttestation("reviewer-a", UUID.randomUUID(), AttestationVerdict.ENDORSED);
        var policyAttestation = policyAttestation(peerAttestation.ledgerEntryId, AttestationVerdict.SOUND);

        when(ledger.findPeerAttestationsByAttestorIds(any(), any()))
                .thenReturn(List.of(peerAttestation));
        when(ledger.findAttestationsByEntryId(peerAttestation.ledgerEntryId, null))
                .thenReturn(List.of(peerAttestation, policyAttestation));

        var result = policy.assessBatch(Set.of("reviewer-a"));

        assertThat(result.get("reviewer-a").weight()).isGreaterThan(0.5);
        assertThat(result.get("reviewer-a").flags()).contains(CredibilityFlag.INSUFFICIENT_DATA);
    }

    @Test
    void assess_disagreementDecreasesWeight() {
        var entryIds = new UUID[6];
        var peerAttestations = new java.util.ArrayList<io.casehub.ledger.api.model.LedgerAttestation>();
        for (int i = 0; i < 6; i++) {
            entryIds[i] = UUID.randomUUID();
            var peer = peerAttestation("reviewer-a", entryIds[i], AttestationVerdict.ENDORSED);
            peerAttestations.add(peer);
            var pol = policyAttestation(entryIds[i], AttestationVerdict.FLAGGED);
            when(ledger.findAttestationsByEntryId(entryIds[i], null))
                    .thenReturn(List.of(peer, pol));
        }

        when(ledger.findPeerAttestationsByAttestorIds(any(), any()))
                .thenReturn(peerAttestations);

        var result = policy.assessBatch(Set.of("reviewer-a"));

        assertThat(result.get("reviewer-a").weight()).isLessThan(0.5);
        assertThat(result.get("reviewer-a").flags()).doesNotContain(CredibilityFlag.INSUFFICIENT_DATA);
        assertThat(result.get("reviewer-a").flags()).contains(CredibilityFlag.LOW_AGREEMENT);
    }

    @Test
    void assess_fewerThanMinDataPoints_setsInsufficientDataFlag() {
        var peer = peerAttestation("reviewer-a", UUID.randomUUID(), AttestationVerdict.ENDORSED);
        var pol = policyAttestation(peer.ledgerEntryId, AttestationVerdict.SOUND);

        when(ledger.findPeerAttestationsByAttestorIds(any(), any()))
                .thenReturn(List.of(peer));
        when(ledger.findAttestationsByEntryId(peer.ledgerEntryId, null))
                .thenReturn(List.of(peer, pol));

        var result = policy.assessBatch(Set.of("reviewer-a"));

        assertThat(result.get("reviewer-a").flags()).contains(CredibilityFlag.INSUFFICIENT_DATA);
        assertThat(result.get("reviewer-a").weight()).isEqualTo(1.0);
    }

    @Test
    void assess_noPolicyAttestation_skipsEntry() {
        var peer = peerAttestation("reviewer-a", UUID.randomUUID(), AttestationVerdict.ENDORSED);

        when(ledger.findPeerAttestationsByAttestorIds(any(), any()))
                .thenReturn(List.of(peer));
        when(ledger.findAttestationsByEntryId(peer.ledgerEntryId, null))
                .thenReturn(List.of(peer));

        var result = policy.assessBatch(Set.of("reviewer-a"));

        assertThat(result.get("reviewer-a").weight()).isEqualTo(1.0);
        assertThat(result.get("reviewer-a").flags()).contains(CredibilityFlag.INSUFFICIENT_DATA);
    }

    @Test
    void assessBatch_queriesOnce() {
        when(ledger.findPeerAttestationsByAttestorIds(any(), any()))
                .thenReturn(List.of());

        policy.assessBatch(Set.of("a", "b", "c"));

        verify(ledger, times(1)).findPeerAttestationsByAttestorIds(any(), any());
    }

    @Test
    void assess_challengedMatchesFlagged_isAgreement() {
        var peer = peerAttestation("reviewer-a", UUID.randomUUID(), AttestationVerdict.CHALLENGED);
        var pol = policyAttestation(peer.ledgerEntryId, AttestationVerdict.FLAGGED);

        when(ledger.findPeerAttestationsByAttestorIds(any(), any()))
                .thenReturn(List.of(peer));
        when(ledger.findAttestationsByEntryId(peer.ledgerEntryId, null))
                .thenReturn(List.of(peer, pol));

        var result = policy.assessBatch(Set.of("reviewer-a"));

        assertThat(result.get("reviewer-a").weight()).isGreaterThan(0.5);
    }

    private static io.casehub.ledger.api.model.LedgerAttestation peerAttestation(String attestorId, UUID entryId, AttestationVerdict verdict) {
        var a = new io.casehub.ledger.api.model.LedgerAttestation();
        a.id = UUID.randomUUID();
        a.ledgerEntryId = entryId;
        a.attestorId = attestorId;
        a.attestorType = ActorType.AGENT;
        a.attestorRole = "peer-reviewer";
        a.verdict = verdict;
        a.confidence = 1.0;
        return a;
    }

    private static io.casehub.ledger.api.model.LedgerAttestation policyAttestation(UUID entryId, AttestationVerdict verdict) {
        var a = new io.casehub.ledger.api.model.LedgerAttestation();
        a.id = UUID.randomUUID();
        a.ledgerEntryId = entryId;
        a.attestorId = "system";
        a.attestorType = ActorType.SYSTEM;
        a.verdict = verdict;
        a.confidence = 0.7;
        return a;
    }
}
