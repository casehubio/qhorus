package io.casehub.qhorus.graphql.agents;

import io.casehub.qhorus.api.channel.Presence;
import io.casehub.qhorus.api.channel.PresenceStatus;
import io.casehub.qhorus.api.channel.PresenceTracker;
import io.casehub.qhorus.api.instance.Instance;
import io.casehub.qhorus.api.instance.InstanceInfo;
import io.casehub.qhorus.api.instance.InstanceManager;
import io.casehub.qhorus.api.instance.RegisterResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AgentsServiceTest {

    private AgentsService service;
    private InstanceManager instanceManager;
    private PresenceTracker presenceTracker;

    @BeforeEach
    void setUp() {
        instanceManager = mock(InstanceManager.class);
        presenceTracker = mock(PresenceTracker.class);
        service = new AgentsService(instanceManager, presenceTracker);
    }

    @Test
    void instancesReturnsAllWhenNoCapabilityFilter() {
        var info = new InstanceInfo("agent-1", "desc", "ONLINE",
                List.of("summarize"), Instant.now().toString(), false);
        when(instanceManager.listInfo()).thenReturn(List.of(info));

        var result = service.instances(null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).instanceId()).isEqualTo("agent-1");
        verify(instanceManager).listInfo();
    }

    @Test
    void instancesFiltersByCapability() {
        var info = new InstanceInfo("agent-1", "desc", "ONLINE",
                List.of("summarize"), Instant.now().toString(), false);
        when(instanceManager.findInfoByCapability("summarize")).thenReturn(List.of(info));

        var result = service.instances("summarize");

        assertThat(result).hasSize(1);
        verify(instanceManager).findInfoByCapability("summarize");
        verify(instanceManager, never()).listInfo();
    }

    @Test
    void instanceByIdReturnsInfo() {
        var info = new InstanceInfo("agent-1", "desc", "ONLINE",
                List.of(), Instant.now().toString(), false);
        when(instanceManager.findInfo("agent-1")).thenReturn(info);

        var result = service.instance("agent-1");

        assertThat(result.instanceId()).isEqualTo("agent-1");
    }

    @Test
    void instanceByIdThrowsWhenNotFound() {
        when(instanceManager.findInfo("missing"))
                .thenThrow(new IllegalArgumentException("Instance not found: missing"));

        assertThatThrownBy(() -> service.instance("missing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing");
    }

    @Test
    void registerDelegatesToManager() {
        var inst = Instance.builder("agent-1").description("desc")
                .id(UUID.randomUUID()).status("ONLINE")
                .lastSeen(Instant.now()).registeredAt(Instant.now()).build();
        var response = new RegisterResponse("agent-1", List.of("ch1"), List.of());
        when(instanceManager.register("agent-1", "desc", List.of("cap"), false))
                .thenReturn(inst);
        when(instanceManager.listInfo()).thenReturn(List.of());

        var result = service.register("agent-1", "desc", List.of("cap"), false);

        assertThat(result.instanceId()).isEqualTo("agent-1");
    }

    @Test
    void deregisterDelegatesToManager() {
        var result = service.deregister("agent-1");

        verify(instanceManager).deregister("agent-1");
        assertThat(result).isTrue();
    }

    @Test
    void presenceReturnsMemberStatus() {
        var p = new Presence("member-1", PresenceStatus.ONLINE, PresenceStatus.ONLINE,
                Instant.now(), null);
        when(presenceTracker.getPresence("member-1")).thenReturn(p);

        var result = service.presence("member-1");

        assertThat(result.memberId()).isEqualTo("member-1");
        assertThat(result.status()).isEqualTo(PresenceStatus.ONLINE);
    }

    @Test
    void channelPresenceDelegatesToTracker() {
        var channelId = UUID.randomUUID();
        var p = new Presence("m-1", PresenceStatus.AVAILABLE, PresenceStatus.AVAILABLE,
                Instant.now(), "working");
        when(presenceTracker.getChannelPresence(channelId)).thenReturn(List.of(p));

        var result = service.channelPresence(channelId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).memberId()).isEqualTo("m-1");
    }

    @Test
    void setPresenceDelegatesToTracker() {
        var p = new Presence("member-1", PresenceStatus.BUSY, PresenceStatus.BUSY,
                Instant.now(), "in meeting");
        when(presenceTracker.getPresence("member-1")).thenReturn(p);

        var result = service.setPresence("BUSY", "in meeting", "member-1");

        verify(presenceTracker).heartbeat(PresenceStatus.BUSY, "in meeting");
        assertThat(result.status()).isEqualTo(PresenceStatus.BUSY);
    }
}
