package io.casehub.qhorus.runtime.api;

import io.casehub.platform.api.identity.ActorType;
import io.casehub.qhorus.api.A2AEnabledProfile;
import io.casehub.qhorus.api.channel.ChannelCreateRequest;
import io.casehub.qhorus.api.message.DispatchResult;
import io.casehub.qhorus.api.message.MessageDispatch;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.runtime.channel.ChannelService;
import io.casehub.qhorus.runtime.message.MessageService;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@TestProfile(A2AEnabledProfile.class)
class A2AStreamIntegrationTest {

    @TestHTTPResource("")
    URI baseUri;

    @Inject
    A2AChannelBackend a2aBackend;
    @Inject
    ChannelService    channelService;
    @Inject
    MessageService    messageService;

    // ── Immediate-close paths ────────────────────────────────────────────────

    @Test
    void sseStream_nonStreamingMethod_returnsErrorEvent() throws Exception {
        String body = ssePost("{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"method\":\"tasks/get\",\"params\":{\"id\":\"" + UUID.randomUUID() + "\"}}");
        assertThat(body).contains("event:error");
        assertThat(body).contains("\"final\":true");
    }

    @Test
    void sseStream_alreadyTerminalDone_sendsImmediateFinalEvent() throws Exception {
        String       channelName = "stream-done-" + UUID.randomUUID();
        String       taskId      = UUID.randomUUID().toString();
        ChannelSetup setup       = createChannelAndDispatchCommand(channelName, taskId);

        QuarkusTransaction.requiringNew().run(() ->
                                                      messageService.dispatch(MessageDispatch.builder()
                                                                                             .channelId(setup.channelId()).sender("agent").type(MessageType.DONE)
                                                                                             .content("done").correlationId(taskId).inReplyTo(setup.commandMessageId())
                                                                                             .actorType(ActorType.AGENT).build()));

        String body = ssePost(streamBody(channelName, taskId));
        assertThat(body).contains("task_status_update");
        assertThat(body).contains("\"state\":\"completed\"");
        assertThat(body).contains("\"final\":true");
    }

    @Test
    void sseStream_alreadyTerminalDecline_sendsCanceledEvent() throws Exception {
        String       channelName = "stream-decline-" + UUID.randomUUID();
        String       taskId      = UUID.randomUUID().toString();
        ChannelSetup setup       = createChannelAndDispatchCommand(channelName, taskId);

        QuarkusTransaction.requiringNew().run(() ->
                                                      messageService.dispatch(MessageDispatch.builder()
                                                                                             .channelId(setup.channelId()).sender("agent").type(MessageType.DECLINE)
                                                                                             .content("I refuse").correlationId(taskId).inReplyTo(setup.commandMessageId())
                                                                                             .actorType(ActorType.AGENT).build()));

        String body = ssePost(streamBody(channelName, taskId));
        assertThat(body).contains("task_status_update");
        assertThat(body).contains("\"state\":\"canceled\"");
        assertThat(body).contains("\"final\":true");
    }

    // ── Live-stream paths ────────────────────────────────────────────────────

    @Test
    void sseStream_receivesCompletedEvent_whenDoneDispatched() throws Exception {
        String       channelName = "stream-live-done-" + UUID.randomUUID();
        String       taskId      = UUID.randomUUID().toString();
        ChannelSetup setup       = createChannelAndDispatchCommand(channelName, taskId);

        CopyOnWriteArrayList<String> events = new CopyOnWriteArrayList<>();
        CountDownLatch               latch  = new CountDownLatch(1);

        java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
                                                                      .connectTimeout(java.time.Duration.ofSeconds(5)).build();
        java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                                                                 .uri(URI.create(baseUri + "/a2a"))
                                                                 .header("Content-Type", "application/json")
                                                                 .header("Accept", "text/event-stream")
                                                                 .POST(java.net.http.HttpRequest.BodyPublishers.ofString(streamBody(channelName, taskId)))
                                                                 .build();

        java.util.concurrent.CompletableFuture<Void> streamFuture = httpClient.sendAsync(req,
                                                                                         java.net.http.HttpResponse.BodyHandlers.ofLines())
                                                                              .thenAccept(resp -> resp.body().forEach(line -> {
                                                                                  if (line.startsWith("data:")) {
                                                                                      String data = line.substring(5);
                                                                                      if (data.contains("task_status_update") || data.contains("\"state\"")) {
                                                                                          events.add(data);
                                                                                          latch.countDown();
                                                                                      }
                                                                                  }
                                                                              }));

        try {
            Awaitility.await().atMost(2, TimeUnit.SECONDS)
                      .until(() -> a2aBackend.streamCount(taskId) > 0);

            QuarkusTransaction.requiringNew().run(() ->
                                                          messageService.dispatch(MessageDispatch.builder()
                                                                                                 .channelId(setup.channelId()).sender("agent").type(MessageType.DONE)
                                                                                                 .content("done").correlationId(taskId).inReplyTo(setup.commandMessageId())
                                                                                                 .actorType(ActorType.AGENT).build()));

            assertThat(latch.await(10, TimeUnit.SECONDS)).as("No SSE event received within 10s").isTrue();
        } finally {
            streamFuture.cancel(true);
        }

        assertThat(events).anyMatch(e -> e.contains("\"state\":\"completed\"") && e.contains("\"final\":true"));
    }

    @Test
    void sseStream_receivesCanceledEvent_whenDeclineDispatched() throws Exception {
        String       channelName = "stream-live-decline-" + UUID.randomUUID();
        String       taskId      = UUID.randomUUID().toString();
        ChannelSetup setup       = createChannelAndDispatchCommand(channelName, taskId);

        CopyOnWriteArrayList<String> events = new CopyOnWriteArrayList<>();
        CountDownLatch               latch  = new CountDownLatch(1);

        java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
                                                                      .connectTimeout(java.time.Duration.ofSeconds(5)).build();
        java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                                                                 .uri(URI.create(baseUri + "/a2a"))
                                                                 .header("Content-Type", "application/json")
                                                                 .header("Accept", "text/event-stream")
                                                                 .POST(java.net.http.HttpRequest.BodyPublishers.ofString(streamBody(channelName, taskId)))
                                                                 .build();

        java.util.concurrent.CompletableFuture<Void> streamFuture = httpClient.sendAsync(req,
                                                                                         java.net.http.HttpResponse.BodyHandlers.ofLines())
                                                                              .thenAccept(resp -> resp.body().forEach(line -> {
                                                                                  if (line.startsWith("data:")) {
                                                                                      String data = line.substring(5);
                                                                                      if (data.contains("\"state\"")) {
                                                                                          events.add(data);
                                                                                          latch.countDown();
                                                                                      }
                                                                                  }
                                                                              }));

        try {
            Awaitility.await().atMost(2, TimeUnit.SECONDS)
                      .until(() -> a2aBackend.streamCount(taskId) > 0);

            QuarkusTransaction.requiringNew().run(() ->
                                                          messageService.dispatch(MessageDispatch.builder()
                                                                                                 .channelId(setup.channelId()).sender("agent").type(MessageType.DECLINE)
                                                                                                 .content("I refuse").correlationId(taskId).inReplyTo(setup.commandMessageId())
                                                                                                 .actorType(ActorType.AGENT).build()));

            assertThat(latch.await(10, TimeUnit.SECONDS)).as("No SSE event received within 10s").isTrue();
        } finally {
            streamFuture.cancel(true);
        }

        assertThat(events).anyMatch(e -> e.contains("\"state\":\"canceled\"") && e.contains("\"final\":true"));
    }

    @Test
    void sseStream_keepaliveEventsDoNotInterfereWithTaskStream() throws Exception {
        String channelName = "stream-keepalive-" + UUID.randomUUID();
        String taskId      = UUID.randomUUID().toString();
        createChannelAndDispatchCommand(channelName, taskId);

        CopyOnWriteArrayList<String> taskEvents = new CopyOnWriteArrayList<>();

        java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
                                                                      .connectTimeout(java.time.Duration.ofSeconds(5)).build();
        java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                                                                 .uri(URI.create(baseUri + "/a2a"))
                                                                 .header("Content-Type", "application/json")
                                                                 .header("Accept", "text/event-stream")
                                                                 .POST(java.net.http.HttpRequest.BodyPublishers.ofString(streamBody(channelName, taskId)))
                                                                 .build();

        java.util.concurrent.CompletableFuture<Void> streamFuture = httpClient.sendAsync(req,
                                                                                         java.net.http.HttpResponse.BodyHandlers.ofLines())
                                                                              .thenAccept(resp -> resp.body().forEach(line -> {
                                                                                  if (line.startsWith("data:") && line.contains("\"state\"")) {
                                                                                      taskEvents.add(line.substring(5));
                                                                                  }
                                                                              }));

        try {
            Awaitility.await().atMost(2, TimeUnit.SECONDS)
                      .until(() -> a2aBackend.streamCount(taskId) > 0);

            Thread.sleep(3_000);

            assertThat(taskEvents).as("Keepalive events must not appear as task events").isEmpty();
            assertThat(a2aBackend.streamCount(taskId))
                    .as("Connection must still be open after keepalives").isGreaterThan(0);
        } finally {
            streamFuture.cancel(true);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private record ChannelSetup(UUID channelId, long commandMessageId) {}

    private ChannelSetup createChannelAndDispatchCommand(String channelName, String taskId) {
        QuarkusTransaction.requiringNew().run(() -> channelService.create(ChannelCreateRequest.builder(channelName)
                                                                                              .description("SSE test").build()));
        UUID[] chId = {null};
        QuarkusTransaction.requiringNew().run(() ->
                                                      chId[0] = channelService.findByName(channelName).orElseThrow().id());
        Long[] cmdMsgId = {null};
        QuarkusTransaction.requiringNew().run(() -> {
            DispatchResult r = messageService.dispatch(MessageDispatch.builder()
                                                                      .channelId(chId[0]).sender("requester").type(MessageType.COMMAND)
                                                                      .content("do this").correlationId(taskId).actorType(ActorType.AGENT).build());
            cmdMsgId[0] = r.messageId();
        });
        return new ChannelSetup(chId[0], cmdMsgId[0]);
    }

    private String streamBody(String channelName, String taskId) {
        return "{\"jsonrpc\":\"2.0\",\"id\":\"" + UUID.randomUUID() + "\",\"method\":\"message/send\",\"params\":"
               + "{\"message\":{\"role\":\"user\",\"parts\":[{\"type\":\"text\",\"text\":\"stream\"}],"
               + "\"contextId\":\"" + channelName + "\",\"taskId\":\"" + taskId + "\"}}}";
    }

    private String ssePost(String body) throws Exception {
        return io.restassured.RestAssured.given()
                                         .contentType("application/json")
                                         .accept("text/event-stream")
                                         .body(body)
                                         .when().post("/a2a")
                                         .then().statusCode(200)
                                         .extract().body().asString();
    }
}
