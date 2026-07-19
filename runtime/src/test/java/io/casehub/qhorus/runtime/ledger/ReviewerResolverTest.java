package io.casehub.qhorus.runtime.ledger;

import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.instance.Instance;
import io.casehub.qhorus.api.spi.PeerReviewRequestedEvent;
import io.casehub.qhorus.api.store.ChannelStore;
import io.casehub.qhorus.runtime.instance.InstanceService;
import jakarta.enterprise.event.Event;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReviewerResolverTest {

    private ReviewerResolver resolver;
    private ChannelStore channelStore;
    private InstanceService instanceService;
    @SuppressWarnings("unchecked")
    private final Event<PeerReviewRequestedEvent> reviewEvent = mock(Event.class);
    private final UUID channelId = UUID.randomUUID();
    private final UUID entryId = UUID.randomUUID();
    private static final String TENANT = "test-tenant";

    @BeforeEach
    void setUp() {
        resolver = new ReviewerResolver();
        channelStore = mock(ChannelStore.class);
        instanceService = mock(InstanceService.class);
        resolver.channelStore = channelStore;
        resolver.instanceService = instanceService;
        resolver.reviewRequestedEvent = reviewEvent;

        when(channelStore.find(channelId)).thenReturn(Optional.of(
                Channel.builder("test-ch").id(channelId).build()));
        when(instanceService.findByCapability("peer-reviewer")).thenReturn(List.of());
    }

    @Test
    void explicit_reviewers_returned_directly() {
        List<String> result = resolver.resolve(channelId,
                List.of("rev-a", "rev-b"), entryId, TENANT);

        assertThat(result).containsExactly("rev-a", "rev-b");
        verify(reviewEvent, never()).fireAsync(any());
    }

    @Test
    void channel_config_used_when_no_explicit() {
        when(channelStore.find(channelId)).thenReturn(Optional.of(
                Channel.builder("test-ch").id(channelId)
                        .reviewerInstances(List.of("ch-rev-1")).build()));

        List<String> result = resolver.resolve(channelId, List.of(), entryId, TENANT);

        assertThat(result).containsExactly("ch-rev-1");
    }

    @Test
    void capability_routing_used_when_no_channel_config() {
        when(instanceService.findByCapability("peer-reviewer")).thenReturn(List.of(
                Instance.builder("cap-rev-1").build(),
                Instance.builder("cap-rev-2").build()));

        List<String> result = resolver.resolve(channelId, List.of(), entryId, TENANT);

        assertThat(result).containsExactly("cap-rev-1", "cap-rev-2");
    }

    @Test
    void cdi_event_fired_when_all_layers_empty() {
        List<String> result = resolver.resolve(channelId, List.of(), entryId, TENANT);

        assertThat(result).isEmpty();
        verify(reviewEvent).fireAsync(any(PeerReviewRequestedEvent.class));
    }

    @Test
    void explicit_wins_over_channel_config() {
        when(channelStore.find(channelId)).thenReturn(Optional.of(
                Channel.builder("test-ch").id(channelId)
                        .reviewerInstances(List.of("ch-rev-1")).build()));

        List<String> result = resolver.resolve(channelId,
                List.of("explicit-rev"), entryId, TENANT);

        assertThat(result).containsExactly("explicit-rev");
    }
}
