package io.casehub.qhorus.api.gateway;

import io.casehub.platform.api.identity.ActorType;

public record BackendRegistration(String backendId, String backendType, ActorType actorType) {}
