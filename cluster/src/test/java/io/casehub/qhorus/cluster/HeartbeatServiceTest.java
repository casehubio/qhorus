package io.casehub.qhorus.cluster;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HeartbeatServiceTest {

    private ClusterManager clusterManager;
    @SuppressWarnings("unchecked")
    private final Function<NodeInfo, HeartbeatResponse> heartbeatCaller = mock(Function.class);
    private HeartbeatService service;
    private static final Instant NOW = Instant.parse("2026-10-06T12:00:00Z");

    @BeforeEach
    void setUp() {
        clusterManager = Mockito.spy(new ClusterManager(
                "node-1",
                Map.of("node-1", "node-1:8080", "node-2", "node-2:8080", "node-3", "node-3:8080"),
                128, 2, true,
                Clock.fixed(NOW, ZoneId.of("UTC"))));
        service = new HeartbeatService(clusterManager, heartbeatCaller);
    }

    @Test
    void tickRecordsHeartbeatOnSuccess() {
        var resp = new HeartbeatResponse("node-2", NOW, clusterManager.ringHash(), "UP");
        when(heartbeatCaller.apply(any())).thenReturn(resp);
        service.tick();
        verify(clusterManager).recordHeartbeat("node-2");
        verify(clusterManager).recordHeartbeat("node-3");
    }

    @Test
    void tickRecordsMissOnFailure() {
        when(heartbeatCaller.apply(any())).thenThrow(new RuntimeException("connection refused"));
        service.tick();
        verify(clusterManager).recordMiss("node-2");
        verify(clusterManager).recordMiss("node-3");
    }

    @Test
    void tickDoesNotPollDeadNodes() {
        clusterManager.recordMiss("node-2");
        clusterManager.recordMiss("node-2");
        assertThat(clusterManager.peerStates().get("node-2").state()).isEqualTo(NodeState.DEAD);
        reset(heartbeatCaller);
        var resp = new HeartbeatResponse("node-3", NOW, clusterManager.ringHash(), "UP");
        when(heartbeatCaller.apply(any())).thenReturn(resp);
        service.tick();
        verify(heartbeatCaller, times(1)).apply(any());
    }

    @Test
    void buildLocalResponseReturnsNodeInfo() {
        HeartbeatResponse resp = service.buildLocalResponse();
        assertThat(resp.nodeId()).isEqualTo("node-1");
        assertThat(resp.ringHash()).isEqualTo(clusterManager.ringHash());
        assertThat(resp.status()).isEqualTo("UP");
    }
}
