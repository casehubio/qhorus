package io.casehub.qhorus.api.channel;

import io.casehub.qhorus.api.message.MessageType;

import java.util.Set;

public record TypeConstraintsRequest(
        Set<MessageType> allowedTypes,
        Set<MessageType> deniedTypes) {}
