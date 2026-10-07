package io.casehub.qhorus.cluster;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.mockito.Mockito.verify;

class HeartbeatSchedulerTest {

    @Test
    void tick_delegates_to_heartbeat_service() {
        HeartbeatService service = Mockito.mock(HeartbeatService.class);
        HeartbeatScheduler scheduler = new HeartbeatScheduler();
        scheduler.heartbeatService = service;

        scheduler.tick();

        verify(service).tick();
    }
}
