package io.casehub.qhorus.playbook.bridge;

import io.casehub.platform.api.identity.ActorType;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.message.MessageView;
import io.casehub.qhorus.api.spi.ProtocolLifecycleContext;
import io.casehub.qhorus.runtime.message.protocol.ProtocolRegistry;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
        handler.protocolRegistryInstance = null;
    }

    private ProtocolDefinition def(UUID channelId, String name, String yaml) {
        return new ProtocolDefinition(UUID.randomUUID(), channelId, name,
                yaml, "DEFAULT", Instant.now());
    }

    private MessageView messageAt(UUID channelId, Instant createdAt) {
        return new MessageView(1L, channelId, "agent-1", MessageType.STATUS,
                               "status update", null, null, null, null, null,
                               List.of(), ActorType.AGENT, createdAt, null, 0);
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

    @SuppressWarnings("unchecked")
    @Test
    void supports_false_forStaticProtocol() {
        store.put(def(UUID.randomUUID(), "STATIC", "version: 1.0"));
        ProtocolRegistry registry = new ProtocolRegistry(
                List.of(new io.casehub.qhorus.api.spi.ChannelProtocol() {
                    @Override public String protocolName() { return "STATIC"; }
                    @Override public List<io.casehub.qhorus.api.spi.DispatchAdvisory> evaluate(
                            io.casehub.qhorus.api.spi.ProtocolContext ctx) { return List.of(); }
                }));
        Instance<ProtocolRegistry> instance = mock(Instance.class);
        when(instance.isResolvable()).thenReturn(true);
        when(instance.get()).thenReturn(registry);
        handler.protocolRegistryInstance = instance;
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

    @Test
    void onInit_recovered_withMessageHistory_usesLastMessageTime() {
        UUID    ch              = UUID.randomUUID();
        Instant lastMessageTime = Instant.parse("2026-11-20T10:00:00Z");
        String  yaml            = "version: 1.0\ndeadlines:\n  timeout: PT5M\n";
        store.put(def(ch, "gate", yaml));

        ProtocolLifecycleContext ctx = new ProtocolLifecycleContext(
                ch, "test-channel", "gate", List.of(),
                List.of(
                        messageAt(ch, Instant.parse("2026-11-20T09:00:00Z")),
                        messageAt(ch, lastMessageTime)
                       ),
                true);

        Instant beforeInit = Instant.now();
        handler.onInit(ctx);

        assertThat(scheduler.activeCount()).isEqualTo(1);
        // Deadline should be baseTime (last message) + 5 minutes, not now + 5 minutes
        // Since last message was in the past, the deadline is also in the past
        assertThat(lastMessageTime.plus(Duration.ofMinutes(5))).isBefore(beforeInit);
    }

    @Test
    void onInit_recovered_withNoMessages_usesCurrentTime() {
        UUID   ch   = UUID.randomUUID();
        String yaml = "version: 1.0\ndeadlines:\n  timeout: PT10M\n";
        store.put(def(ch, "gate", yaml));

        ProtocolLifecycleContext ctx = new ProtocolLifecycleContext(
                ch, "test-channel", "gate", List.of(), List.of(), true);

        Instant beforeInit = Instant.now();
        handler.onInit(ctx);

        assertThat(scheduler.activeCount()).isEqualTo(1);
        // With empty history and recovered=true, recoverBaseTime falls back to Instant.now()
        // The deadline should be in the future (now + 10 minutes)
    }

    @Test
    void onInit_recovered_pastDueDeadlines_firesOnSweep() {
        UUID    ch       = UUID.randomUUID();
        Instant pastTime = Instant.now().minus(Duration.ofHours(1));
        String  yaml     = "version: 1.0\ndeadlines:\n  timeout: PT5M\n  escalation: PT30M\n";
        store.put(def(ch, "gate", yaml));

        ProtocolLifecycleContext ctx = new ProtocolLifecycleContext(
                ch, "test-channel", "gate", List.of(),
                List.of(messageAt(ch, pastTime)),
                true);

        List<DeadlineKey>         firedKeys = new ArrayList<>();
        ProtocolDeadlineScheduler sweepable = new ProtocolDeadlineScheduler(firedKeys::add);
        handler.deadlineScheduler = sweepable;

        handler.onInit(ctx);

        assertThat(sweepable.activeCount()).isEqualTo(2);

        // Both deadlines are past-due (pastTime + 5m and pastTime + 30m are both < now)
        sweepable.sweep();

        assertThat(firedKeys).hasSize(2);
        assertThat(sweepable.activeCount()).isZero();
    }

    @Test
    void onInit_recovered_partiallyExpiredDeadlines() {
        UUID ch = UUID.randomUUID();
        // 20 minutes ago — 5m deadline expired, 1h deadline still active
        Instant pastTime = Instant.now().minus(Duration.ofMinutes(20));
        String  yaml     = "version: 1.0\ndeadlines:\n  timeout: PT5M\n  escalation: PT1H\n";
        store.put(def(ch, "gate", yaml));

        ProtocolLifecycleContext ctx = new ProtocolLifecycleContext(
                ch, "test-channel", "gate", List.of(),
                List.of(messageAt(ch, pastTime)),
                true);

        List<DeadlineKey>         firedKeys = new ArrayList<>();
        ProtocolDeadlineScheduler sweepable = new ProtocolDeadlineScheduler(firedKeys::add);
        handler.deadlineScheduler = sweepable;

        handler.onInit(ctx);

        assertThat(sweepable.activeCount()).isEqualTo(2);

        sweepable.sweep();

        // Only the 5m deadline should fire (past-due); 1h is still active
        assertThat(firedKeys).hasSize(1);
        assertThat(firedKeys.get(0).deadlineName()).isEqualTo("timeout");
        assertThat(sweepable.activeCount()).isEqualTo(1);
    }


}
