package io.casehub.qhorus.api.event;

import io.casehub.qhorus.api.instance.ExternalAgentBinding;

public record BindingVerificationRequestedEvent(ExternalAgentBinding binding) {}
