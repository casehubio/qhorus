package io.casehub.qhorus.cluster;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.mockito.Mockito.*;

class InternalSecretFilterTest {

    @Test
    void rejects_request_without_secret_header() {
        InternalSecretFilter filter = new InternalSecretFilter();
        filter.config = mockConfig(Optional.of("my-secret"));

        ContainerRequestContext ctx = mockContext("/internal/dispatch", null);
        filter.filter(ctx);

        verify(ctx).abortWith(argThat(r -> r.getStatus() == 401));
    }

    @Test
    void rejects_request_with_wrong_secret() {
        InternalSecretFilter filter = new InternalSecretFilter();
        filter.config = mockConfig(Optional.of("my-secret"));

        ContainerRequestContext ctx = mockContext("/internal/dispatch", "wrong");
        filter.filter(ctx);

        verify(ctx).abortWith(argThat(r -> r.getStatus() == 401));
    }

    @Test
    void allows_request_with_correct_secret() {
        InternalSecretFilter filter = new InternalSecretFilter();
        filter.config = mockConfig(Optional.of("my-secret"));

        ContainerRequestContext ctx = mockContext("/internal/dispatch", "my-secret");
        filter.filter(ctx);

        verify(ctx, never()).abortWith(any());
    }

    @Test
    void allows_all_when_no_secret_configured() {
        InternalSecretFilter filter = new InternalSecretFilter();
        filter.config = mockConfig(Optional.empty());

        ContainerRequestContext ctx = mockContext("/internal/dispatch", null);
        filter.filter(ctx);

        verify(ctx, never()).abortWith(any());
    }

    @Test
    void ignores_non_internal_paths() {
        InternalSecretFilter filter = new InternalSecretFilter();
        filter.config = mockConfig(Optional.of("my-secret"));

        ContainerRequestContext ctx = mockContext("/api/channels", null);
        filter.filter(ctx);

        verify(ctx, never()).abortWith(any());
    }

    @Test
    void matches_path_without_leading_slash() {
        InternalSecretFilter filter = new InternalSecretFilter();
        filter.config = mockConfig(Optional.of("my-secret"));

        ContainerRequestContext ctx = mockContext("internal/heartbeat", null);
        filter.filter(ctx);

        verify(ctx).abortWith(argThat(r -> r.getStatus() == 401));
    }

    private RelayConfig mockConfig(Optional<String> secret) {
        RelayConfig config = mock(RelayConfig.class);
        when(config.internalSecret()).thenReturn(secret);
        return config;
    }

    private ContainerRequestContext mockContext(String path, String secretHeader) {
        ContainerRequestContext ctx = mock(ContainerRequestContext.class);
        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getPath()).thenReturn(path);
        when(ctx.getUriInfo()).thenReturn(uriInfo);
        when(ctx.getHeaderString("X-Internal-Secret")).thenReturn(secretHeader);
        return ctx;
    }
}
