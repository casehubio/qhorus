package io.casehub.qhorus.api.message;

import io.casehub.platform.api.identity.ActorType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MessageTest {

    @Test
    void builder_createsRecordWithAllFields() {
        UUID        channelId    = UUID.randomUUID();
        UUID        commitmentId = UUID.randomUUID();
        ArtefactRef ref1         = new ArtefactRef(UUID.randomUUID().toString(), ArtefactType.DOCUMENT, "Spec", null);
        ArtefactRef ref2         = new ArtefactRef("https://example.com", ArtefactType.EXTERNAL, null, null);
        Instant     now          = Instant.now();
        Instant     deadline     = now.plusSeconds(300);
        Instant     ackAt        = now.plusSeconds(10);

        Message msg = Message.builder()
                             .id(1L)
                             .channelId(channelId)
                             .sender("agent-alpha")
                             .messageType(MessageType.COMMAND)
                             .actorType(ActorType.AGENT)
                             .tenancyId("tenant-1")
                             .content("do the thing")
                             .correlationId("corr-123")
                             .inReplyTo(42L)
                             .replyCount(3)
                             .artefactRefs(List.of(ref1, ref2))
                             .target("role:specialist")
                             .commitmentId(commitmentId)
                             .deadline(deadline)
                             .acknowledgedAt(ackAt)
                             .version(2)
                             .createdAt(now)
                             .build();

        assertThat(msg.id()).isEqualTo(1L);
        assertThat(msg.channelId()).isEqualTo(channelId);
        assertThat(msg.sender()).isEqualTo("agent-alpha");
        assertThat(msg.messageType()).isEqualTo(MessageType.COMMAND);
        assertThat(msg.actorType()).isEqualTo(ActorType.AGENT);
        assertThat(msg.tenancyId()).isEqualTo("tenant-1");
        assertThat(msg.content()).isEqualTo("do the thing");
        assertThat(msg.correlationId()).isEqualTo("corr-123");
        assertThat(msg.inReplyTo()).isEqualTo(42L);
        assertThat(msg.replyCount()).isEqualTo(3);
        assertThat(msg.artefactRefs()).containsExactly(ref1, ref2);
        assertThat(msg.target()).isEqualTo("role:specialist");
        assertThat(msg.commitmentId()).isEqualTo(commitmentId);
        assertThat(msg.deadline()).isEqualTo(deadline);
        assertThat(msg.acknowledgedAt()).isEqualTo(ackAt);
        assertThat(msg.version()).isEqualTo(2);
        assertThat(msg.createdAt()).isEqualTo(now);}

    @Test
    void toBuilder_roundTripsAllFields() {
        UUID        channelId    = UUID.randomUUID();
        UUID        commitmentId = UUID.randomUUID();
        ArtefactRef ref1         = new ArtefactRef(UUID.randomUUID().toString(), ArtefactType.CODE, "Main.java", null);
        Instant     now          = Instant.now();
        Instant     deadline     = now.plusSeconds(300);
        Instant     ackAt        = now.plusSeconds(10);

        Message original = Message.builder()
                                  .id(1L)
                                  .channelId(channelId)
                                  .sender("agent-alpha")
                                  .messageType(MessageType.COMMAND)
                                  .actorType(ActorType.AGENT)
                                  .tenancyId("tenant-1")
                                  .content("do the thing")
                                  .correlationId("corr-123")
                                  .inReplyTo(42L)
                                  .replyCount(3)
                                  .artefactRefs(List.of(ref1))
                                  .target("role:specialist")
                                  .commitmentId(commitmentId)
                                  .deadline(deadline)
                                  .acknowledgedAt(ackAt)
                                  .version(2)
                                  .createdAt(now)
                                  .build();

        Message copy = original.toBuilder().build();

        assertThat(copy.id()).isEqualTo(original.id());
        assertThat(copy.channelId()).isEqualTo(original.channelId());
        assertThat(copy.sender()).isEqualTo(original.sender());
        assertThat(copy.messageType()).isEqualTo(original.messageType());
        assertThat(copy.actorType()).isEqualTo(original.actorType());
        assertThat(copy.tenancyId()).isEqualTo(original.tenancyId());
        assertThat(copy.content()).isEqualTo(original.content());
        assertThat(copy.correlationId()).isEqualTo(original.correlationId());
        assertThat(copy.inReplyTo()).isEqualTo(original.inReplyTo());
        assertThat(copy.replyCount()).isEqualTo(original.replyCount());
        assertThat(copy.artefactRefs()).isEqualTo(original.artefactRefs());
        assertThat(copy.target()).isEqualTo(original.target());
        assertThat(copy.commitmentId()).isEqualTo(original.commitmentId());
        assertThat(copy.deadline()).isEqualTo(original.deadline());
        assertThat(copy.acknowledgedAt()).isEqualTo(original.acknowledgedAt());
        assertThat(copy.version()).isEqualTo(original.version());
        assertThat(copy.createdAt()).isEqualTo(original.createdAt());}

    @Test
    void nullArtefactRefs_preservedAsNull() {
        Message msg = Message.builder()
                .sender("agent-beta")
                .messageType(MessageType.STATUS)
                .build();

        assertThat(msg.artefactRefs()).isNull();
    }

    @Test
    void defensiveCopy_preventsArtefactRefsMutationAfterConstruction() {
        ArtefactRef                 ref1 = new ArtefactRef(UUID.randomUUID().toString(), ArtefactType.DOCUMENT, null, null);
        java.util.List<ArtefactRef> refs = new ArrayList<>(java.util.List.of(ref1));

        Message msg = Message.builder()
                             .sender("agent-gamma")
                             .messageType(MessageType.QUERY)
                             .artefactRefs(refs)
                             .build();

        refs.add(new ArtefactRef("https://example.com", ArtefactType.EXTERNAL, null, null));

        assertThat(msg.artefactRefs()).containsExactly(ref1);}
}
