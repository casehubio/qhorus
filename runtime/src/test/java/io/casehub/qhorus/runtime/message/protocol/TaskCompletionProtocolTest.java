package io.casehub.qhorus.runtime.message.protocol;

import io.casehub.qhorus.api.message.Commitment;
import io.casehub.qhorus.api.message.CommitmentState;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.spi.ProtocolContext;
import io.casehub.qhorus.api.spi.Severity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TaskCompletionProtocolTest {

    private final TaskCompletionProtocol protocol = new TaskCompletionProtocol();

    {
        protocol.maxOpenCommands = 3;
    }

    private ProtocolContext ctx(MessageType incoming, String sender, List<Commitment> commitments) {
        return new ProtocolContext(UUID.randomUUID(), "test-channel", incoming, sender,
                                   UUID.randomUUID().toString(), List.of(), List.of(), commitments);
    }

    private Commitment openCommand(String correlationId, String requester, String obligor) {
        return Commitment.builder()
                         .id(UUID.randomUUID()).correlationId(correlationId)
                         .channelId(UUID.randomUUID()).messageType(MessageType.COMMAND)
                         .requester(requester).obligor(obligor).state(CommitmentState.OPEN)
                         .createdAt(Instant.now()).build();
    }

    private Commitment openQuery(String correlationId, String requester) {
        return Commitment.builder()
                         .id(UUID.randomUUID()).correlationId(correlationId)
                         .channelId(UUID.randomUUID()).messageType(MessageType.QUERY)
                         .requester(requester).state(CommitmentState.OPEN)
                         .createdAt(Instant.now()).build();
    }

    @Test
    void noActiveCommitments_noAdvisory() {
        assertThat(protocol.evaluate(ctx(MessageType.COMMAND, "a", List.of()))).isEmpty();
    }

    @Test
    void openCommandsAtThreshold_advisoryOnNewCommand() {
        List<Commitment> commitments = List.of(
                openCommand("c1", "a", "b"),
                openCommand("c2", "a", "b"),
                openCommand("c3", "a", "b"));
        var advisories = protocol.evaluate(ctx(MessageType.COMMAND, "a", commitments));
        assertThat(advisories).anySatisfy(a -> {
            assertThat(a.source()).isEqualTo("TASK_COMPLETION");
            assertThat(a.severity()).isEqualTo(Severity.WARNING);
            assertThat(a.message()).contains("3 open COMMANDs");
            assertThat(a.evidence()).containsEntry("openCommandCount", 3);
        });
    }

    @Test
    void senderIsObligorWithOpenObligation_advisory() {
        List<Commitment> commitments = List.of(openCommand("c1", "requester", "obligor-a"));
        var              advisories  = protocol.evaluate(ctx(MessageType.STATUS, "obligor-a", commitments));
        assertThat(advisories).hasSize(1);
        assertThat(advisories.get(0).source()).isEqualTo("TASK_COMPLETION");
        assertThat(advisories.get(0).severity()).isEqualTo(Severity.WARNING);
        assertThat(advisories.get(0).message()).contains("open obligation");
        assertThat(advisories.get(0).evidence()).containsEntry("senderIsObligor", true);
    }

    @Test
    void senderIsNotObligor_noObligorAdvisory() {
        List<Commitment> commitments = List.of(openCommand("c1", "requester", "obligor-a"));
        assertThat(protocol.evaluate(ctx(MessageType.STATUS, "someone-else", commitments))).isEmpty();
    }

    @Test
    void queryCommitmentsIgnored() {
        List<Commitment> commitments = List.of(
                openQuery("q1", "a"), openQuery("q2", "a"), openQuery("q3", "a"));
        assertThat(protocol.evaluate(ctx(MessageType.COMMAND, "a", commitments))).isEmpty();
    }
}
