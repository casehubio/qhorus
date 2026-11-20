package io.casehub.qhorus.playbook.bridge;

import java.util.UUID;

public record DeadlineKey(UUID channelId, String protocolName, String deadlineName) {}
