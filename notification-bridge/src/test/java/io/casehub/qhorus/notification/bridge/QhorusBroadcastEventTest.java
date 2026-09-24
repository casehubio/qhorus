package io.casehub.qhorus.notification.bridge;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QhorusBroadcastEventTest {

    @Test
    void type_includesCapabilityTag() {
        var event = new QhorusBroadcastEvent("tenant-1", "agent-2", "analyzer",
                UUID.randomUUID(), "broadcast/analyzer", "agent-1", "STATUS", "anomaly detected");
        assertThat(event.type()).isEqualTo("io.casehub.qhorus.broadcast.analyzer");
    }

    @Test
    void tenancyId_returnsValue() {
        var event = new QhorusBroadcastEvent("tenant-1", "agent-2", "analyzer",
                UUID.randomUUID(), "broadcast/analyzer", "agent-1", "STATUS", "content");
        assertThat(event.tenancyId()).isEqualTo("tenant-1");
    }

    @Test
    void nullTenancyId_throws() {
        assertThatThrownBy(() -> new QhorusBroadcastEvent(null, "agent-2", "analyzer",
                UUID.randomUUID(), "broadcast/analyzer", "agent-1", "STATUS", "content"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullRecipientId_throws() {
        assertThatThrownBy(() -> new QhorusBroadcastEvent("tenant-1", null, "analyzer",
                UUID.randomUUID(), "broadcast/analyzer", "agent-1", "STATUS", "content"))
                .isInstanceOf(NullPointerException.class);
    }
}
