package io.casehub.qhorus.mesh.auth;

import io.casehub.platform.api.identity.CurrentPrincipal;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/test/identity")
public class IdentityTestResource {

    @Inject CurrentPrincipal principal;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public String identity() {
        return "{\"actorId\":\"" + principal.actorId()
            + "\",\"tenancyId\":\"" + principal.tenancyId() + "\"}";
    }
}
