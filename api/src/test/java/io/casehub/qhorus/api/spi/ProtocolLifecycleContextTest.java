package io.casehub.qhorus.api.spi;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProtocolLifecycleContextTest {

    @Test
    void nullListsNormalisedToEmpty() {
        var ctx = new ProtocolLifecycleContext(
                UUID.randomUUID(), "test-ch", "MY_PROTO",
                null, null, false);

        assertThat(ctx.protocolParticipants()).isEmpty();
        assertThat(ctx.messageHistory()).isEmpty();
    }

    @Test
    void listsAreDefensivelyCopied() {
        var participants = new ArrayList<>(List.of("agent-a"));
        var ctx = new ProtocolLifecycleContext(
                UUID.randomUUID(), "test-ch", "MY_PROTO",
                participants, List.of(), false);

        participants.add("agent-b");
        assertThat(ctx.protocolParticipants()).containsExactly("agent-a");
    }
}
