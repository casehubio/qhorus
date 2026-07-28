package io.casehub.qhorus.notification.bridge;

import io.casehub.platform.api.datasource.DataSource;
import io.casehub.platform.api.datasource.DataSourceRegistry;
import io.casehub.qhorus.api.gateway.MessageObserver;
import io.casehub.qhorus.api.gateway.MessageReceivedEvent;
import io.casehub.qhorus.api.message.Commitment;
import io.casehub.qhorus.api.message.CommitmentState;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.store.CommitmentStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static io.casehub.platform.api.subscription.SubscriptionConstants.NOTIFICATION_DATASOURCE_PATH;
import static io.casehub.platform.api.identity.TenancyConstants.PLATFORM_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationBridgeObserverTest {

    private       CommitmentStore            commitmentStore;
    @SuppressWarnings("unchecked")
    private final DataSource<Object>         dataSource = mock(DataSource.class);
    private       NotificationBridgeObserver observer;

    private static final UUID   CHANNEL_ID     = UUID.randomUUID();
    private static final String CHANNEL_NAME   = "test-channel";
    private static final String TENANCY_ID     = "tenant-1";
    private static final String CORRELATION_ID = UUID.randomUUID().toString();
    private static final String REQUESTER      = "agent-requester";
    private static final String OBLIGOR        = "agent-obligor";

    @BeforeEach
    void setUp() {
        commitmentStore = mock(CommitmentStore.class);
        var registry = mock(DataSourceRegistry.class);
        when(registry.resolveSource(NOTIFICATION_DATASOURCE_PATH, PLATFORM_TENANT_ID))
                .thenReturn(Optional.of(dataSource));
        observer = new NotificationBridgeObserver(commitmentStore, registry);
    }

    @Test
    void command_with_obligor_fires_assigned_event() {
        when(commitmentStore.findByCorrelationId(CORRELATION_ID))
                .thenReturn(Optional.of(commitment(REQUESTER, OBLIGOR)));

        observer.onMessage(event(MessageType.COMMAND, REQUESTER, "Do this task"));

        var captor = ArgumentCaptor.forClass(Object.class);
        verify(dataSource).add(captor.capture());
        var fired = (QhorusObligationEvent) captor.getValue();

        assertThat(fired.kind()).isEqualTo(QhorusObligationEvent.Kind.ASSIGNED);
        assertThat(fired.tenancyId()).isEqualTo(TENANCY_ID);
        assertThat(fired.obligor()).isEqualTo(OBLIGOR);
        assertThat(fired.requester()).isEqualTo(REQUESTER);
        assertThat(fired.channelId()).isEqualTo(CHANNEL_ID);
        assertThat(fired.channelName()).isEqualTo(CHANNEL_NAME);
        assertThat(fired.senderId()).isEqualTo(REQUESTER);
        assertThat(fired.correlationId()).isEqualTo(CORRELATION_ID);
        assertThat(fired.content()).isEqualTo("Do this task");
    }

    @Test
    void command_without_commitment_skips() {
        when(commitmentStore.findByCorrelationId(CORRELATION_ID))
                .thenReturn(Optional.empty());

        observer.onMessage(event(MessageType.COMMAND, REQUESTER, "Do this"));

        verify(dataSource, never()).add(any());
    }

    @Test
    void command_with_blank_obligor_skips() {
        when(commitmentStore.findByCorrelationId(CORRELATION_ID))
                .thenReturn(Optional.of(commitment(REQUESTER, "")));

        observer.onMessage(event(MessageType.COMMAND, REQUESTER, "Do this"));

        verify(dataSource, never()).add(any());
    }

    @Test
    void done_fires_fulfilled_event_for_requester() {
        when(commitmentStore.findByCorrelationId(CORRELATION_ID))
                .thenReturn(Optional.of(commitment(REQUESTER, OBLIGOR)));

        observer.onMessage(event(MessageType.DONE, OBLIGOR, "Task complete"));

        var captor = ArgumentCaptor.forClass(Object.class);
        verify(dataSource).add(captor.capture());
        var fired = (QhorusObligationEvent) captor.getValue();

        assertThat(fired.kind()).isEqualTo(QhorusObligationEvent.Kind.FULFILLED);
        assertThat(fired.requester()).isEqualTo(REQUESTER);
        assertThat(fired.obligor()).isEqualTo(OBLIGOR);
    }

    @Test
    void failure_fires_failed_event() {
        when(commitmentStore.findByCorrelationId(CORRELATION_ID))
                .thenReturn(Optional.of(commitment(REQUESTER, OBLIGOR)));

        observer.onMessage(event(MessageType.FAILURE, OBLIGOR, "Could not complete"));

        var captor = ArgumentCaptor.forClass(Object.class);
        verify(dataSource).add(captor.capture());
        var fired = (QhorusObligationEvent) captor.getValue();

        assertThat(fired.kind()).isEqualTo(QhorusObligationEvent.Kind.FAILED);
    }

    @Test
    void done_from_requester_to_self_skips() {
        when(commitmentStore.findByCorrelationId(CORRELATION_ID))
                .thenReturn(Optional.of(commitment(REQUESTER, OBLIGOR)));

        observer.onMessage(event(MessageType.DONE, REQUESTER, "Self-resolved"));

        verify(dataSource, never()).add(any());
    }

    @Test
    void status_message_skips() {
        observer.onMessage(event(MessageType.STATUS, OBLIGOR, "Progress update"));

        verify(commitmentStore, never()).findByCorrelationId(any());
        verify(dataSource, never()).add(any());
    }

    @Test
    void event_message_skips() {
        var evt = new MessageReceivedEvent(
                1L, CHANNEL_NAME, CHANNEL_ID, TENANCY_ID,
                MessageType.EVENT, "system:telemetry", CORRELATION_ID,
                Instant.now(), null, null);

        observer.onMessage(evt);

        verify(commitmentStore, never()).findByCorrelationId(any());
        verify(dataSource, never()).add(any());
    }

    @Test
    void null_correlationId_skips_all_processing() {
        var evt = new MessageReceivedEvent(
                1L, CHANNEL_NAME, CHANNEL_ID, TENANCY_ID,
                MessageType.COMMAND, REQUESTER, null,
                Instant.now(), "Do this", null);

        observer.onMessage(evt);

        verify(commitmentStore, never()).findByCorrelationId(any());
        verify(dataSource, never()).add(any());
    }

    @Test
    void scope_is_local() {
        assertThat(observer.scope()).isEqualTo(MessageObserver.Scope.LOCAL);
    }

    @Test
    void datasource_add_failure_is_non_fatal() {
        when(commitmentStore.findByCorrelationId(CORRELATION_ID))
                .thenReturn(Optional.of(commitment(REQUESTER, OBLIGOR)));
        doThrow(new RuntimeException("DS down")).when(dataSource).add(any());

        observer.onMessage(event(MessageType.COMMAND, REQUESTER, "Do this"));
    }

    @Test
    void long_content_is_truncated() {
        when(commitmentStore.findByCorrelationId(CORRELATION_ID))
                .thenReturn(Optional.of(commitment(REQUESTER, OBLIGOR)));

        observer.onMessage(event(MessageType.COMMAND, REQUESTER, "a".repeat(300)));

        var captor = ArgumentCaptor.forClass(Object.class);
        verify(dataSource).add(captor.capture());
        var fired = (QhorusObligationEvent) captor.getValue();

        assertThat(fired.content()).hasSize(200);
    }

    @Test
    void datasource_not_registered_skips_silently() {
        var emptyRegistry = mock(DataSourceRegistry.class);
        when(emptyRegistry.resolveSource(any(), any())).thenReturn(Optional.empty());
        var obs = new NotificationBridgeObserver(commitmentStore, emptyRegistry);

        when(commitmentStore.findByCorrelationId(CORRELATION_ID))
                .thenReturn(Optional.of(commitment(REQUESTER, OBLIGOR)));

        obs.onMessage(event(MessageType.COMMAND, REQUESTER, "Do this"));

        verify(dataSource, never()).add(any());
    }

    @Test
    void truncate_helper() {
        assertThat(NotificationBridgeObserver.truncate(null, 10)).isNull();
        assertThat(NotificationBridgeObserver.truncate("short", 10)).isEqualTo("short");
        assertThat(NotificationBridgeObserver.truncate("a".repeat(300), 200)).hasSize(200);
    }

    private MessageReceivedEvent event(MessageType type, String sender, String content) {
        return new MessageReceivedEvent(
                1L, CHANNEL_NAME, CHANNEL_ID, TENANCY_ID,
                type, sender, CORRELATION_ID,
                Instant.now(), content, null);
    }

    private Commitment commitment(String requester, String obligor) {
        return Commitment.builder()
                         .id(UUID.randomUUID())
                         .correlationId(CORRELATION_ID)
                         .channelId(CHANNEL_ID)
                         .messageType(MessageType.COMMAND)
                         .requester(requester)
                         .obligor(obligor)
                         .state(CommitmentState.OPEN)
                         .tenancyId(TENANCY_ID)
                         .createdAt(Instant.now())
                         .build();
    }
}
