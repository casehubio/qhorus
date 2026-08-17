package io.casehub.qhorus.graphql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.channel.ChannelReader;
import io.casehub.qhorus.api.channel.ChannelSemantic;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class QhorusModelEnricherTest {

    @Test
    void summaryDescribesCapabilities() {
        ChannelReader reader = mock(ChannelReader.class);
        var enricher = new QhorusModelEnricher(reader);

        String summary = enricher.summary();

        assertThat(summary).contains("channel");
        assertThat(summary).contains("message");
        assertThat(summary).contains("commitment");
    }

    @Test
    void stateReportsActiveChannelCount() {
        ChannelReader reader = mock(ChannelReader.class);
        Channel ch1 = Channel.builder("ch-1").id(UUID.randomUUID())
                .semantic(ChannelSemantic.APPEND).tenancyId("t")
                .createdAt(Instant.now()).lastActivityAt(Instant.now()).build();
        Channel ch2 = Channel.builder("ch-2").id(UUID.randomUUID())
                .semantic(ChannelSemantic.APPEND).tenancyId("t")
                .createdAt(Instant.now()).lastActivityAt(Instant.now()).build();
        when(reader.listAll()).thenReturn(List.of(ch1, ch2));

        var enricher = new QhorusModelEnricher(reader);
        var state = enricher.state();

        assertThat(state).containsEntry("activeChannels", 2);
    }
}
