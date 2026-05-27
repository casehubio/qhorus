package io.casehub.qhorus.api;

import java.util.HashMap;
import java.util.Map;

public class WatchdogAlertEndpointsProfile extends WatchdogEnabledProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> config = new HashMap<>(super.getConfigOverrides());
        config.put("casehub.qhorus.watchdog.alert.endpoints[0].connector-id", "slack");
        config.put("casehub.qhorus.watchdog.alert.endpoints[0].destination", "https://hooks.slack.com/test");
        return config;
    }
}
