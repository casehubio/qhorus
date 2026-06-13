package io.casehub.qhorus.api;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.casehub.platform.api.identity.ActorType;
import io.casehub.qhorus.api.channel.ChannelSemantic;
import io.casehub.qhorus.api.message.DispatchResult;
import io.casehub.qhorus.api.message.MessageDispatch;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.runtime.channel.ChannelCreateRequest;
import io.casehub.qhorus.runtime.channel.ChannelService;
import io.casehub.qhorus.runtime.message.MessageService;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

/**
 * Integration tests for {@code GET /a2a/tasks/{id}/stream}.
 *
 * <p>Tests cover immediate-close paths (task not found, A2A disabled, already terminal)
 * which do not require a long-lived SSE client.
 *
 * <p>Refs qhorus#147.
 */
@QuarkusTest
@TestProfile(A2AEnabledProfile.class)
class A2AStreamTaskTest {

    @Inject
    ChannelService channelService;

    @Inject
    MessageService messageService;

    private static final String STREAM_BASE = "/a2a/tasks/";

    // ── immediate-close: task not found ──────────────────────────────────────

    @Test
    void streamTask_taskNotFound_returnsEventStreamWithErrorEvent() {
        final String taskId = UUID.randomUUID().toString();

        final String body = given()
                .accept("text/event-stream")
                .when()
                .get(STREAM_BASE + taskId + "/stream")
                .then()
                .statusCode(200)
                .contentType("text/event-stream")
                .extract().body().asString();

        assertThat(body).contains("event:error");
        assertThat(body).contains("\"error\"");
        assertThat(body).contains("\"final\":true");
    }

    @Test
    void streamTask_invalidUuid_returnsEventStreamWithErrorEvent() {
        final String body = given()
                .accept("text/event-stream")
                .when()
                .get(STREAM_BASE + "not-a-uuid/stream")
                .then()
                .statusCode(200)
                .contentType("text/event-stream")
                .extract().body().asString();

        assertThat(body).contains("event:error");
        assertThat(body).contains("\"final\":true");
    }

    // ── immediate-close: already terminal ────────────────────────────────────

    @Test
    void streamTask_alreadyTerminalTask_returnsImmediateFinalEvent() {
        final String channelName = "stream-terminal-" + UUID.randomUUID();
        final String taskId = UUID.randomUUID().toString();
        final UUID[] chId = new UUID[1];
        final Long[] cmdMsgId = new Long[1];

        // Step 1: create channel
        QuarkusTransaction.requiringNew().run(() -> channelService.create(new ChannelCreateRequest(
                channelName, "SSE test", ChannelSemantic.APPEND,
                null, null, null, null, null, null, null, null, null, null, null)));

        // Step 2: get channel ID
        QuarkusTransaction.requiringNew().run(() ->
                chId[0] = channelService.findByName(channelName).orElseThrow().id);

        // Step 3: send COMMAND, capture message ID for inReplyTo
        QuarkusTransaction.requiringNew().run(() -> {
            final DispatchResult r = messageService.dispatch(MessageDispatch.builder()
                    .channelId(chId[0]).sender("requester")
                    .type(MessageType.COMMAND).content("do this")
                    .correlationId(taskId).actorType(ActorType.AGENT).build());
            cmdMsgId[0] = r.messageId();
        });

        // Step 4: send DONE with inReplyTo pointing at the COMMAND
        QuarkusTransaction.requiringNew().run(() ->
                messageService.dispatch(MessageDispatch.builder()
                        .channelId(chId[0]).sender("agent")
                        .type(MessageType.DONE).content("done")
                        .correlationId(taskId).inReplyTo(cmdMsgId[0])
                        .actorType(ActorType.AGENT).build()));

        // SSE connect — already terminal, should immediately return completed event and close
        final String body = given()
                .accept("text/event-stream")
                .when()
                .get(STREAM_BASE + taskId + "/stream")
                .then()
                .statusCode(200)
                .contentType("text/event-stream")
                .extract().body().asString();

        assertThat(body).contains("event:task_status_update");
        assertThat(body).contains("\"state\":\"completed\"");
        assertThat(body).contains("\"final\":true");
    }

    @Test
    void streamTask_terminalDecline_returnsCancelledNotFailed() {
        final String channelName = "stream-decline-" + UUID.randomUUID();
        final String taskId = UUID.randomUUID().toString();
        final UUID[] chId = new UUID[1];
        final Long[] cmdMsgId = new Long[1];

        QuarkusTransaction.requiringNew().run(() -> channelService.create(new ChannelCreateRequest(
                channelName, "SSE decline test", ChannelSemantic.APPEND,
                null, null, null, null, null, null, null, null, null, null, null)));

        QuarkusTransaction.requiringNew().run(() ->
                chId[0] = channelService.findByName(channelName).orElseThrow().id);

        QuarkusTransaction.requiringNew().run(() -> {
            final DispatchResult r = messageService.dispatch(MessageDispatch.builder()
                    .channelId(chId[0]).sender("requester")
                    .type(MessageType.COMMAND).content("do this")
                    .correlationId(taskId).actorType(ActorType.AGENT).build());
            cmdMsgId[0] = r.messageId();
        });

        QuarkusTransaction.requiringNew().run(() ->
                messageService.dispatch(MessageDispatch.builder()
                        .channelId(chId[0]).sender("agent")
                        .type(MessageType.DECLINE).content("I refuse")
                        .correlationId(taskId).inReplyTo(cmdMsgId[0])
                        .actorType(ActorType.AGENT).build()));

        final String body = given()
                .accept("text/event-stream")
                .when()
                .get(STREAM_BASE + taskId + "/stream")
                .then()
                .statusCode(200)
                .contentType("text/event-stream")
                .extract().body().asString();

        // DECLINE → "cancelled", not "failed". Refs #147.
        assertThat(body).contains("\"state\":\"cancelled\"");
        assertThat(body).contains("\"final\":true");
    }
}
