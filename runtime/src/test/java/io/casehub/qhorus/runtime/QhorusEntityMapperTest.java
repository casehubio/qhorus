package io.casehub.qhorus.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.qhorus.api.message.ArtefactRef;
import io.casehub.qhorus.api.message.ArtefactType;
import io.casehub.qhorus.api.message.Message;
import io.casehub.qhorus.api.message.MessageType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class QhorusEntityMapperTest {

    private final QhorusEntityMapper mapper = new QhorusEntityMapper(new ObjectMapper());

    @Test
    void toTimelineEntry_message_includesEnrichedFields() {
        Message m = Message.builder()
                .id(1L).channelId(UUID.randomUUID()).sender("agent-1")
                .messageType(MessageType.COMMAND).actorType(ActorType.AGENT)
                .content("do something").correlationId("corr-1")
                .inReplyTo(42L).target("agent-2").replyCount(3)
                .deadline(Instant.parse("2026-08-01T00:00:00Z"))
                .topic("work-items")
                .artefactRefs(List.of(new ArtefactRef("doc://spec.md", ArtefactType.DOCUMENT, "Spec", null)))
                .build();

        Map<String, Object> entry = mapper.toTimelineEntry(m);

        assertThat(entry.get("type")).isEqualTo("MESSAGE");
        assertThat(entry.get("in_reply_to")).isEqualTo(42L);
        assertThat(entry.get("target")).isEqualTo("agent-2");
        assertThat(entry.get("reply_count")).isEqualTo(3);
        assertThat(entry.get("topic")).isEqualTo("work-items");
        assertThat(entry.get("deadline")).isEqualTo("2026-08-01T00:00:00Z");
        @SuppressWarnings("unchecked")
        List<ArtefactRef> refs = (List<ArtefactRef>) entry.get("artefact_refs");
        assertThat(refs).hasSize(1);
        assertThat(refs.get(0).uri()).isEqualTo("doc://spec.md");
    }

    @Test
    void toTimelineEntry_message_nullOptionalFields() {
        Message m = Message.builder()
                .id(2L).channelId(UUID.randomUUID()).sender("agent-1")
                .messageType(MessageType.STATUS).actorType(ActorType.AGENT)
                .content("status update")
                .build();

        Map<String, Object> entry = mapper.toTimelineEntry(m);

        assertThat(entry.get("in_reply_to")).isNull();
        assertThat(entry.get("target")).isNull();
        assertThat(entry.get("reply_count")).isEqualTo(0);
        assertThat(entry.get("topic")).isNull();
        assertThat(entry.get("deadline")).isNull();
        assertThat(entry.get("artefact_refs")).isNull();
    }

    @Test
    void toTimelineEntry_event_doesNotIncludeMessageFields() {
        Message m = Message.builder()
                .id(3L).channelId(UUID.randomUUID()).sender("system")
                .messageType(MessageType.EVENT).actorType(ActorType.SYSTEM)
                .build();

        Map<String, Object> entry = mapper.toTimelineEntry(m);

        assertThat(entry.get("type")).isEqualTo("EVENT");
        assertThat(entry).doesNotContainKey("in_reply_to");
        assertThat(entry).doesNotContainKey("target");
        assertThat(entry).doesNotContainKey("reply_count");
    }
}
