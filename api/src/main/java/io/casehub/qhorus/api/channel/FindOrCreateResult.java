package io.casehub.qhorus.api.channel;

public record FindOrCreateResult(Channel channel, boolean wasCreated) {}
