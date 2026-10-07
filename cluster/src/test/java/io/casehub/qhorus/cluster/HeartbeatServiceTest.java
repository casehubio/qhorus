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
    void tickSkipsDeadNodesOnNonProbeTicks() {
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
    void deadPeersReProbeEveryFifthTick() {
        clusterManager.recordMiss("node-2");
        clusterManager.recordMiss("node-2");
        assertThat(clusterManager.peerStates().get("node-2").state()).isEqualTo(NodeState.DEAD);

        var resp = new HeartbeatResponse("node-3", NOW, clusterManager.ringHash(), "UP");
        when(heartbeatCaller.apply(any())).thenReturn(resp);

        // Ticks 1-4: DEAD peer not probed
        for (int i = 0; i < 4; i++) {
            reset(heartbeatCaller);
            when(heartbeatCaller.apply(any())).thenReturn(resp);
            service.tick();
            verify(heartbeatCaller, times(1)).apply(any()); // only node-3
        }

        // Tick 5: DEAD peer IS probed
        reset(heartbeatCaller);
        when(heartbeatCaller.apply(any())).thenReturn(resp);
        service.tick();
        verify(heartbeatCaller, times(2)).apply(any()); // node-2 + node-3
    }

    @Test
    void deadPeerRecoveredOnSuccessfulProbe() {
        clusterManager.recordMiss("node-2");
        clusterManager.recordMiss("node-2");
        assertThat(clusterManager.peerStates().get("node-2").state()).isEqualTo(NodeState.DEAD);

        var resp2 = new HeartbeatResponse("node-2", NOW, clusterManager.ringHash(), "UP");
        var resp3 = new HeartbeatResponse("node-3", NOW, clusterManager.ringHash(), "UP");
        when(heartbeatCaller.apply(any())).thenReturn(resp3);

        // Advance to 5th tick where DEAD peers are probed
        for (int i = 0; i < 4; i++) {
            service.tick();
        }
        reset(heartbeatCaller);
        when(heartbeatCaller.apply(any())).thenReturn(resp3);
        // node-2 probe also succeeds — responds
        when(heartbeatCaller.apply(clusterManager.peerStates().get("node-2").nodeInfo())).thenReturn(resp2);
        service.tick();

        // node-2 should transition back to ALIVE
        assertThat(clusterManager.peerStates().get("node-2").state()).isEqualTo(NodeState.ALIVE);
        assertThat(clusterManager.clusterSize()).isEqualTo(3);
    }


    @Test
    void buildLocalResponseReturnsNodeInfo() {
        HeartbeatResponse resp = service.buildLocalResponse();
        assertThat(resp.nodeId()).isEqualTo("node-1");
        assertThat(resp.ringHash()).isEqualTo(clusterManager.ringHash());
        assertThat(resp.status()).isEqualTo("UP");
    }
}
