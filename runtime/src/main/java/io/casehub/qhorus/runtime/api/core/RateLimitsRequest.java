package io.casehub.qhorus.runtime.api.core;

public record RateLimitsRequest(Integer perChannel, Integer perInstance) {}
