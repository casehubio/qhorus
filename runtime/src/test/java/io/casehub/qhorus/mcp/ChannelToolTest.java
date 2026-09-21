package io.casehub.qhorus.mcp;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.casehub.platform.api.identity.ActorTypeResolver;
import io.casehub.qhorus.api.message.MessageDispatch;
import io.casehub.qhorus.api.message.MessageType;
import jakarta.persistence.EntityManager;
import io.casehub.qhorus.runtime.channel.ChannelEntity;
import io.casehub.qhorus.testing.QhorusTestHelper;
import io.casehub.qhorus.api.channel.ChannelDetail;
import io.casehub.qhorus.runtime.message.MessageService;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class ChannelToolTest {

    @Inject
    EntityManager em;

    @Inject QhorusTestHelper helper;

    @Inject
    MessageService messageService;

    private static String unique(String prefix) {
        return prefix + "-" + System.nanoTime();
    }

    @Test
    @TestTransaction
    void createChannelDefaultsToAppendSemantic() {
        String name = unique("auth-review");
        ChannelDetail ch = helper.createChannel(name, "Auth code review thread", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        assertEquals(name, ch.name());
        assertEquals("Auth code review thread", ch.description());
        assertEquals("APPEND", ch.semantic());
        assertNotNull(ch.channelId());
    }

    @Test
    @TestTransaction
    void createChannelWithExplicitSemantic() {
        String name = unique("findings");
        ChannelDetail ch = helper.createChannel(name, "Research findings", "COLLECT", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        assertEquals("COLLECT", ch.semantic());
    }

    @Test
    @TestTransaction
    void createChannelWithBarrierContributors() {
        String name = unique("sync-point");
        ChannelDetail ch = helper.createChannel(name, "All must contribute", "BARRIER", "alice,bob,carol", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        assertEquals("BARRIER", ch.semantic());
        assertEquals("alice,bob,carol", ch.barrierContributors());
    }

    @Test
    void createDuplicateChannelNameThrowsException() {
        String name = "dup-tool-" + System.nanoTime();
        QuarkusTransaction.requiringNew().run(() -> helper.createChannel(name, "First", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null));
        try {
            assertThrows(Exception.class,
                    () -> QuarkusTransaction.requiringNew().run(() -> helper.createChannel(name, "Second", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null)));
        } finally {
            QuarkusTransaction.requiringNew().run(() -> em.createQuery("DELETE FROM Channel e WHERE e.name = :p1").setParameter("p1", name).executeUpdate());
        }
    }

    @Test
    @TestTransaction
    void createChannelWithInvalidSemanticThrowsDescriptiveError() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> helper.createChannel(unique("bad-sem-ch"), "Test", "RUBBISH", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null));

        assertTrue(ex.getMessage().contains("RUBBISH"),
                "error message should mention the invalid value");
        assertTrue(ex.getMessage().contains("APPEND"),
                "error message should list valid values");
    }

    @Test
    @TestTransaction
    void listChannelsIncludesCreatedChannels() {
        String n1 = unique("list-ch-1");
        String n2 = unique("list-ch-2");
        helper.createChannel(n1, "First", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        helper.createChannel(n2, "Second", "LAST_WRITE", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        List<ChannelDetail> channels = helper.listChannels();

        assertTrue(channels.stream().anyMatch(c -> n1.equals(c.name())));
        assertTrue(channels.stream().anyMatch(c -> n2.equals(c.name())));
    }

    @Test
    void listChannelsIncludesMessageCount() {
        String name = unique("counted-ch");
        ChannelDetail ch = helper.createChannel(name, "Count test", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        QuarkusTransaction.requiringNew().run(() -> {
            messageService.dispatch(MessageDispatch.builder()
                    .channelId(ch.channelId())
                    .sender("alice")
                    .type(MessageType.STATUS)
                    .content("msg1")
                    .actorType(ActorTypeResolver.resolve("alice"))
                    .build());
            messageService.dispatch(MessageDispatch.builder()
                    .channelId(ch.channelId())
                    .sender("bob")
                    .type(MessageType.STATUS)
                    .content("msg2")
                    .actorType(ActorTypeResolver.resolve("bob"))
                    .build());
        });

        List<ChannelDetail> channels = helper.listChannels();
        ChannelDetail counted = channels.stream()
                .filter(c -> name.equals(c.name())).findFirst().orElseThrow();

        assertEquals(2, counted.messageCount());
    }

    @Test
    @TestTransaction
    void findChannelMatchesByName() {
        String name = unique("auth-refactor");
        helper.createChannel(name, "Auth refactoring", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        helper.createChannel(unique("unrelated-ch"), "Something else", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        List<ChannelDetail> found = helper.findChannelByKeyword(name);

        assertEquals(1, found.size());
        assertEquals(name, found.get(0).name());
    }

    @Test
    @TestTransaction
    void findChannelMatchesByDescriptionCaseInsensitive() {
        String name = unique("my-channel");
        String desc = "security-review-" + System.nanoTime();
        helper.createChannel(name, desc, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        List<ChannelDetail> found = helper.findChannelByKeyword(desc.substring(0, 15));

        assertTrue(found.stream().anyMatch(c -> name.equals(c.name())));
    }

    @Test
    @TestTransaction
    void findChannelReturnsEmptyWhenNoMatch() {
        helper.createChannel(unique("some-channel"), "Some description", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        List<ChannelDetail> found = helper.findChannelByKeyword("xyzzy-no-match-" + System.nanoTime());

        assertTrue(found.isEmpty());
    }

    @Test
    @TestTransaction
    void pauseChannel_acceptsChannelUuid() {
        String name = unique("uuid-pause-test");
        ChannelDetail created = helper.createChannel(name, "Test", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        String uuid = created.channelId().toString();

        ChannelDetail result = helper.pauseChannel(uuid, null);

        assertEquals(name, result.name());
    }

    @Test
    @TestTransaction
    void resumeChannel_acceptsChannelUuid() {
        String name = unique("uuid-resume-test");
        ChannelDetail created = helper.createChannel(name, "Test", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        String uuid = created.channelId().toString();
        helper.pauseChannel(uuid, null);

        ChannelDetail result = helper.resumeChannel(uuid, null);

        assertEquals(name, result.name());
    }
}
