package io.casehub.qhorus.runtime.store.jpa;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.qhorus.api.message.Message;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.runtime.message.MessageEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class JpaMessageStoreRecentTest {

    @Test
    @SuppressWarnings("unchecked")
    void recentMessagesPreserveCorrectionsAndRetractions() {
        UUID channelId = UUID.randomUUID();
        var store = new JpaMessageStore();
        store.currentPrincipal = mock(CurrentPrincipal.class);
        when(store.currentPrincipal.tenancyId()).thenReturn("default");
        store.em = mock(EntityManager.class);
        TypedQuery<MessageEntity> query = mock(TypedQuery.class, RETURNS_SELF);
        when(store.em.createQuery(anyString(), eq(MessageEntity.class))).thenReturn(query);
        Message correction = Message.builder().id(11L).channelId(channelId)
                .messageType(MessageType.STATUS).correctsMessageId(10L).build();
        Message retraction = correction.toBuilder().id(12L).retraction(true).build();
        when(query.getResultList()).thenReturn(List.of(
                MessageEntity.fromDomain(retraction), MessageEntity.fromDomain(correction)));

        var recent = store.findRecent(channelId, 10);

        assertThat(recent).extracting(view -> view.id()).containsExactly(11L, 12L);
        assertThat(recent).extracting(view -> view.correctsMessageId()).containsExactly(10L, 10L);
        assertThat(recent).extracting(view -> view.retraction()).containsExactly(false, true);
    }
}
