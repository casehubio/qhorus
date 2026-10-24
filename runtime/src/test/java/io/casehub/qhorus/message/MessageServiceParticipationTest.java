package io.casehub.qhorus.message;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.casehub.platform.api.identity.ActorType;
import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.channel.ChannelCreateRequest;
import io.casehub.qhorus.api.channel.ParticipationPolicyMode;
import io.casehub.qhorus.api.message.MessageDispatch;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.store.ChannelStore;
import io.casehub.qhorus.runtime.channel.ChannelService;
import io.casehub.qhorus.runtime.instance.InstanceService;
import io.casehub.qhorus.runtime.message.MessageService;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;

import java.util.List;

@QuarkusTest
class MessageServiceParticipationTest {

    @Inject ChannelService channelService;
    @Inject MessageService messageService;
    @Inject InstanceService instanceService;
    @Inject ChannelStore channelStore;

    private Channel createChannelWithPolicy(String name, ParticipationPolicyMode policy) {
        Channel ch = channelService.create(ChannelCreateRequest.builder(name).build());
        if (policy != null) {
            Channel updated = ch.toBuilder().participationPolicy(policy).build();
            return channelStore.put(updated);
        }
        return ch;
    }

    @Test
    @TestTransaction
    void open_allowsUnregisteredSender() {
        Channel ch = createChannelWithPolicy("open-test", ParticipationPolicyMode.OPEN);

        assertDoesNotThrow(() -> messageService.dispatch(MessageDispatch.builder()
                .channelId(ch.id())
                .sender("unregistered-agent")
                .type(MessageType.STATUS)
                .content("Hello")
                .actorType(ActorType.AGENT)
                .build()));

        assertFalse(instanceService.findByInstanceId("unregistered-agent").isPresent());
    }

    @Test
    @TestTransaction
    void enforce_rejectsUnregisteredSender() {
        Channel ch = createChannelWithPolicy("enforce-test", ParticipationPolicyMode.ENFORCE);

        var ex = assertThrows(IllegalStateException.class, () ->
                messageService.dispatch(MessageDispatch.builder()
                        .channelId(ch.id())
                        .sender("unknown-agent")
                        .type(MessageType.STATUS)
                        .content("Hello")
                        .actorType(ActorType.AGENT)
                        .build()));
        assertTrue(ex.getMessage().contains("not a registered mesh instance"));
    }

    @Test
    @TestTransaction
    void enforce_allowsRegisteredSender() {
        Channel ch = createChannelWithPolicy("enforce-reg-test", ParticipationPolicyMode.ENFORCE);
        instanceService.register("known-agent", "Test agent", List.of());

        assertDoesNotThrow(() -> messageService.dispatch(MessageDispatch.builder()
                .channelId(ch.id())
                .sender("known-agent")
                .type(MessageType.STATUS)
                .content("Hello from known")
                .actorType(ActorType.AGENT)
                .build()));
    }

    @Test
    @TestTransaction
    void autoRegister_registersUnknownSender() {
        Channel ch = createChannelWithPolicy("autoreg-test", ParticipationPolicyMode.AUTO_REGISTER);

        assertFalse(instanceService.findByInstanceId("new-agent").isPresent());

        assertDoesNotThrow(() -> messageService.dispatch(MessageDispatch.builder()
                .channelId(ch.id())
                .sender("new-agent")
                .type(MessageType.STATUS)
                .content("Hello")
                .actorType(ActorType.AGENT)
                .build()));

        assertTrue(instanceService.findByInstanceId("new-agent").isPresent());
    }

    @Test
    @TestTransaction
    void autoRegister_skipsAlreadyRegistered() {
        Channel ch = createChannelWithPolicy("autoreg-existing-test", ParticipationPolicyMode.AUTO_REGISTER);
        instanceService.register("existing-agent", "Already registered", List.of("some-cap"));

        assertDoesNotThrow(() -> messageService.dispatch(MessageDispatch.builder()
                .channelId(ch.id())
                .sender("existing-agent")
                .type(MessageType.STATUS)
                .content("Hello")
                .actorType(ActorType.AGENT)
                .build()));

        assertEquals(List.of("some-cap"),
                instanceService.findCapabilityTagsForInstance("existing-agent"));
    }

    @Test
    @TestTransaction
    void systemSender_bypassesEnforce() {
        Channel ch = createChannelWithPolicy("system-test", ParticipationPolicyMode.ENFORCE);

        assertDoesNotThrow(() -> messageService.dispatch(MessageDispatch.builder()
                .channelId(ch.id())
                .sender("system:watchdog")
                .type(MessageType.STATUS)
                .content("System message")
                .actorType(ActorType.SYSTEM)
                .build()));

        assertFalse(instanceService.findByInstanceId("system:watchdog").isPresent());
    }

    @Test
    @TestTransaction
    void eventType_bypassesEnforce() {
        Channel ch = createChannelWithPolicy("event-test", ParticipationPolicyMode.ENFORCE);

        assertDoesNotThrow(() -> messageService.dispatch(MessageDispatch.builder()
                .channelId(ch.id())
                .sender("unregistered-telemetry")
                .type(MessageType.EVENT)
                .actorType(ActorType.AGENT)
                .build()));
    }

    @Test
    @TestTransaction
    void nullChannelOverride_inheritsGlobalDefault() {
        Channel ch = createChannelWithPolicy("inherit-test", null);

        assertDoesNotThrow(() -> messageService.dispatch(MessageDispatch.builder()
                .channelId(ch.id())
                .sender("any-sender")
                .type(MessageType.STATUS)
                .content("Should work with OPEN global default")
                .actorType(ActorType.AGENT)
                .build()));
    }
}
