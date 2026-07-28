package io.casehub.qhorus.notification.bridge;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QhorusObligationEventTest {

    private static final UUID CHANNEL_ID = UUID.randomUUID();

    @ParameterizedTest
    @EnumSource(QhorusObligationEvent.Kind.class)
    void type_returns_prefixed_kind(QhorusObligationEvent.Kind kind) {
        var event = new QhorusObligationEvent(
                kind, "tenant-1", "obligor-1", "requester-1",
                CHANNEL_ID, "test-channel", "sender-1",
                UUID.randomUUID().toString(), "content");

        assertThat(event.type()).isEqualTo("io.casehub.qhorus.obligation." + kind.name().toLowerCase());
    }

    @Test
    void tenancyId_returned_correctly() {
        var event = new QhorusObligationEvent(
                QhorusObligationEvent.Kind.ASSIGNED, "my-tenant", "o", "r",
                CHANNEL_ID, "ch", "s", "corr", null);

        assertThat(event.tenancyId()).isEqualTo("my-tenant");
    }

    @Test
    void content_is_nullable() {
        var event = new QhorusObligationEvent(
                QhorusObligationEvent.Kind.FULFILLED, "t", "o", "r",
                CHANNEL_ID, "ch", "s", "corr", null);

        assertThat(event.content()).isNull();
    }

    @Test
    void null_kind_rejected() {
        assertThatThrownBy(() -> new QhorusObligationEvent(
                null, "t", "o", "r", CHANNEL_ID, "ch", "s", "corr", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void null_tenancyId_rejected() {
        assertThatThrownBy(() -> new QhorusObligationEvent(
                QhorusObligationEvent.Kind.ASSIGNED, null, "o", "r",
                CHANNEL_ID, "ch", "s", "corr", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void implements_subscribable_event() {
        var event = new QhorusObligationEvent(
                QhorusObligationEvent.Kind.FAILED, "t", "o", "r",
                CHANNEL_ID, "ch", "s", "corr", "body");

        assertThat(event).isInstanceOf(io.casehub.platform.api.subscription.SubscribableEvent.class);
    }
}
