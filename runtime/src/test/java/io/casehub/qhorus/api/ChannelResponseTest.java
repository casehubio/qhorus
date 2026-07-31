package io.casehub.qhorus.api;

import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.channel.ChannelSemantic;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.runtime.api.ChannelResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ChannelResponseTest {

    @Test
    void fromChannelMapsAllFields() {
        final var id = UUID.randomUUID();
        final var spaceId = UUID.randomUUID();
        final var ch = Channel.builder("test-channel")
                .id(id).description("desc").semantic(ChannelSemantic.BARRIER)
                .barrierContributors(List.of("agent-a", "agent-b"))
                .allowedWriters(List.of("writer-1"))
                .adminInstances(List.of("admin-1"))
                .reviewerInstances(List.of("reviewer-1"))
                .allowedTypes(Set.of(MessageType.QUERY, MessageType.RESPONSE))
                .deniedTypes(Set.of(MessageType.EVENT))
                .rateLimitPerChannel(100).rateLimitPerInstance(10)
                .protocols(List.of("REQUEST_RESPONSE"))
                .protocolParticipants(List.of("agent-a", "agent-b"))
                .trackDelivery(true).spaceId(spaceId)
                .paused(true).lastActivityAt(Instant.parse("2026-07-30T10:00:00Z"))
                .build();

        final var response = ChannelResponse.from(ch, 42L, "my-space");

        assertThat(response.channelId()).isEqualTo(id);
        assertThat(response.name()).isEqualTo("test-channel");
        assertThat(response.description()).isEqualTo("desc");
        assertThat(response.semantic()).isEqualTo("BARRIER");
        assertThat(response.messageCount()).isEqualTo(42L);
        assertThat(response.paused()).isTrue();
        assertThat(response.barrierContributors()).containsExactly("agent-a", "agent-b");
        assertThat(response.allowedWriters()).containsExactly("writer-1");
        assertThat(response.adminInstances()).containsExactly("admin-1");
        assertThat(response.reviewerInstances()).containsExactly("reviewer-1");
        assertThat(response.allowedTypes()).containsExactlyInAnyOrder("QUERY", "RESPONSE");
        assertThat(response.deniedTypes()).containsExactlyInAnyOrder("EVENT");
        assertThat(response.rateLimitPerChannel()).isEqualTo(100);
        assertThat(response.rateLimitPerInstance()).isEqualTo(10);
        assertThat(response.protocols()).containsExactly("REQUEST_RESPONSE");
        assertThat(response.protocolParticipants()).containsExactly("agent-a", "agent-b");
        assertThat(response.trackDelivery()).isTrue();
        assertThat(response.spaceId()).isEqualTo(spaceId);
        assertThat(response.spaceName()).isEqualTo("my-space");
        assertThat(response.lastActivityAt()).isEqualTo("2026-07-30T10:00:00Z");
    }

    @Test
    void fromChannelHandlesNulls() {
        final var ch = Channel.builder("minimal").build();
        final var response = ChannelResponse.from(ch, 0L, null);

        assertThat(response.name()).isEqualTo("minimal");
        assertThat(response.semantic()).isNull();
        assertThat(response.barrierContributors()).isEmpty();
        assertThat(response.allowedTypes()).isNull();
        assertThat(response.deniedTypes()).isNull();
        assertThat(response.spaceName()).isNull();
        assertThat(response.lastActivityAt()).isNull();
    }
}
