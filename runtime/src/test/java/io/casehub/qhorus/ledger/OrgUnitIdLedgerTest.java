package io.casehub.qhorus.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.casehub.platform.api.identity.ActorType;
import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.channel.ChannelCreateRequest;
import io.casehub.qhorus.api.channel.Space;
import io.casehub.qhorus.api.channel.SpaceCreateRequest;
import io.casehub.qhorus.api.message.DispatchResult;
import io.casehub.qhorus.api.message.MessageDispatch;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.runtime.channel.ChannelService;
import io.casehub.qhorus.runtime.channel.SpaceService;
import io.casehub.qhorus.runtime.ledger.MessageLedgerEntry;
import io.casehub.qhorus.runtime.ledger.MessageLedgerEntryRepository;
import io.casehub.qhorus.runtime.message.MessageService;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class OrgUnitIdLedgerTest {

    @Inject ChannelService channelService;
    @Inject MessageService messageService;
    @Inject SpaceService spaceService;
    @Inject MessageLedgerEntryRepository ledgerRepo;

    @Test
    void ledgerEntryCarriesOrgUnitIdFromChannelSpace() {
        UUID[] spaceId = new UUID[1];
        UUID[] channelId = new UUID[1];
        Long[] messageId = new Long[1];

        QuarkusTransaction.requiringNew().run(() -> {
            Space space = spaceService.create(new SpaceCreateRequest("team-alpha-" + System.nanoTime(), "Team Alpha", null));
            spaceId[0] = space.id();
            Channel ch = channelService.create(ChannelCreateRequest.builder("org-ledger-" + System.nanoTime())
                    .spaceId(space.id()).build());
            channelId[0] = ch.id();
            DispatchResult result = messageService.dispatch(MessageDispatch.builder()
                    .channelId(ch.id())
                    .sender("agent-x")
                    .type(MessageType.STATUS)
                    .content("status update")
                    .actorType(ActorType.AGENT)
                    .build());
            messageId[0] = result.messageId();
        });

        MessageLedgerEntry entry = ledgerRepo.findByMessageId(messageId[0]).orElseThrow();
        assertThat(entry.orgUnitId).isEqualTo(spaceId[0]);
    }

    @Test
    void ledgerEntryWithoutSpaceHasNullOrgUnitId() {
        Long[] messageId = new Long[1];

        QuarkusTransaction.requiringNew().run(() -> {
            Channel ch = channelService.create(ChannelCreateRequest.builder("no-space-ledger-" + System.nanoTime()).build());
            DispatchResult result = messageService.dispatch(MessageDispatch.builder()
                    .channelId(ch.id())
                    .sender("agent-y")
                    .type(MessageType.STATUS)
                    .content("no space")
                    .actorType(ActorType.AGENT)
                    .build());
            messageId[0] = result.messageId();
        });

        MessageLedgerEntry entry = ledgerRepo.findByMessageId(messageId[0]).orElseThrow();
        assertThat(entry.orgUnitId).isNull();
    }

    @Test
    void listEntriesFiltersByOrgUnitId() {
        UUID[] spaceId = new UUID[1];
        UUID[] channelId = new UUID[1];

        QuarkusTransaction.requiringNew().run(() -> {
            Space space = spaceService.create(new SpaceCreateRequest("filter-unit-" + System.nanoTime(), null, null));
            spaceId[0] = space.id();
            Channel ch = channelService.create(ChannelCreateRequest.builder("org-filter-" + System.nanoTime())
                    .spaceId(space.id()).build());
            channelId[0] = ch.id();
            messageService.dispatch(MessageDispatch.builder()
                    .channelId(ch.id())
                    .sender("agent-z")
                    .type(MessageType.STATUS)
                    .content("in unit")
                    .actorType(ActorType.AGENT)
                    .build());
        });

        List<MessageLedgerEntry> withFilter = ledgerRepo.listEntries(
                channelId[0], null, null, null, null, null, spaceId[0], false, 100, null);
        assertThat(withFilter).isNotEmpty();
        assertThat(withFilter).allMatch(e -> spaceId[0].equals(e.orgUnitId));

        UUID randomOrgUnit = UUID.randomUUID();
        List<MessageLedgerEntry> noMatch = ledgerRepo.listEntries(
                channelId[0], null, null, null, null, null, randomOrgUnit, false, 100, null);
        assertThat(noMatch).isEmpty();
    }
}
