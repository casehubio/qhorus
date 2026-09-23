package io.casehub.qhorus.runtime.api.core;

import java.util.Set;

public record TypeConstraintsRequest(Set<String> allowedTypes, Set<String> deniedTypes) {}
