package io.casehub.qhorus.notification.bridge;

import io.casehub.platform.api.datasource.DataSource;
import io.casehub.platform.api.datasource.DataSourceRegistry;
import io.casehub.qhorus.api.message.Commitment;
import io.casehub.qhorus.api.message.CommitmentDeclinedEvent;
import io.casehub.qhorus.api.message.CommitmentExpiredEvent;
import io.casehub.qhorus.api.message.CommitmentState;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.store.CommitmentStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static io.casehub.platform.api.identity.TenancyConstants.PLATFORM_TENANT_ID;
import static io.casehub.platform.api.subscription.SubscriptionConstants.NOTIFICATION_DATASOURCE_PATH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommitmentEventNotifierTest {

    private       CommitmentStore         commitmentStore;
    @SuppressWarnings("unchecked")
    private final DataSource<Object>      dataSource = mock(DataSource.class);
    private       CommitmentEventNotifier notifier;

    private static final UUID   COMMITMENT_ID  = UUID.randomUUID();
    private static final UUID   CHANNEL_ID     = UUID.randomUUID();
    private static final String CORRELATION_ID = UUID.randomUUID().toString();
    private static final String TENANCY_ID     = "tenant-1";
    private static final String REQUESTER      = "agent-requester";
    private static final String OBLIGOR        = "agent-obligor";

    @BeforeEach
    void setUp() {
        commitmentStore = mock(CommitmentStore.class);
        var registry = mock(DataSourceRegistry.class);
        when(registry.resolveSource(NOTIFICATION_DATASOURCE_PATH, PLATFORM_TENANT_ID))
                .thenReturn(Optional.of(dataSource));
        notifier = new CommitmentEventNotifier(commitmentStore, registry);

        when(commitmentStore.findById(COMMITMENT_ID))
                .thenReturn(Optional.of(commitment()));
    }

    @Test
    void declined_fires_declined_event_for_requester() {
        notifier.onDeclined(new CommitmentDeclinedEvent(
                COMMITMENT_ID, CORRELATION_ID, CHANNEL_ID, OBLIGOR, REQUESTER));

        var captor = ArgumentCaptor.forClass(Object.class);
        verify(dataSource).add(captor.capture());
        var fired = (QhorusObligationEvent) captor.getValue();

        assertThat(fired.kind()).isEqualTo(QhorusObligationEvent.Kind.DECLINED);
        assertThat(fired.tenancyId()).isEqualTo(TENANCY_ID);
        assertThat(fired.requester()).isEqualTo(REQUESTER);
        assertThat(fired.obligor()).isEqualTo(OBLIGOR);
        assertThat(fired.channelId()).isEqualTo(CHANNEL_ID);
        assertThat(fired.correlationId()).isEqualTo(CORRELATION_ID);
    }

    @Test
    void declined_with_null_requester_skips() {
        notifier.onDeclined(new CommitmentDeclinedEvent(
                COMMITMENT_ID, CORRELATION_ID, CHANNEL_ID, OBLIGOR, null));

        verify(dataSource, never()).add(any());
    }

    @Test
    void declined_with_blank_requester_skips() {
        notifier.onDeclined(new CommitmentDeclinedEvent(
                COMMITMENT_ID, CORRELATION_ID, CHANNEL_ID, OBLIGOR, ""));

        verify(dataSource, never()).add(any());
    }

    @Test
    void expired_fires_expired_event() {
        notifier.onExpired(new CommitmentExpiredEvent(
                COMMITMENT_ID, CORRELATION_ID, CHANNEL_ID, OBLIGOR, REQUESTER,
                Instant.now().minusSeconds(60)));

        var captor = ArgumentCaptor.forClass(Object.class);
        verify(dataSource).add(captor.capture());
        var fired = (QhorusObligationEvent) captor.getValue();

        assertThat(fired.kind()).isEqualTo(QhorusObligationEvent.Kind.EXPIRED);
        assertThat(fired.requester()).isEqualTo(REQUESTER);
        assertThat(fired.obligor()).isEqualTo(OBLIGOR);
    }

    @Test
    void expired_with_null_obligor_preserves_null() {
        notifier.onExpired(new CommitmentExpiredEvent(
                COMMITMENT_ID, CORRELATION_ID, CHANNEL_ID, null, REQUESTER,
                Instant.now()));

        var captor = ArgumentCaptor.forClass(Object.class);
        verify(dataSource).add(captor.capture());
        var fired = (QhorusObligationEvent) captor.getValue();

        assertThat(fired.obligor()).isNull();
        assertThat(fired.senderId()).isNull();
    }

    @Test
    void expired_with_null_requester_skips() {
        notifier.onExpired(new CommitmentExpiredEvent(
                COMMITMENT_ID, CORRELATION_ID, CHANNEL_ID, OBLIGOR, null,
                Instant.now()));

        verify(dataSource, never()).add(any());
    }

    @Test
    void declined_uses_default_tenancy_when_commitment_not_found() {
        when(commitmentStore.findById(COMMITMENT_ID)).thenReturn(Optional.empty());

        notifier.onDeclined(new CommitmentDeclinedEvent(
                COMMITMENT_ID, CORRELATION_ID, CHANNEL_ID, OBLIGOR, REQUESTER));

        var captor = ArgumentCaptor.forClass(Object.class);
        verify(dataSource).add(captor.capture());
        var fired = (QhorusObligationEvent) captor.getValue();

        assertThat(fired.tenancyId()).isEqualTo("DEFAULT");
    }

    @Test
    void datasource_failure_is_non_fatal() {
        doThrow(new RuntimeException("DS down")).when(dataSource).add(any());

        notifier.onDeclined(new CommitmentDeclinedEvent(
                COMMITMENT_ID, CORRELATION_ID, CHANNEL_ID, OBLIGOR, REQUESTER));
    }

    private Commitment commitment() {
        return Commitment.builder()
                         .id(COMMITMENT_ID)
                         .correlationId(CORRELATION_ID)
                         .channelId(CHANNEL_ID)
                         .messageType(MessageType.COMMAND)
                         .requester(REQUESTER)
                         .obligor(OBLIGOR)
                         .state(CommitmentState.DECLINED)
                         .tenancyId(TENANCY_ID)
                         .createdAt(Instant.now())
                         .build();
    }
}
