package io.casehub.qhorus.cluster;

import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
@IfBuildProperty(name = "casehub.qhorus.relay.enabled", stringValue = "true", enableIfMissing = false)
public class QuorumViolationExceptionMapper implements ExceptionMapper<QuorumViolationException> {

    @Override
    public Response toResponse(QuorumViolationException exception) {
        return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                .entity("{\"error\":\"" + exception.getMessage() + "\"}")
                .type("application/json")
                .build();
    }
}
