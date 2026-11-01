package io.casehub.qhorus.channel;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.channel.ChannelCreateRequest;
import io.casehub.qhorus.api.channel.Space;
import io.casehub.qhorus.api.channel.SpaceCreateRequest;
import io.casehub.qhorus.runtime.channel.ChannelService;
import io.casehub.qhorus.runtime.channel.SpaceService;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class ProtocolInheritanceTest {

    @Inject ChannelService channelService;
    @Inject SpaceService spaceService;

    @Test
    @TestTransaction
    void channelInheritsProtocolsFromSpace() {
        Space space = spaceService.create(new SpaceCreateRequest(
                "org-" + System.nanoTime(), "Test org",
                null, List.of("REQUEST_RESPONSE", "TASK_COMPLETION"), List.of("agent-a", "agent-b")));

        Channel ch = channelService.create(ChannelCreateRequest.builder("inherit-" + System.nanoTime())
                .spaceId(space.id())
                .build());

        assertThat(ch.protocols()).containsExactly("REQUEST_RESPONSE", "TASK_COMPLETION");
        assertThat(ch.protocolParticipants()).containsExactly("agent-a", "agent-b");
    }

    @Test
    @TestTransaction
    void explicitProtocolsOverrideSpaceDefaults() {
        Space space = spaceService.create(new SpaceCreateRequest(
                "org-" + System.nanoTime(), "Test org",
                null, List.of("REQUEST_RESPONSE"), List.of("agent-a")));

        Channel ch = channelService.create(ChannelCreateRequest.builder("override-" + System.nanoTime())
                .spaceId(space.id())
                .protocols(List.of("ROUND_ROBIN"))
                .protocolParticipants(List.of("x", "y"))
                .build());

        assertThat(ch.protocols()).containsExactly("ROUND_ROBIN");
        assertThat(ch.protocolParticipants()).containsExactly("x", "y");
    }

    @Test
    @TestTransaction
    void channelWithoutSpaceHasNoProtocols() {
        Channel ch = channelService.create(ChannelCreateRequest.builder("no-space-" + System.nanoTime())
                .build());

        assertThat(ch.protocols()).isEmpty();
        assertThat(ch.protocolParticipants()).isEmpty();
    }

    @Test
    @TestTransaction
    void spaceWithoutDefaultProtocolsDoesNotInherit() {
        Space space = spaceService.create(new SpaceCreateRequest(
                "plain-" + System.nanoTime(), "No defaults", null));

        Channel ch = channelService.create(ChannelCreateRequest.builder("plain-ch-" + System.nanoTime())
                .spaceId(space.id())
                .build());

        assertThat(ch.protocols()).isEmpty();
        assertThat(ch.protocolParticipants()).isEmpty();
    }

    @Test
    @TestTransaction
    void copyOnCreateSemanticsPreservesChannelAfterSpaceChange() {
        Space space = spaceService.create(new SpaceCreateRequest(
                "copy-" + System.nanoTime(), "Test",
                null, List.of("REQUEST_RESPONSE"), List.of("agent-a")));

        Channel ch = channelService.create(ChannelCreateRequest.builder("copy-ch-" + System.nanoTime())
                .spaceId(space.id())
                .build());

        assertThat(ch.protocols()).containsExactly("REQUEST_RESPONSE");

        channelService.setProtocols(ch.id(), List.of("ROUND_ROBIN"));
        Channel updated = channelService.findById(ch.id()).orElseThrow();

        assertThat(updated.protocols()).containsExactly("ROUND_ROBIN");
    }
}
