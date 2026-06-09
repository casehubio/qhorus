package io.casehub.qhorus.store;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.casehub.platform.api.identity.ActorType;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.identity.TenancyConstants;
import io.casehub.qhorus.api.channel.ChannelSemantic;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.runtime.channel.Channel;
import io.casehub.qhorus.api.message.CommitmentState;
import io.casehub.qhorus.runtime.message.Commitment;
import io.casehub.qhorus.runtime.message.Message;
import io.casehub.qhorus.runtime.store.ChannelStore;
import io.casehub.qhorus.runtime.store.CommitmentStore;
import io.casehub.qhorus.runtime.store.MessageStore;
import io.casehub.qhorus.runtime.store.query.ChannelQuery;
import io.casehub.qhorus.runtime.store.query.MessageQuery;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class TenantIsolationTest {

    private static final String TENANT_A = TenancyConstants.DEFAULT_TENANT_ID;
    private static final String TENANT_B = "tenant-b-isolation-test";

    @Inject ChannelStore channelStore;
    @Inject MessageStore messageStore;
    @Inject CommitmentStore commitmentStore;
    @InjectMock CurrentPrincipal currentPrincipal;

    // ─── Channel tests ───────────────────────────────────────────────────────

    @Test
    @Transactional
    void channel_createdInTenantA_notVisibleToTenantB() {
        Mockito.when(currentPrincipal.tenancyId()).thenReturn(TENANT_A);
        Channel ch = new Channel();
        ch.id = UUID.randomUUID();
        ch.name = "isolation-test-" + UUID.randomUUID();
        ch.semantic = ChannelSemantic.APPEND;
        ch.tenancyId = TENANT_A;
        channelStore.put(ch);

        Mockito.when(currentPrincipal.tenancyId()).thenReturn(TENANT_B);
        Optional<Channel> found = channelStore.find(ch.id);
        var list = channelStore.scan(ChannelQuery.builder().build());

        assertThat(found).isEmpty();
        assertThat(list).noneMatch(c -> c.id.equals(ch.id));
    }

    @Test
    @Transactional
    void channel_findByName_scopedToTenant() {
        String name = "shared-name-" + UUID.randomUUID();

        Mockito.when(currentPrincipal.tenancyId()).thenReturn(TENANT_A);
        Channel chA = new Channel();
        chA.id = UUID.randomUUID();
        chA.name = name;
        chA.semantic = ChannelSemantic.APPEND;
        chA.tenancyId = TENANT_A;
        channelStore.put(chA);

        Mockito.when(currentPrincipal.tenancyId()).thenReturn(TENANT_B);
        Channel chB = new Channel();
        chB.id = UUID.randomUUID();
        chB.name = name;
        chB.semantic = ChannelSemantic.APPEND;
        chB.tenancyId = TENANT_B;
        channelStore.put(chB);

        Mockito.when(currentPrincipal.tenancyId()).thenReturn(TENANT_A);
        Optional<Channel> foundA = channelStore.findByName(name);
        assertThat(foundA).isPresent().get().extracting(c -> c.id).isEqualTo(chA.id);

        Mockito.when(currentPrincipal.tenancyId()).thenReturn(TENANT_B);
        Optional<Channel> foundB = channelStore.findByName(name);
        assertThat(foundB).isPresent().get().extracting(c -> c.id).isEqualTo(chB.id);
    }

    // ─── Message tests ───────────────────────────────────────────────────────

    @Test
    @Transactional
    void message_createdInTenantA_notVisibleToTenantB() {
        Mockito.when(currentPrincipal.tenancyId()).thenReturn(TENANT_A);
        Channel ch = new Channel();
        ch.id = UUID.randomUUID();
        ch.name = "msg-isolation-" + UUID.randomUUID();
        ch.semantic = ChannelSemantic.APPEND;
        ch.tenancyId = TENANT_A;
        channelStore.put(ch);

        Message m = new Message();
        m.channelId = ch.id;
        m.sender = "agent-a";
        m.messageType = MessageType.QUERY;
        m.actorType = ActorType.AGENT;
        m.tenancyId = TENANT_A;
        messageStore.put(m);

        Mockito.when(currentPrincipal.tenancyId()).thenReturn(TENANT_B);
        var list = messageStore.scan(
                MessageQuery.builder().channelId(ch.id).build());
        assertThat(list).isEmpty();
        int count = messageStore.countByChannel(ch.id);
        assertThat(count).isZero();
    }

    // ─── Commitment tests ─────────────────────────────────────────────────────

    @Test
    @Transactional
    void commitment_createdInTenantA_notVisibleToTenantB() {
        Mockito.when(currentPrincipal.tenancyId()).thenReturn(TENANT_A);
        UUID channelId = UUID.randomUUID();
        Commitment c = new Commitment();
        c.id = UUID.randomUUID();
        c.correlationId = "corr-" + UUID.randomUUID();
        c.channelId = channelId;
        c.obligor = "agent-a";
        c.requester = "agent-req";
        c.state = CommitmentState.OPEN;
        c.messageType = MessageType.COMMAND;
        c.tenancyId = TENANT_A;
        commitmentStore.save(c);

        Mockito.when(currentPrincipal.tenancyId()).thenReturn(TENANT_B);
        var open = commitmentStore.findAllOpen();
        assertThat(open).noneMatch(x -> x.id.equals(c.id));
    }
}
