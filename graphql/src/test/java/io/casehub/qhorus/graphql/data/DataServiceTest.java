package io.casehub.qhorus.graphql.data;

import io.casehub.qhorus.api.data.DataManager;
import io.casehub.qhorus.api.data.SharedData;
import io.casehub.qhorus.api.message.ArtefactRef;
import io.casehub.qhorus.api.message.ArtefactType;
import io.casehub.qhorus.api.message.ConsumerMessaging;
import io.casehub.qhorus.api.message.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class DataServiceTest {

    private DataServiceImpl service;
    private DataManager dataManager;
    private ConsumerMessaging consumerMessaging;

    @BeforeEach
    void setUp() {
        dataManager = mock(DataManager.class);
        consumerMessaging = mock(ConsumerMessaging.class);
        service = new DataServiceImpl(dataManager, consumerMessaging);
    }

    @Test
    void artefactByKeyDelegates() {
        var sd = SharedData.builder("my-doc").id(UUID.randomUUID())
                .content("hello").complete(true).sizeBytes(5)
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
        when(dataManager.getByKey("my-doc")).thenReturn(Optional.of(sd));

        var result = service.artefact("my-doc", null);

        assertThat(result.key()).isEqualTo("my-doc");
        verify(dataManager).getByKey("my-doc");
    }

    @Test
    void artefactByUuidDelegates() {
        UUID id = UUID.randomUUID();
        var sd = SharedData.builder("doc").id(id)
                .content("data").complete(true).sizeBytes(4)
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
        when(dataManager.getByUuid(id)).thenReturn(Optional.of(sd));

        var result = service.artefact(null, id);

        assertThat(result.id()).isEqualTo(id);
        verify(dataManager).getByUuid(id);
    }

    @Test
    void artefactThrowsWhenNeitherKeyNorId() {
        assertThatThrownBy(() -> service.artefact(null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void artefactRefsReturnsMessageRefs() {
        Long messageId = 42L;
        var ref = new ArtefactRef("urn:test", ArtefactType.DOCUMENT, "doc", null);
        var msg = Message.builder().id(messageId).channelId(UUID.randomUUID())
                .sender("alice").content("content")
                .artefactRefs(List.of(ref)).createdAt(Instant.now()).build();
        when(consumerMessaging.findById(messageId)).thenReturn(Optional.of(msg));

        var result = service.artefactRefs(messageId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).uri()).isEqualTo("urn:test");
    }

    @Test
    void artefactsListsAll() {
        var sd = SharedData.builder("doc").id(UUID.randomUUID())
                .content("x").complete(true).sizeBytes(1)
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
        when(dataManager.listAll()).thenReturn(List.of(sd));

        var result = service.artefacts();

        assertThat(result).hasSize(1);
    }

    @Test
    void shareArtefactDelegates() {
        var sd = SharedData.builder("doc").id(UUID.randomUUID())
                .content("data").complete(true).sizeBytes(4)
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
        when(dataManager.store("doc", "desc", "alice", "data", false, true)).thenReturn(sd);

        var result = service.shareArtefact("doc", "desc", "alice", "data");

        assertThat(result.key()).isEqualTo("doc");
    }

    @Test
    void beginArtefactDelegates() {
        var sd = SharedData.builder("doc").id(UUID.randomUUID())
                .content("chunk1").complete(false).sizeBytes(6)
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
        when(dataManager.store("doc", "desc", "alice", "chunk1", false, false)).thenReturn(sd);

        var result = service.beginArtefact("doc", "desc", "alice", "chunk1");

        assertThat(result.complete()).isFalse();
    }

    @Test
    void isGcEligibleDelegates() {
        UUID id = UUID.randomUUID();
        when(dataManager.isGcEligible(id)).thenReturn(true);

        assertThat(service.isGcEligible(id)).isTrue();
    }

    @Test
    void revokeArtefactDeletesAndReturnsTrue() {
        UUID id = UUID.randomUUID();
        var sd = SharedData.builder("doc").id(id)
                .content("x").complete(true).sizeBytes(1)
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
        when(dataManager.getByUuid(id)).thenReturn(Optional.of(sd));

        var result = service.revokeArtefact(id);

        assertThat(result).isTrue();
        verify(dataManager).delete(id);
    }

    @Test
    void revokeArtefactReturnsFalseWhenNotFound() {
        UUID id = UUID.randomUUID();
        when(dataManager.getByUuid(id)).thenReturn(Optional.empty());

        var result = service.revokeArtefact(id);

        assertThat(result).isFalse();
        verify(dataManager, never()).delete(any());
    }
}
