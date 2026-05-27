package io.casehub.qhorus.runtime.watchdog;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;

import io.casehub.qhorus.api.watchdog.WatchdogAlertEvent;

@ApplicationScoped
public class AlertEventCapture {

    public static final CopyOnWriteArrayList<WatchdogAlertEvent> events = new CopyOnWriteArrayList<>();
    private static volatile CountDownLatch latch = new CountDownLatch(0);

    public static void expectCount(int n) {
        events.clear();
        latch = new CountDownLatch(n);
    }

    public static boolean await(long timeout, TimeUnit unit) throws InterruptedException {
        return latch.await(timeout, unit);
    }

    void onAlert(@ObservesAsync WatchdogAlertEvent event) {
        events.add(event);
        latch.countDown();
    }
}
