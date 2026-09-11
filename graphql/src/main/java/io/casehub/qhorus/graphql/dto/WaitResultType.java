package io.casehub.qhorus.graphql.dto;

@org.eclipse.microprofile.graphql.Type("WaitResult")
public record WaitResultType(
        boolean found,
        boolean timedOut,
        String correlationId,
        MessageType message,
        String status) {
}
