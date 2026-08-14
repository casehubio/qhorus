package io.casehub.qhorus.message;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.casehub.platform.api.identity.ActorType;
import io.casehub.platform.api.identity.ActorTypeResolver;
import io.casehub.qhorus.api.message.Commitment;
import io.casehub.qhorus.api.message.CommitmentState;
import io.casehub.qhorus.api.message.DispatchResult;
import io.casehub.qhorus.api.message.MessageDispatch;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.channel.ChannelCreateRequest;
import io.casehub.qhorus.api.store.CommitmentStore;
import io.casehub.qhorus.runtime.channel.ChannelService;
import io.casehub.qhorus.runtime.message.MessageService;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class ProposeDispatchTest {

    @Inject ChannelService channelService;
    @Inject MessageService messageService;
    @Inject CommitmentStore commitmentStore;

    @Test
    @TestTransaction
    void propose_opens_commitment() {
        Channel ch = channelService.create(ChannelCreateRequest.builder("propose-test-1").build());

        DispatchResult result = messageService.dispatch(MessageDispatch.builder()
                .channelId(ch.id())
                .sender("proposer")
                .type(MessageType.PROPOSE)
                .content("I will do X if you agree")
                .correlationId("prop-corr-1")
                .actorType(ActorTypeResolver.resolve("proposer"))
                .build());

        assertNotNull(result.messageId());
        Optional<Commitment> commitment = commitmentStore.findByCorrelationId("prop-corr-1");
        assertTrue(commitment.isPresent(), "PROPOSE should open a commitment");
        assertEquals(CommitmentState.OPEN, commitment.get().state());
        assertEquals(MessageType.PROPOSE, commitment.get().messageType());
        assertEquals("proposer", commitment.get().requester());
    }

    @Test
    @TestTransaction
    void response_on_propose_does_not_fulfill() {
        Channel ch = channelService.create(ChannelCreateRequest.builder("propose-test-2").build());

        DispatchResult proposeResult = messageService.dispatch(MessageDispatch.builder()
                .channelId(ch.id())
                .sender("proposer")
                .type(MessageType.PROPOSE)
                .content("I will do X if you agree")
                .correlationId("prop-corr-2")
                .actorType(ActorTypeResolver.resolve("proposer"))
                .build());

        messageService.dispatch(MessageDispatch.builder()
                .channelId(ch.id())
                .sender("responder")
                .type(MessageType.RESPONSE)
                .content("counter-proposal: how about Y instead")
                .correlationId("prop-corr-2")
                .inReplyTo(proposeResult.messageId())
                .actorType(ActorTypeResolver.resolve("responder"))
                .build());

        Optional<Commitment> commitment = commitmentStore.findByCorrelationId("prop-corr-2");
        assertTrue(commitment.isPresent());
        assertEquals(CommitmentState.OPEN, commitment.get().state(),
                "RESPONSE should NOT fulfill a PROPOSE commitment");
    }

    @Test
    @TestTransaction
    void done_on_propose_fulfills() {
        Channel ch = channelService.create(ChannelCreateRequest.builder("propose-test-3").build());

        DispatchResult proposeResult = messageService.dispatch(MessageDispatch.builder()
                .channelId(ch.id())
                .sender("proposer")
                .type(MessageType.PROPOSE)
                .content("I will do X if you agree")
                .correlationId("prop-corr-3")
                .actorType(ActorTypeResolver.resolve("proposer"))
                .build());

        messageService.dispatch(MessageDispatch.builder()
                .channelId(ch.id())
                .sender("acceptor")
                .type(MessageType.DONE)
                .content("accepted")
                .correlationId("prop-corr-3")
                .inReplyTo(proposeResult.messageId())
                .actorType(ActorTypeResolver.resolve("acceptor"))
                .build());

        Optional<Commitment> commitment = commitmentStore.findByCorrelationId("prop-corr-3");
        assertTrue(commitment.isPresent());
        assertEquals(CommitmentState.FULFILLED, commitment.get().state(),
                "DONE should fulfill a PROPOSE commitment (acceptance)");
    }

    @Test
    @TestTransaction
    void response_on_command_still_fulfills() {
        Channel ch = channelService.create(ChannelCreateRequest.builder("propose-test-4").build());

        DispatchResult cmdResult = messageService.dispatch(MessageDispatch.builder()
                .channelId(ch.id())
                .sender("commander")
                .type(MessageType.COMMAND)
                .content("do this")
                .correlationId("cmd-corr-1")
                .actorType(ActorTypeResolver.resolve("commander"))
                .build());

        messageService.dispatch(MessageDispatch.builder()
                .channelId(ch.id())
                .sender("executor")
                .type(MessageType.RESPONSE)
                .content("done")
                .correlationId("cmd-corr-1")
                .inReplyTo(cmdResult.messageId())
                .actorType(ActorTypeResolver.resolve("executor"))
                .build());

        Optional<Commitment> commitment = commitmentStore.findByCorrelationId("cmd-corr-1");
        assertTrue(commitment.isPresent());
        assertEquals(CommitmentState.FULFILLED, commitment.get().state(),
                "RESPONSE should still fulfill COMMAND commitments");
    }

    @Test
    @TestTransaction
    void decline_on_propose_declines() {
        Channel ch = channelService.create(ChannelCreateRequest.builder("propose-test-5").build());

        DispatchResult proposeResult = messageService.dispatch(MessageDispatch.builder()
                .channelId(ch.id())
                .sender("proposer")
                .type(MessageType.PROPOSE)
                .content("I will do X if you agree")
                .correlationId("prop-corr-5")
                .actorType(ActorTypeResolver.resolve("proposer"))
                .build());

        messageService.dispatch(MessageDispatch.builder()
                .channelId(ch.id())
                .sender("rejector")
                .type(MessageType.DECLINE)
                .content("no thanks")
                .correlationId("prop-corr-5")
                .inReplyTo(proposeResult.messageId())
                .actorType(ActorTypeResolver.resolve("rejector"))
                .build());

        Optional<Commitment> commitment = commitmentStore.findByCorrelationId("prop-corr-5");
        assertTrue(commitment.isPresent());
        assertEquals(CommitmentState.DECLINED, commitment.get().state(),
                "DECLINE should decline a PROPOSE commitment");
    }
}
