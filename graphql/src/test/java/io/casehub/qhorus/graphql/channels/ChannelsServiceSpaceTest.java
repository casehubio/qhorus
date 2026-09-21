package io.casehub.qhorus.graphql.channels;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.channel.ChannelManager;
import io.casehub.qhorus.api.channel.ChannelReader;
import io.casehub.qhorus.api.channel.ChannelSemantic;
import io.casehub.qhorus.api.channel.MembershipManager;
import io.casehub.qhorus.api.channel.Space;
import io.casehub.qhorus.api.channel.SpaceCreateRequest;
import io.casehub.qhorus.api.channel.SpaceManager;
import io.casehub.qhorus.api.channel.TopicManager;
import io.casehub.qhorus.api.channel.UnreadCountProvider;
import io.casehub.qhorus.api.gateway.BackendRegistry;
import io.casehub.qhorus.api.message.ConsumerMessaging;
import io.casehub.qhorus.api.store.MessageReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChannelsServiceSpaceTest {

    private ChannelsService service;
    private SpaceManager spaceManager;

    @BeforeEach
    void setUp() {
        spaceManager = mock(SpaceManager.class);
        service = new ChannelsService(
                mock(ChannelReader.class), mock(ConsumerMessaging.class),
                mock(ChannelManager.class), mock(TopicManager.class),
                mock(MembershipManager.class), mock(UnreadCountProvider.class),
                spaceManager, mock(BackendRegistry.class),
                mock(io.casehub.qhorus.api.store.MessageStore.class), mock(CurrentPrincipal.class),
                mock(io.casehub.qhorus.api.channel.ChannelSummaryManager.class),
                mock(io.casehub.qhorus.api.channel.ProjectionReader.class),
                mock(io.casehub.qhorus.api.channel.ProtocolReader.class),
                mock(io.casehub.qhorus.api.channel.RoutingDiagnostics.class));
    }

    @Test
    void spaceReturnsFromManager() {
        UUID id = UUID.randomUUID();
        var space = new Space(id, "project-alpha", "desc", null, "t", Instant.now());
        when(spaceManager.findById(id)).thenReturn(Optional.of(space));

        var result = service.space(id);

        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo("project-alpha");
    }

    @Test
    void spaceReturnsNullWhenNotFound() {
        UUID id = UUID.randomUUID();
        when(spaceManager.findById(id)).thenReturn(Optional.empty());

        assertThat(service.space(id)).isNull();
    }

    @Test
    void spacesListsRootsWhenNoParent() {
        var root = new Space(UUID.randomUUID(), "root", null, null, "t", Instant.now());
        when(spaceManager.listRoots()).thenReturn(List.of(root));

        var result = service.spaces(null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("root");
    }

    @Test
    void spacesListsChildrenWhenParent() {
        UUID parentId = UUID.randomUUID();
        var child = new Space(UUID.randomUUID(), "child", null, parentId, "t", Instant.now());
        when(spaceManager.listChildren(parentId)).thenReturn(List.of(child));

        var result = service.spaces(parentId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("child");
    }

    @Test
    void spaceChannelsDelegatesToManager() {
        UUID spaceId = UUID.randomUUID();
        Channel ch = Channel.builder("ch-1")
                .id(UUID.randomUUID())
                .semantic(ChannelSemantic.APPEND)
                .tenancyId("t")
                .createdAt(Instant.now())
                .lastActivityAt(Instant.now())
                .build();
        when(spaceManager.listChannels(spaceId)).thenReturn(List.of(ch));

        var result = service.spaceChannels(spaceId);

        assertThat(result).hasSize(1);
    }

    @Test
    void createSpaceDelegatesToManager() {
        var request = new SpaceCreateRequest("new-space", "description", null);
        var created = new Space(UUID.randomUUID(), "new-space", "description", null, "t", Instant.now());
        when(spaceManager.create(request)).thenReturn(created);

        var result = service.createSpace(request);

        assertThat(result.name()).isEqualTo("new-space");
    }

    @Test
    void deleteSpaceDelegatesToManager() {
        UUID spaceId = UUID.randomUUID();

        var result = service.deleteSpace(spaceId);

        verify(spaceManager).delete(spaceId);
        assertThat(result.deleted()).isTrue();
    }

    @Test
    void renameSpaceDelegatesToManager() {
        UUID spaceId = UUID.randomUUID();
        var renamed = new Space(spaceId, "renamed", null, null, "t", Instant.now());
        when(spaceManager.rename(spaceId, "renamed")).thenReturn(renamed);

        var result = service.renameSpace(spaceId, "renamed");

        assertThat(result.name()).isEqualTo("renamed");
    }

    @Test
    void moveSpaceDelegatesToManager() {
        UUID spaceId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        var moved = new Space(spaceId, "s", null, parentId, "t", Instant.now());
        when(spaceManager.moveSpace(spaceId, parentId)).thenReturn(moved);

        var result = service.moveSpace(spaceId, parentId);

        assertThat(result.parentSpaceId()).isEqualTo(parentId);
    }

    @Test
    void moveChannelToSpaceDelegatesToManager() {
        UUID channelId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        Channel ch = Channel.builder("moved-ch")
                .id(channelId)
                .semantic(ChannelSemantic.APPEND)
                .spaceId(spaceId)
                .tenancyId("t")
                .createdAt(Instant.now())
                .lastActivityAt(Instant.now())
                .build();
        when(spaceManager.moveChannelToSpace(channelId, spaceId)).thenReturn(ch);

        var result = service.moveChannelToSpace(channelId, spaceId);

        assertThat(result.spaceId()).isEqualTo(spaceId);
    }
}
