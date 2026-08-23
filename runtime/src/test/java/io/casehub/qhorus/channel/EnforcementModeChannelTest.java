package io.casehub.qhorus.channel;

import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.channel.ChannelCreateRequest;
import io.casehub.qhorus.api.channel.ChannelSemantic;
import io.casehub.qhorus.api.channel.EnforcementMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EnforcementModeChannelTest {

    @Test
    void channelDefaultsToNullEnforcementMode() {
        var ch = Channel.builder("test-ch")
                .semantic(ChannelSemantic.APPEND)
                .tenancyId("default")
                .build();
        assertThat(ch.enforcementMode()).isNull();
        assertThat(ch.enforcementExclusions()).isEmpty();
    }

    @Test
    void channelBuilderSetsEnforcementMode() {
        var ch = Channel.builder("test-ch")
                .semantic(ChannelSemantic.APPEND)
                .enforcementMode(EnforcementMode.BLOCKING)
                .enforcementExclusions(List.of("CORRELATION_INTEGRITY"))
                .tenancyId("default")
                .build();
        assertThat(ch.enforcementMode()).isEqualTo(EnforcementMode.BLOCKING);
        assertThat(ch.enforcementExclusions()).containsExactly("CORRELATION_INTEGRITY");
    }

    @Test
    void channelCreateRequestSetsEnforcementMode() {
        var req = ChannelCreateRequest.builder("test-ch")
                .enforcementMode(EnforcementMode.QUARANTINE)
                .enforcementExclusions(List.of("TYPE_POLICY"))
                .build();
        assertThat(req.enforcementMode()).isEqualTo(EnforcementMode.QUARANTINE);
        assertThat(req.enforcementExclusions()).containsExactly("TYPE_POLICY");
    }

    @Test
    void channelFromRequestPropagatesEnforcementFields() {
        var req = ChannelCreateRequest.builder("test-ch")
                .enforcementMode(EnforcementMode.BLOCKING)
                .enforcementExclusions(List.of("REQUEST_RESPONSE"))
                .build();
        var ch = Channel.fromRequest(req, "default");
        assertThat(ch.enforcementMode()).isEqualTo(EnforcementMode.BLOCKING);
        assertThat(ch.enforcementExclusions()).containsExactly("REQUEST_RESPONSE");
    }

    @Test
    void toBuilderPreservesEnforcementFields() {
        var ch = Channel.builder("test-ch")
                .semantic(ChannelSemantic.APPEND)
                .enforcementMode(EnforcementMode.QUARANTINE)
                .enforcementExclusions(List.of("TYPE_POLICY", "CORRELATION_INTEGRITY"))
                .tenancyId("default")
                .build();
        var rebuilt = ch.toBuilder().build();
        assertThat(rebuilt.enforcementMode()).isEqualTo(EnforcementMode.QUARANTINE);
        assertThat(rebuilt.enforcementExclusions()).containsExactly("TYPE_POLICY", "CORRELATION_INTEGRITY");
    }

    @Test
    void enforcementExclusionsNullNormalizesToEmptyList() {
        var ch = Channel.builder("test-ch")
                .semantic(ChannelSemantic.APPEND)
                .enforcementMode(EnforcementMode.BLOCKING)
                .tenancyId("default")
                .build();
        assertThat(ch.enforcementExclusions()).isEmpty();
    }
}
