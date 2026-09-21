package io.casehub.qhorus.api.channel;

import java.util.List;

public record ChannelEnforcementConfig(
        String enforcementMode,
        List<String> enforcementExclusions,
        List<String> availableSources) {}
