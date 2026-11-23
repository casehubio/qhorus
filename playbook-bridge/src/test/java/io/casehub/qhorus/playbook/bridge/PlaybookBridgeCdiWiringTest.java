package io.casehub.qhorus.playbook.bridge;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class PlaybookBridgeCdiWiringTest {

    @Inject PlaybookProtocolResolver resolver;
    @Inject PlaybookProtocolLifecycleHandler lifecycleHandler;
    @Inject ProtocolDeadlineScheduler deadlineScheduler;
    @Inject CompilationCache compilationCache;
    @Inject ProtocolDefinitionStore definitionStore;
    @Inject DeadlineSweepScheduler sweepScheduler;
    @Inject PlaybookBridgeBeans bridgeBeans;

    @Test
    void allBeansInjectable() {
        assertThat(resolver).isNotNull();
        assertThat(lifecycleHandler).isNotNull();
        assertThat(deadlineScheduler).isNotNull();
        assertThat(compilationCache).isNotNull();
        assertThat(definitionStore).isNotNull();
        assertThat(sweepScheduler).isNotNull();
        assertThat(bridgeBeans).isNotNull();
    }

    @Test
    void definitionStoreIsJpaImplementation() {
        assertThat(io.quarkus.arc.ClientProxy.unwrap(definitionStore))
                .isInstanceOf(JpaProtocolDefinitionStore.class);
    }
}
