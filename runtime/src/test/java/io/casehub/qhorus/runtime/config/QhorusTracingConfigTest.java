package io.casehub.qhorus.runtime.config;

import static org.assertj.core.api.Assertions.*;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@QuarkusTest
class QhorusTracingConfigTest {

    @Inject QhorusTracingConfig config;

    @Test
    void defaults_are_all_true() {
        assertThat(config.enabled()).isTrue();
        assertThat(config.dispatch()).isTrue();
        assertThat(config.commitments()).isTrue();
        assertThat(config.fanOut()).isTrue();
        assertThat(config.ledgerWrite()).isTrue();
        assertThat(config.delivery()).isTrue();
    }
}
