package io.casehub.qhorus.ledger;

import io.casehub.qhorus.testing.QhorusTestHelper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import io.quarkus.test.TestTransaction;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class PeerAttestationIntegrationTest {

    @Inject QhorusTestHelper helper;

    @Inject
    io.casehub.qhorus.runtime.channel.ChannelService channelService;

    @Inject
    io.casehub.qhorus.runtime.instance.InstanceService instanceService;

    @Test
    @TestTransaction
    void explicit_attest_writes_endorsed_attestation() {
        String chName = "peer-attest-" + UUID.randomUUID().toString().substring(0, 8);
        helper.createChannel(chName, "test channel", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        String requesterId = "requester-" + chName;
        instanceService.register(requesterId, "requester", List.of());
        instanceService.register("anonymous", "test-principal", List.of());

        helper.sendMessage(chName, requesterId, "command", "do the task", null,
                          null, null, null, null, null, null, null, null);

        var ledgerEntries = helper.listLedgerEntries(chName, "COMMAND", null, null, null, null, null, null);
        var commandEntry = ledgerEntries.stream()
                                        .filter(e -> "COMMAND".equals(e.get("message_type")))
                                        .findFirst().orElseThrow();
        String entryId = commandEntry.get("entry_id").toString();

        var result = helper.attest(entryId, "ENDORSED", "output verified");
        assertThat(result.get("verdict")).isEqualTo("ENDORSED");

        var attestations = helper.listAttestations(entryId);
        assertThat(attestations).hasSizeGreaterThanOrEqualTo(1);
        var peerAttestation = attestations.stream()
                                          .filter(a -> "peer-reviewer".equals(a.get("attestor_role")))
                                          .findFirst().orElseThrow();
        assertThat(peerAttestation.get("verdict")).isEqualTo("ENDORSED");
        assertThat(peerAttestation.get("evidence")).isEqualTo("output verified");
    }

    @Test
    @TestTransaction
    void request_peer_review_sends_query() {
        String chName = "peer-review-" + UUID.randomUUID().toString().substring(0, 8);
        helper.createChannel(chName, "test channel", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        instanceService.register("req-" + chName, "requester", List.of());
        instanceService.register("rev-" + chName, "reviewer", List.of("peer-reviewer"));

        helper.sendMessage(chName, "req-" + chName, "command", "do the task", null,
                          null, null, null, null, null, null, null, null);

        var ledgerEntries = helper.listLedgerEntries(chName, "COMMAND", null, null, null, null, null, null);
        var commandEntry = ledgerEntries.stream()
                                        .filter(e -> "COMMAND".equals(e.get("message_type")))
                                        .findFirst().orElseThrow();
        String entryId = commandEntry.get("entry_id").toString();

        var result = helper.requestPeerReview(entryId, "rev-" + chName, null);
        assertThat(((Number) result.get("reviewers_sent")).intValue()).isEqualTo(1);
    }
}
