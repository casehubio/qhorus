package io.casehub.qhorus.graphql.dto;

@org.eclipse.microprofile.graphql.Type("DeleteMessageResult")
public record DeleteMessageResultType(
        Long messageId,
        boolean deleted,
        String sender,
        String messageType,
        String preview,
        String status) {
}
