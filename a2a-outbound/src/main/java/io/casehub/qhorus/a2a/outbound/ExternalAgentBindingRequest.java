package io.casehub.qhorus.a2a.outbound;

public record ExternalAgentBindingRequest(
        String endpoint,
        String authConfigKey,
        String protocolVersion) {}
