package io.casehub.qhorus.notification.bridge;

import io.casehub.platform.api.notification.NotificationSeverity;
import io.casehub.platform.api.subscription.Subscription;
import io.casehub.platform.api.subscription.SubscriptionInput;
import io.casehub.platform.api.subscription.SubscriptionScope;
import io.casehub.platform.api.subscription.SubscriptionStore;
import io.casehub.platform.api.subscription.NotificationTemplate;
import io.casehub.platform.api.subscription.TargetType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class QhorusSubscriptionBootstrapTest {

    private SubscriptionStore subscriptionStore;
    private QhorusSubscriptionBootstrap bootstrap;

    @BeforeEach
    void setUp() {
        subscriptionStore = mock(SubscriptionStore.class);
        when(subscriptionStore.findAllEnabled()).thenReturn(Stream.empty());
        bootstrap = new QhorusSubscriptionBootstrap(subscriptionStore);
    }

    @Test
    void registers_five_default_subscriptions_when_none_exist() {
        bootstrap.onStartup(null);

        verify(subscriptionStore, times(5)).store(any(SubscriptionInput.class));
    }

    @Test
    void skips_registration_when_subscription_already_exists() {
        var existing = new Subscription(
                "id-1", "system:qhorus", "PLATFORM",
                "qhorus.obligation.assigned",
                "io.casehub.qhorus.obligation.assigned",
                List.of(), List.of(), false,
                new NotificationTemplate("t", null, NotificationSeverity.INFO,
                        "qhorus.obligation.assigned", null, "channel", "channelId", "senderId"),
                true, SubscriptionScope.SYSTEM, Instant.now(), Instant.now());

        when(subscriptionStore.findAllEnabled()).thenReturn(Stream.of(existing));
        bootstrap.onStartup(null);

        verify(subscriptionStore, times(4)).store(any(SubscriptionInput.class));
    }

    @Test
    void assigned_subscription_targets_obligor_field() {
        bootstrap.onStartup(null);

        var captor = ArgumentCaptor.forClass(SubscriptionInput.class);
        verify(subscriptionStore, times(5)).store(captor.capture());

        var assigned = captor.getAllValues().stream()
                .filter(i -> i.eventType().endsWith(".assigned"))
                .findFirst().orElseThrow();

        assertThat(assigned.targets()).hasSize(1);
        assertThat(assigned.targets().get(0).type()).isEqualTo(TargetType.EVENT_FIELD);
        assertThat(assigned.targets().get(0).id()).isEqualTo("obligor");
        assertThat(assigned.scope()).isEqualTo(SubscriptionScope.SYSTEM);
    }

    @Test
    void fulfilled_subscription_targets_requester_field() {
        bootstrap.onStartup(null);

        var captor = ArgumentCaptor.forClass(SubscriptionInput.class);
        verify(subscriptionStore, times(5)).store(captor.capture());

        var fulfilled = captor.getAllValues().stream()
                .filter(i -> i.eventType().endsWith(".fulfilled"))
                .findFirst().orElseThrow();

        assertThat(fulfilled.targets().get(0).id()).isEqualTo("requester");
        assertThat(fulfilled.template().severity()).isEqualTo(NotificationSeverity.INFO);
    }

    @Test
    void failed_subscription_has_warning_severity() {
        bootstrap.onStartup(null);

        var captor = ArgumentCaptor.forClass(SubscriptionInput.class);
        verify(subscriptionStore, times(5)).store(captor.capture());

        var failed = captor.getAllValues().stream()
                .filter(i -> i.eventType().endsWith(".failed"))
                .findFirst().orElseThrow();

        assertThat(failed.template().severity()).isEqualTo(NotificationSeverity.WARNING);
    }

    @Test
    void store_failure_is_non_fatal() {
        doThrow(new RuntimeException("DB down")).when(subscriptionStore).store(any());

        bootstrap.onStartup(null);
    }
}
