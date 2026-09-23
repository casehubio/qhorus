package io.casehub.qhorus.runtime.api.core;

public record MessagePostRequest(String sender, String type, String actorType, String content) {}
