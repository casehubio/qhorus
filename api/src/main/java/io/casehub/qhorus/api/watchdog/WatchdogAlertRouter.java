package io.casehub.qhorus.api.watchdog;

import java.util.List;

public interface WatchdogAlertRouter {
    List<AlertDeliveryTarget> route(WatchdogAlertEvent event);
}
