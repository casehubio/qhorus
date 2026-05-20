package io.casehub.qhorus;

import io.casehub.qhorus.runtime.api.A2AActorResolver;
import io.casehub.qhorus.runtime.api.A2AChannelBackend;
import io.casehub.qhorus.runtime.api.A2AResource;
import io.casehub.qhorus.runtime.api.AgentCardResource;
import io.casehub.qhorus.runtime.api.ReactiveA2AResource;
import io.casehub.qhorus.runtime.api.ReactiveAgentCardResource;
import io.casehub.qhorus.runtime.message.ReactiveMessageService;
import io.casehub.qhorus.runtime.mcp.QhorusMcpTools;
import io.casehub.qhorus.runtime.mcp.ReactiveQhorusMcpTools;
import io.quarkus.arc.Arc;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that when HIBERNATE_REACTIVE capability is present but no reactive
 * datasource driver is available (H2 test environment), reactive beans are
 * excluded from the CDI container and blocking beans are active.
 *
 * This test runs in the runtime module itself where quarkus-hibernate-reactive-panache
 * is still on the classpath (needed for compilation). The build-time property
 * {@code casehub.qhorus.reactive.enabled} defaults to {@code false} (via
 * {@code @WithDefault} in {@code QhorusBuildTimeConfig}), so
 * {@code @IfBuildProperty(casehub.qhorus.reactive.enabled=true)} on reactive beans
 * evaluates to false and they are excluded from the CDI container at augmentation time.
 */
@QuarkusTest
class ReactiveCapabilityExclusionTest {

    @Test
    void blockingMcpTools_isActiveWhenNoReactiveDriver() {
        var instance = Arc.container().select(QhorusMcpTools.class);
        assertFalse(instance.isUnsatisfied(),
                "QhorusMcpTools must be active when no reactive datasource driver is present");
    }

    @Test
    void reactiveMcpTools_isExcludedWhenNoReactiveDriver() {
        var instance = Arc.container().select(ReactiveQhorusMcpTools.class);
        assertTrue(instance.isUnsatisfied(),
                "ReactiveQhorusMcpTools must not be in CDI when no reactive datasource driver is present");
    }

    @Test
    void blockingA2AResource_isActive() {
        assertFalse(Arc.container().select(A2AResource.class).isUnsatisfied());
    }

    @Test
    void reactiveA2AResource_isExcluded() {
        assertTrue(Arc.container().select(ReactiveA2AResource.class).isUnsatisfied());
    }

    @Test
    void blockingAgentCardResource_isActive() {
        assertFalse(Arc.container().select(AgentCardResource.class).isUnsatisfied());
    }

    @Test
    void reactiveAgentCardResource_isExcluded() {
        assertTrue(Arc.container().select(ReactiveAgentCardResource.class).isUnsatisfied());
    }

    @Test
    void a2aChannelBackend_isActive() {
        assertFalse(Arc.container().select(A2AChannelBackend.class).isUnsatisfied());
    }

    @Test
    void a2aActorResolver_isActive() {
        assertFalse(Arc.container().select(A2AActorResolver.class).isUnsatisfied());
    }

    @Test
    void reactiveMessageService_isExcluded() {
        assertTrue(Arc.container().select(ReactiveMessageService.class).isUnsatisfied());
    }
}
