package io.casehub.qhorus.playbook.bridge;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.qhorus.api.spi.ProtocolLifecycleContext;

class PlaybookProtocolLifecycleHandlerTest {

    private PlaybookProtocolLifecycleHandler handler;
    private InMemoryProtocolDefinitionStore store;
    private List<DeadlineKey> registeredDeadlines;
    private ProtocolDeadlineScheduler scheduler;

    @BeforeEach
    void setUp() {
        store = new InMemoryProtocolDefinitionStore();
        registeredDeadlines = new ArrayList<>();
        scheduler = new ProtocolDeadlineScheduler(registeredDeadlines::add);
        handler = new PlaybookProtocolLifecycleHandler();
        handler.definitionStore = store;
        handler.deadlineScheduler = scheduler;
        handler.protocolRegistry = null;
    }

    private ProtocolDefinition def(UUID channelId, String name, String yaml) {
        return new ProtocolDefinition(UUID.randomUUID(), channelId, name,
                yaml, "DEFAULT", Instant.now());
    }

    @Test
    void supports_true_forExistingDefinition() {
        store.put(def(UUID.randomUUID(), "approval-gate", "version: 1.0"));
        assertThat(handler.supports("approval-gate")).isTrue();
    }

    @Test
    void supports_false_forUnknown() {
        assertThat(handler.supports("nonexistent")).isFalse();
    }

    @Test
    void supports_false_forStaticProtocol() {
        store.put(def(UUID.randomUUID(), "STATIC", "version: 1.0"));
        handler.protocolRegistry = new io.casehub.qhorus.runtime.message.protocol.ProtocolRegistry(
                List.of(new io.casehub.qhorus.api.spi.ChannelProtocol() {
                    @Override public String protocolName() { return "STATIC"; }
                    @Override public List<io.casehub.qhorus.api.spi.DispatchAdvisory> evaluate(
                            io.casehub.qhorus.api.spi.ProtocolContext ctx) { return List.of(); }
                }));
        assertThat(handler.supports("STATIC")).isFalse();
    }

    @Test
    void onInit_registersDeadlines() {
        UUID ch = UUID.randomUUID();
        String yaml = "version: 1.0\ndeadlines:\n  response-timeout: PT5M\n  escalation: PT1H\n";
        store.put(def(ch, "gate", yaml));

        ProtocolLifecycleContext ctx = new ProtocolLifecycleContext(
                ch, "test-channel", "gate", List.of(), List.of(), false);

        handler.onInit(ctx);

        assertThat(scheduler.activeCount()).isEqualTo(2);
    }

    @Test
    void onInit_noDeadlines_registersNothing() {
        UUID ch = UUID.randomUUID();
        store.put(def(ch, "gate", "version: 1.0\n"));

        ProtocolLifecycleContext ctx = new ProtocolLifecycleContext(
                ch, "test-channel", "gate", List.of(), List.of(), false);

        handler.onInit(ctx);

        assertThat(scheduler.activeCount()).isZero();
    }

    @Test
    void onInit_missingDefinition_logsWarning() {
        UUID ch = UUID.randomUUID();
        ProtocolLifecycleContext ctx = new ProtocolLifecycleContext(
                ch, "test-channel", "missing", List.of(), List.of(), false);

        handler.onInit(ctx);

        assertThat(scheduler.activeCount()).isZero();
    }

    @Test
    void onDestroy_clearsDeadlines() {
        UUID ch = UUID.randomUUID();
        scheduler.register(new DeadlineKey(ch, "gate", "timeout"),
                Instant.now().plus(Duration.ofHours(1)));

        handler.onDestroy(ch, "gate");

        assertThat(scheduler.activeCount()).isZero();
    }

    @Test
    void parseDeadlines_extractsFromYaml() {
        String yaml = "version: 1.0\ndeadlines:\n  timeout: PT5M\n  escalation: 1h\n";
        Map<String, String> result = handler.parseDeadlines(yaml);
        assertThat(result).containsEntry("timeout", "PT5M");
        assertThat(result).containsEntry("escalation", "1h");
    }

    @Test
    void parseDeadlines_emptyWhenNoDeadlinesSection() {
        assertThat(handler.parseDeadlines("version: 1.0\nsteps:\n")).isEmpty();
    }

    @Test
    void parseDuration_handlesISOFormat() {
        assertThat(PlaybookProtocolLifecycleHandler.parseDuration("PT5M"))
                .isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void parseDuration_handlesShorthand() {
        assertThat(PlaybookProtocolLifecycleHandler.parseDuration("30s"))
                .isEqualTo(Duration.ofSeconds(30));
        assertThat(PlaybookProtocolLifecycleHandler.parseDuration("5m"))
                .isEqualTo(Duration.ofMinutes(5));
        assertThat(PlaybookProtocolLifecycleHandler.parseDuration("2h"))
                .isEqualTo(Duration.ofHours(2));
    }

    @Test
    void parseDuration_returnsNull_forInvalid() {
        assertThat(PlaybookProtocolLifecycleHandler.parseDuration("invalid")).isNull();
    }
}
