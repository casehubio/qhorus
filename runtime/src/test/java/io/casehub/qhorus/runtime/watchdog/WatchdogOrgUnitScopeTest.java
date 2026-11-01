package io.casehub.qhorus.runtime.watchdog;

import io.casehub.platform.api.identity.ActorType;
import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.channel.ChannelSemantic;
import io.casehub.qhorus.api.channel.Space;
import io.casehub.qhorus.api.message.Message;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.store.ChannelStore;
import io.casehub.qhorus.api.store.MessageStore;
import io.casehub.qhorus.api.store.SpaceStore;
import io.casehub.qhorus.api.store.WatchdogStore;
import io.casehub.qhorus.api.store.query.MessageQuery;
import io.casehub.qhorus.api.watchdog.Watchdog;
import io.casehub.qhorus.api.watchdog.WatchdogConditionType;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static io.casehub.platform.api.identity.TenancyConstants.DEFAULT_TENANT_ID;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(io.casehub.qhorus.api.WatchdogEnabledProfile.class)
class WatchdogOrgUnitScopeTest {

    @Inject WatchdogEvaluationService watchdogService;
    @Inject ChannelStore channelStore;
    @Inject WatchdogStore watchdogStore;
    @Inject MessageStore messageStore;
    @Inject SpaceStore spaceStore;

    @Test
    @TestTransaction
    void orgUnitScope_filtersChannelsBySpace() {
        Space space = spaceStore.put(new Space(UUID.randomUUID(), "team-alpha-" + UUID.randomUUID(),
                "Team Alpha", null, DEFAULT_TENANT_ID, Instant.now(), List.of(), List.of()));

        Channel notifCh = channelStore.put(Channel.builder("notif-org-" + UUID.randomUUID())
                .semantic(ChannelSemantic.APPEND).build());
        Channel inScope = channelStore.put(Channel.builder("in-scope-" + UUID.randomUUID())
                .semantic(ChannelSemantic.APPEND).spaceId(space.id()).build());
        Channel outOfScope = channelStore.put(Channel.builder("out-of-scope-" + UUID.randomUUID())
                .semantic(ChannelSemantic.APPEND).build());

        for (int i = 0; i < 5; i++) {
            messageStore.put(Message.builder().channelId(inScope.id()).sender("agent-" + i)
                    .messageType(MessageType.STATUS).actorType(ActorType.AGENT).content("msg " + i).build());
        }
        for (int i = 0; i < 10; i++) {
            messageStore.put(Message.builder().channelId(outOfScope.id()).sender("agent-" + i)
                    .messageType(MessageType.STATUS).actorType(ActorType.AGENT).content("msg " + i).build());
        }

        watchdogStore.put(Watchdog.builder(WatchdogConditionType.QUEUE_DEPTH, "*")
                .thresholdCount(3)
                .orgUnitScope(space.id())
                .notificationChannel(notifCh.name())
                .createdBy("test")
                .build());

        watchdogService.evaluateAll();

        List<Message> alerts = messageStore.scan(MessageQuery.forChannel(notifCh.id()));
        assertFalse(alerts.isEmpty(), "QUEUE_DEPTH should fire for in-scope channel");
        assertTrue(alerts.get(0).content().contains(inScope.name()),
                "Alert should reference the in-scope channel, not the out-of-scope one");
    }

    @Test
    @TestTransaction
    void noOrgUnitScope_matchesAllChannels() {
        Channel notifCh = channelStore.put(Channel.builder("notif-no-org-" + UUID.randomUUID())
                .semantic(ChannelSemantic.APPEND).build());
        Channel ch = channelStore.put(Channel.builder("any-ch-" + UUID.randomUUID())
                .semantic(ChannelSemantic.APPEND).build());

        for (int i = 0; i < 5; i++) {
            messageStore.put(Message.builder().channelId(ch.id()).sender("agent-" + i)
                    .messageType(MessageType.STATUS).actorType(ActorType.AGENT).content("msg " + i).build());
        }

        watchdogStore.put(Watchdog.builder(WatchdogConditionType.QUEUE_DEPTH, "*")
                .thresholdCount(3)
                .notificationChannel(notifCh.name())
                .createdBy("test")
                .build());

        watchdogService.evaluateAll();

        List<Message> alerts = messageStore.scan(MessageQuery.forChannel(notifCh.id()));
        assertFalse(alerts.isEmpty(), "QUEUE_DEPTH should fire without orgUnitScope (matches all)");
    }

    @Test
    @TestTransaction
    void orgUnitScope_noAlert_whenChannelOutsideSpace() {
        Space space = spaceStore.put(new Space(UUID.randomUUID(), "team-beta-" + UUID.randomUUID(),
                "Team Beta", null, DEFAULT_TENANT_ID, Instant.now(), List.of(), List.of()));

        Channel notifCh = channelStore.put(Channel.builder("notif-beta-" + UUID.randomUUID())
                .semantic(ChannelSemantic.APPEND).build());
        Channel outsideCh = channelStore.put(Channel.builder("outside-" + UUID.randomUUID())
                .semantic(ChannelSemantic.APPEND).build());

        for (int i = 0; i < 10; i++) {
            messageStore.put(Message.builder().channelId(outsideCh.id()).sender("agent-" + i)
                    .messageType(MessageType.STATUS).actorType(ActorType.AGENT).content("msg " + i).build());
        }

        watchdogStore.put(Watchdog.builder(WatchdogConditionType.QUEUE_DEPTH, "*")
                .thresholdCount(3)
                .orgUnitScope(space.id())
                .notificationChannel(notifCh.name())
                .createdBy("test")
                .build());

        watchdogService.evaluateAll();

        List<Message> alerts = messageStore.scan(MessageQuery.forChannel(notifCh.id()));
        assertTrue(alerts.isEmpty(),
                "QUEUE_DEPTH should NOT fire when channels are outside the scoped Space");
    }
}
