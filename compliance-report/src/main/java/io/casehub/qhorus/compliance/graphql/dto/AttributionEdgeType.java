package io.casehub.qhorus.compliance.graphql.dto;

import io.casehub.qhorus.compliance.model.AttributionEdge;
import org.eclipse.microprofile.graphql.Type;

@Type("AttributionEdge")
public record AttributionEdgeType(
        String from,
        String to,
        String type,
        Long elapsedMs) {

    public static AttributionEdgeType from(AttributionEdge e) {
        return new AttributionEdgeType(e.from(), e.to(), e.type(), e.elapsedMs());
    }
}
