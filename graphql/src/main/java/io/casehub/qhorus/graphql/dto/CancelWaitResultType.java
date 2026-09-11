package io.casehub.qhorus.graphql.dto;

@org.eclipse.microprofile.graphql.Type("CancelWaitResult")
public record CancelWaitResultType(
        String correlationId,
        boolean cancelled,
        String message) {
}
