package io.casehub.qhorus.channel;

import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.channel.ChannelCreateRequest;
import io.casehub.qhorus.api.channel.ChannelSemantic;
import io.casehub.qhorus.api.channel.ChannelVisibility;
import io.casehub.qhorus.api.channel.FindOrCreateResult;
import io.casehub.qhorus.api.channel.MemberRole;
import io.casehub.qhorus.api.channel.ParticipationPolicyMode;
import io.casehub.qhorus.api.channel.VisibilityManager;
import io.casehub.qhorus.runtime.channel.ChannelMembershipService;
import io.casehub.qhorus.runtime.channel.ChannelService;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@QuarkusTest
class ChannelVisibilityTest {

    @Inject ChannelService channelService;
    @Inject VisibilityManager visibilityManager;
    @Inject ChannelMembershipService membershipService;

    @Test
    void createChannel_withVisibility_persistsField() {
        String name = "vis-test-" + System.nanoTime();
        QuarkusTransaction.requiringNew().run(() -> {
            Channel ch = channelService.create(ChannelCreateRequest.builder(name)
                    .visibility(ChannelVisibility.PRIVATE)
                    .build());
            assertThat(ch.visibility()).isEqualTo(ChannelVisibility.PRIVATE);
        });

        QuarkusTransaction.requiringNew().run(() -> {
            Channel found = channelService.findByName(name).orElseThrow();
            assertThat(found.visibility()).isEqualTo(ChannelVisibility.PRIVATE);
        });
    }

    @Test
    void createChannel_publicVisibility_defaultsToBroadcastSemantic() {
        String name = "vis-public-" + System.nanoTime();
        QuarkusTransaction.requiringNew().run(() -> {
            Channel ch = channelService.create(ChannelCreateRequest.builder(name)
                    .visibility(ChannelVisibility.PUBLIC)
                    .build());
            assertThat(ch.semantic()).isEqualTo(ChannelSemantic.BROADCAST);
            assertThat(ch.participationPolicy()).isEqualTo(ParticipationPolicyMode.OPEN);
        });
    }

    @Test
    void createChannel_privateVisibility_defaultsToAppendEnforce() {
        String name = "vis-private-" + System.nanoTime();
        QuarkusTransaction.requiringNew().run(() -> {
            Channel ch = channelService.create(ChannelCreateRequest.builder(name)
                    .visibility(ChannelVisibility.PRIVATE)
                    .build());
            assertThat(ch.semantic()).isEqualTo(ChannelSemantic.APPEND);
            assertThat(ch.participationPolicy()).isEqualTo(ParticipationPolicyMode.ENFORCE);
        });
    }

    @Test
    void createChannel_directVisibility_defaultsToAppendEnforce() {
        String name = "dm/vis-direct-a-" + System.nanoTime() + "/vis-direct-b";
        QuarkusTransaction.requiringNew().run(() -> {
            Channel ch = channelService.create(ChannelCreateRequest.builder(name)
                    .visibility(ChannelVisibility.DIRECT)
                    .build());
            assertThat(ch.semantic()).isEqualTo(ChannelSemantic.APPEND);
            assertThat(ch.participationPolicy()).isEqualTo(ParticipationPolicyMode.ENFORCE);
        });
    }

    @Test
    void createChannel_visibilityWithExplicitSemantic_preservesSemantic() {
        String name = "vis-explicit-" + System.nanoTime();
        QuarkusTransaction.requiringNew().run(() -> {
            Channel ch = channelService.create(ChannelCreateRequest.builder(name)
                    .visibility(ChannelVisibility.PUBLIC)
                    .semantic(ChannelSemantic.APPEND)
                    .build());
            assertThat(ch.visibility()).isEqualTo(ChannelVisibility.PUBLIC);
            assertThat(ch.semantic()).isEqualTo(ChannelSemantic.APPEND);
        });
    }

    @Test
    void setVisibility_updatesField() {
        String name = "vis-set-" + System.nanoTime();
        UUID[] id = new UUID[1];
        QuarkusTransaction.requiringNew().run(() -> {
            Channel ch = channelService.create(ChannelCreateRequest.builder(name).build());
            id[0] = ch.id();
        });

        QuarkusTransaction.requiringNew().run(() -> {
            Channel updated = channelService.setVisibility(id[0], ChannelVisibility.PRIVATE);
            assertThat(updated.visibility()).isEqualTo(ChannelVisibility.PRIVATE);
        });

        QuarkusTransaction.requiringNew().run(() -> {
            Channel found = channelService.findByName(name).orElseThrow();
            assertThat(found.visibility()).isEqualTo(ChannelVisibility.PRIVATE);
        });
    }

    @Test
    void setVisibility_toDirect_requiresExactlyTwoMembers() {
        String name = "vis-direct-guard-" + System.nanoTime();
        UUID[] id = new UUID[1];
        QuarkusTransaction.requiringNew().run(() -> {
            Channel ch = channelService.create(ChannelCreateRequest.builder(name).build());
            id[0] = ch.id();
            membershipService.join(ch.id(), "alice", MemberRole.PARTICIPANT, "default");
        });

        assertThatThrownBy(() ->
                QuarkusTransaction.requiringNew().run(() ->
                        channelService.setVisibility(id[0], ChannelVisibility.DIRECT)))
                .hasMessageContaining("exactly 2 members");
    }

    @Test
    void setVisibility_toDirect_withTwoMembers_succeeds() {
        String name = "vis-direct-ok-" + System.nanoTime();
        UUID[] id = new UUID[1];
        QuarkusTransaction.requiringNew().run(() -> {
            Channel ch = channelService.create(ChannelCreateRequest.builder(name).build());
            id[0] = ch.id();
            membershipService.join(ch.id(), "alice", MemberRole.PARTICIPANT, "default");
            membershipService.join(ch.id(), "bob", MemberRole.PARTICIPANT, "default");
        });

        QuarkusTransaction.requiringNew().run(() -> {
            Channel updated = channelService.setVisibility(id[0], ChannelVisibility.DIRECT);
            assertThat(updated.visibility()).isEqualTo(ChannelVisibility.DIRECT);
        });
    }

    @Test
    void findByVisibility_returnsMatchingChannels() {
        String suffix = String.valueOf(System.nanoTime());
        QuarkusTransaction.requiringNew().run(() -> {
            channelService.create(ChannelCreateRequest.builder("vis-find-pub-" + suffix)
                    .visibility(ChannelVisibility.PUBLIC).build());
            channelService.create(ChannelCreateRequest.builder("vis-find-priv-" + suffix)
                    .visibility(ChannelVisibility.PRIVATE).build());
        });

        QuarkusTransaction.requiringNew().run(() -> {
            List<Channel> publicChannels = channelService.findByVisibility(ChannelVisibility.PUBLIC);
            assertThat(publicChannels).anyMatch(ch -> ch.name().equals("vis-find-pub-" + suffix));
            assertThat(publicChannels).noneMatch(ch -> ch.name().equals("vis-find-priv-" + suffix));
        });
    }

    @Test
    void createChannel_withBoundGroupId_persistsField() {
        UUID groupId = UUID.randomUUID();
        String name = "group/vis-bound-" + System.nanoTime();
        QuarkusTransaction.requiringNew().run(() -> {
            Channel ch = channelService.create(ChannelCreateRequest.builder(name)
                    .visibility(ChannelVisibility.PRIVATE)
                    .boundGroupId(groupId)
                    .build());
            assertThat(ch.boundGroupId()).isEqualTo(groupId);
        });

        QuarkusTransaction.requiringNew().run(() -> {
            Channel found = channelService.findByName(name).orElseThrow();
            assertThat(found.boundGroupId()).isEqualTo(groupId);
        });
    }

    @Test
    void getOrCreateDirect_roundTrip() {
        String suffix = String.valueOf(System.nanoTime());
        QuarkusTransaction.requiringNew().run(() -> {
            FindOrCreateResult result = visibilityManager.getOrCreateDirect(
                    "agent-a-" + suffix, "agent-b-" + suffix);
            assertThat(result.wasCreated()).isTrue();
            assertThat(result.channel().name()).startsWith("dm/agent-a-");
            assertThat(result.channel().visibility()).isEqualTo(ChannelVisibility.DIRECT);
        });

        QuarkusTransaction.requiringNew().run(() -> {
            FindOrCreateResult result = visibilityManager.getOrCreateDirect(
                    "agent-b-" + suffix, "agent-a-" + suffix);
            assertThat(result.wasCreated()).isFalse();
        });
    }

    @Test
    void findDirectChannelsFor_roundTrip() {
        String suffix = String.valueOf(System.nanoTime());
        QuarkusTransaction.requiringNew().run(() ->
                visibilityManager.getOrCreateDirect(
                        "finder-" + suffix, "peer-" + suffix));

        QuarkusTransaction.requiringNew().run(() -> {
            List<Channel> channels = visibilityManager.findDirectChannelsFor("finder-" + suffix);
            assertThat(channels).hasSize(1);
            assertThat(channels.get(0).visibility()).isEqualTo(ChannelVisibility.DIRECT);
        });
    }

    @Test
    void createGroupChannel_roundTrip() {
        UUID groupId = UUID.randomUUID();
        String suffix = String.valueOf(System.nanoTime());
        QuarkusTransaction.requiringNew().run(() -> {
            Channel ch = visibilityManager.createGroupChannel(
                    groupId, "team-" + suffix,
                    List.of("coordinator-" + suffix, "member-" + suffix), "coordinator-" + suffix);
            assertThat(ch.visibility()).isEqualTo(ChannelVisibility.PRIVATE);
            assertThat(ch.boundGroupId()).isEqualTo(groupId);
        });

        QuarkusTransaction.requiringNew().run(() -> {
            Optional<Channel> found = visibilityManager.findGroupChannel(groupId);
            assertThat(found).isPresent();
            assertThat(found.get().boundGroupId()).isEqualTo(groupId);
        });
    }
}
