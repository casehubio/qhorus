package io.casehub.qhorus.a2a.outbound.core;

public record ExternalAgentBindingRequest(
        String endpoint,
        String authConfigKey,
        String protocolVersion) {}
