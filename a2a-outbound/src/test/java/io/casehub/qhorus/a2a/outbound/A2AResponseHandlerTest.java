package io.casehub.qhorus.a2a.outbound;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.casehub.a2a.model.A2AArtifact;
import io.casehub.a2a.model.A2AMessage;
import io.casehub.a2a.model.A2APart;
import io.casehub.a2a.model.A2ATask;
import io.casehub.a2a.model.A2ATaskState;
import io.casehub.a2a.model.A2ATaskStatus;
import io.casehub.qhorus.api.message.MessageDispatch;
import io.casehub.qhorus.api.message.MessageType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class A2AResponseHandlerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private A2AResponseHandler handler;

    @BeforeEach
    void setUp() {
        handler = new A2AResponseHandler(new com.fasterxml.jackson.databind.ObjectMapper());
    }

    @Test
    void completed_mapsToDone() {
        final A2ATask task = taskWithState(A2ATaskState.COMPLETED);
        final ResponseContext ctx = context();

        MessageDispatch dispatch = handler.mapResponse(task, ctx);

        assertThat(dispatch.type()).isEqualTo(MessageType.DONE);
        assertThat(dispatch.channelId()).isEqualTo(ctx.channelId());
        assertThat(dispatch.sender()).isEqualTo(ctx.externalAgentInstanceId());
        assertThat(dispatch.correlationId()).isEqualTo(ctx.correlationId());
        assertThat(dispatch.inReplyTo()).isEqualTo(ctx.inReplyTo());
    }

    @Test
    void failed_mapsToFailure() {
        final A2ATask task = taskWithState(A2ATaskState.FAILED);
        final ResponseContext ctx = context();

        MessageDispatch dispatch = handler.mapResponse(task, ctx);

        assertThat(dispatch.type()).isEqualTo(MessageType.FAILURE);
    }

    @Test
    void canceled_mapsToDecline() {
        final A2ATask task = taskWithState(A2ATaskState.CANCELED);
        final ResponseContext ctx = context();

        MessageDispatch dispatch = handler.mapResponse(task, ctx);

        assertThat(dispatch.type()).isEqualTo(MessageType.DECLINE);
    }

    @Test
    void working_mapsToStatus() {
        final A2ATask task = taskWithState(A2ATaskState.WORKING);
        final ResponseContext ctx = context();

        MessageDispatch dispatch = handler.mapResponse(task, ctx);

        assertThat(dispatch.type()).isEqualTo(MessageType.STATUS);
    }

    @Test
    void inputRequired_mapsToStatusWithPayload() {
        final A2ATask task = taskWithState(A2ATaskState.INPUT_REQUIRED);
        final ResponseContext ctx = context();

        MessageDispatch dispatch = handler.mapResponse(task, ctx);

        assertThat(dispatch.type()).isEqualTo(MessageType.STATUS);
        assertThat(dispatch.payload()).contains("\"input_required\":true");
    }

    @Test
    void textPart_extractedAsContent() {
        final A2AMessage message = new A2AMessage("agent", List.of(new A2APart.TextPart("Hello from external")),
                null, null, null, Map.of());
        final A2ATask task = new A2ATask("t1", null,
                new A2ATaskStatus(A2ATaskState.COMPLETED, message), List.of(), List.of());
        final ResponseContext ctx = context();

        MessageDispatch dispatch = handler.mapResponse(task, ctx);

        assertThat(dispatch.content()).isEqualTo("Hello from external");
    }

    @Test
    void multipleTextParts_concatenatedWithSeparator() {
        final A2AMessage message = new A2AMessage("agent",
                List.of(new A2APart.TextPart("Part one"), new A2APart.TextPart("Part two")),
                null, null, null, Map.of());
        final A2ATask task = new A2ATask("t1", null,
                new A2ATaskStatus(A2ATaskState.COMPLETED, message), List.of(), List.of());

        MessageDispatch dispatch = handler.mapResponse(task, context());

        assertThat(dispatch.content()).isEqualTo("Part one\n\nPart two");
    }

    @Test
    void dataPart_extractedAsPayload() {
        final ObjectNode data = MAPPER.createObjectNode().put("key", "value");
        final A2AMessage message = new A2AMessage("agent",
                List.of(new A2APart.DataPart("application/json", data)),
                null, null, null, Map.of());
        final A2ATask task = new A2ATask("t1", null,
                new A2ATaskStatus(A2ATaskState.COMPLETED, message), List.of(), List.of());

        MessageDispatch dispatch = handler.mapResponse(task, context());

        assertThat(dispatch.payload()).contains("\"key\":\"value\"");
    }

    @Test
    void noStatusMessage_nullContent() {
        final A2ATask task = new A2ATask("t1", null,
                new A2ATaskStatus(A2ATaskState.WORKING, null), List.of(), List.of());

        MessageDispatch dispatch = handler.mapResponse(task, context());

        assertThat(dispatch.content()).isNull();
    }

    @Test
    void completedWithArtifacts_artifactTextMergedIntoContent() {
        final A2AMessage message = new A2AMessage("agent",
                List.of(new A2APart.TextPart("Done")), null, null, null, Map.of());
        final A2AArtifact artifact = new A2AArtifact("result",
                List.of(new A2APart.TextPart("Artifact output")), 0, false);
        final A2ATask task = new A2ATask("t1", null,
                new A2ATaskStatus(A2ATaskState.COMPLETED, message), List.of(artifact), List.of());

        MessageDispatch dispatch = handler.mapResponse(task, context());

        assertThat(dispatch.content()).isEqualTo("Done\n\nArtifact output");
    }

    @Test
    void completedWithArtifactDataParts_mergedIntoPayload() {
        final ObjectNode artifactData = MAPPER.createObjectNode().put("result", 42);
        final A2AArtifact artifact = new A2AArtifact("data-result",
                List.of(new A2APart.DataPart("application/json", artifactData)), 0, false);
        final A2ATask task = new A2ATask("t1", null,
                new A2ATaskStatus(A2ATaskState.COMPLETED, null), List.of(artifact), List.of());

        MessageDispatch dispatch = handler.mapResponse(task, context());

        assertThat(dispatch.payload()).contains("\"result\":42");
        assertThat(dispatch.payload()).contains("\"name\":\"data-result\"");
    }

    @Test
    void submitted_mapsToStatus() {
        final A2ATask task = taskWithState(A2ATaskState.SUBMITTED);
        MessageDispatch dispatch = handler.mapResponse(task, context());
        assertThat(dispatch.type()).isEqualTo(MessageType.STATUS);
    }

    private static A2ATask taskWithState(A2ATaskState state) {
        return new A2ATask("task-1", null, new A2ATaskStatus(state, null), List.of(), List.of());
    }

    private static ResponseContext context() {
        return new ResponseContext(UUID.randomUUID(), "ext-agent-1",
                UUID.randomUUID().toString(), 42L);
    }
}
