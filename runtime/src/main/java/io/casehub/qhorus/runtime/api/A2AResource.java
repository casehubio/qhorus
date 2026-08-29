package io.casehub.qhorus.runtime.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.a2a.jsonrpc.JsonRpcError;
import io.casehub.a2a.jsonrpc.JsonRpcRequest;
import io.casehub.a2a.model.A2AMessage;
import io.casehub.a2a.model.A2APart;
import io.casehub.a2a.model.A2ATask;
import io.casehub.a2a.model.A2ATaskState;
import io.casehub.a2a.model.A2ATaskStatus;
import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.gateway.ChannelRef;
import io.casehub.qhorus.api.gateway.OutboundMessage;
import io.casehub.qhorus.api.message.CommitmentState;
import io.casehub.qhorus.api.message.Message;
import io.casehub.qhorus.runtime.channel.ChannelService;
import io.casehub.qhorus.runtime.config.QhorusConfig;
import io.casehub.qhorus.runtime.message.CommitmentService;
import io.casehub.qhorus.runtime.message.MessageService;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseEventSink;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Path("/a2a")
@ApplicationScoped
public class A2AResource {

    private static final Logger LOG = Logger.getLogger(A2AResource.class);

    @Inject
    QhorusConfig config;

    @Inject
    A2AChannelBackend a2aBackend;

    @Inject
    CommitmentService commitmentService;

    @Inject
    MessageService messageService;

    @Inject
    ChannelService channelService;

    @Inject
    io.casehub.qhorus.api.store.ChannelMembershipStore channelMembershipStore;

    @Inject
    ObjectMapper mapper;
    @Inject
    jakarta.enterprise.inject.Instance<io.casehub.qhorus.api.store.PushNotificationConfigStore> pushNotificationConfigStore;

    @Inject
    jakarta.enterprise.inject.Instance<io.casehub.qhorus.api.a2a.PushNotificationRegistrar> pushNotificationRegistrar;
    @Inject
    io.casehub.platform.api.identity.CurrentPrincipal                                       currentPrincipal;


    // -----------------------------------------------------------------------
    // JSON-RPC 2.0 dispatch — sync
    // -----------------------------------------------------------------------

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional
    public Response dispatch(JsonRpcRequest request, @Context HttpHeaders headers) {
        if (!config.a2a().enabled()) {
            return Response.status(Response.Status.NOT_IMPLEMENTED)
                           .entity(jsonRpcErrorNode(request, JsonRpcError.INTERNAL_ERROR,
                                                    "A2A endpoint is disabled. Set casehub.qhorus.a2a.enabled=true to activate."))
                           .type(MediaType.APPLICATION_JSON).build();
        }
        if (request == null || request.method() == null) {
            return Response.ok(jsonRpcErrorNode(request, JsonRpcError.INVALID_REQUEST, null))
                           .type(MediaType.APPLICATION_JSON).build();
        }
        return switch (request.method()) {
            case "message/send" -> handleMessageSend(request, headers);
            case "tasks/get" -> handleTasksGet(request);
            case "tasks/cancel" -> handleTasksCancel(request);
            case "pushNotificationConfig/set" -> handlePushConfigSet(request);
            case "pushNotificationConfig/get" -> handlePushConfigGet(request);
            case "pushNotificationConfig/list" -> handlePushConfigList(request);
            case "pushNotificationConfig/delete" -> handlePushConfigDelete(request);
            default -> Response.ok(jsonRpcErrorNode(request, JsonRpcError.METHOD_NOT_FOUND,
                                                    request.method())).type(MediaType.APPLICATION_JSON).build();
        };
    }

    // -----------------------------------------------------------------------
    // JSON-RPC 2.0 dispatch — SSE stream
    // -----------------------------------------------------------------------

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces("text/event-stream")
    @RunOnVirtualThread
    public void dispatchStream(JsonRpcRequest request,
                               @Context SseEventSink sink,
                               @Context Sse sse) throws Exception {
        if (!config.a2a().enabled()) {
            sendErrorEvent(sink, sse, null, "A2A endpoint is disabled");
            return;
        }
        if (request == null || !"message/send".equals(request.method())) {
            String msg = request != null && request.method() != null
                         ? "Method '" + request.method() + "' does not support streaming"
                         : "Invalid request";
            sendErrorEvent(sink, sse, null, msg);
            return;
        }
        handleMessageSendStream(request, sink, sse);
    }

    // -----------------------------------------------------------------------
    // message/send — sync
    // -----------------------------------------------------------------------

    private Response handleMessageSend(JsonRpcRequest request, HttpHeaders headers) {
        JsonNode params = request.params();
        if (params == null || !params.has("message")) {
            return Response.ok(jsonRpcErrorNode(request, JsonRpcError.INVALID_PARAMS,
                                                "message is required")).type(MediaType.APPLICATION_JSON).build();
        }

        A2AMessage msg;
        try {
            msg = mapper.treeToValue(params.get("message"), A2AMessage.class);
        } catch (Exception e) {
            return Response.ok(jsonRpcErrorNode(request, JsonRpcError.INVALID_PARAMS,
                                                "Invalid message: " + e.getMessage())).type(MediaType.APPLICATION_JSON).build();
        }

        if (msg.contextId() == null || msg.contextId().isBlank()) {
            return Response.ok(jsonRpcErrorNode(request, JsonRpcError.INVALID_PARAMS,
                                                "message.contextId (channel name) is required")).type(MediaType.APPLICATION_JSON).build();
        }
        if (msg.parts() == null || msg.parts().isEmpty()) {
            return Response.ok(jsonRpcErrorNode(request, JsonRpcError.INVALID_PARAMS,
                                                "message.parts must contain at least one text part")).type(MediaType.APPLICATION_JSON).build();
        }

        String text = msg.parts().stream()
                         .filter(p -> p instanceof A2APart.TextPart)
                         .map(p -> ((A2APart.TextPart) p).text())
                         .filter(java.util.Objects::nonNull)
                         .findFirst().orElse(null);
        if (text == null) {
            return Response.ok(jsonRpcErrorNode(request, JsonRpcError.INVALID_PARAMS,
                                                "message.parts must contain at least one text part")).type(MediaType.APPLICATION_JSON).build();
        }

        Channel channel = channelService.findByName(msg.contextId()).orElse(null);
        if (channel == null) {
            return Response.ok(jsonRpcErrorNode(request, JsonRpcError.INVALID_PARAMS,
                                                "channel not found: " + msg.contextId())).type(MediaType.APPLICATION_JSON).build();
        }

        a2aBackend.ensureRegistered(channel.id(), new ChannelRef(channel.id(), channel.name()));

        @SuppressWarnings("unchecked")
        Map<String, String> metadata = msg.metadata() != null
                                       ? msg.metadata().entrySet().stream()
                                            .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, e -> String.valueOf(e.getValue())))
                                       : Map.of();
        String taskId = (msg.taskId() != null && !msg.taskId().isBlank())
                        ? msg.taskId()
                        : UUID.randomUUID().toString();

        try {
            String actorTypeHeader = headers != null ? headers.getHeaderString("x-qhorus-actor-type") : null;
            a2aBackend.receive(msg.contextId(), msg.role(), text, taskId, metadata, actorTypeHeader);
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            return Response.ok(jsonRpcErrorNode(request, JsonRpcError.INTERNAL_ERROR,
                                                cause.getMessage())).type(MediaType.APPLICATION_JSON).build();
        }

        if (pushNotificationConfigStore.isResolvable() && params.has("pushNotificationConfig")) {
            JsonNode pushCfg = params.get("pushNotificationConfig");
            if (pushCfg.has("url")) {
                String pushUrl = pushCfg.get("url").asText();
                String pushToken = pushCfg.has("token") ? pushCfg.get("token").asText() : null;
                String pushAuthScheme = pushCfg.has("authScheme") ? pushCfg.get("authScheme").asText() : null;
                String pushAuthRef = pushCfg.has("authCredentialsRef") ? pushCfg.get("authCredentialsRef").asText() : null;
                var cfg = new io.casehub.qhorus.api.a2a.PushNotificationConfig(
                        UUID.randomUUID(), taskId, channel.id(), pushUrl, pushToken,
                        pushAuthScheme, pushAuthRef,
                        currentPrincipal.tenancyId(),
                        java.time.Instant.now(), null);
                pushNotificationConfigStore.get().put(cfg);
                if (pushNotificationRegistrar.isResolvable()) {
                    pushNotificationRegistrar.get().onConfigCreated(channel.id(), taskId);
                }
            }
        }

        var task = new A2ATask(taskId, msg.contextId(),
                                                    new A2ATaskStatus(A2ATaskState.SUBMITTED, null),
                                                    null, null);
        return Response.ok(jsonRpcSuccessNode(request, task)).type(MediaType.APPLICATION_JSON).build();
    }

    // -----------------------------------------------------------------------
    // tasks/get — sync
    // -----------------------------------------------------------------------

    private Response handleTasksGet(JsonRpcRequest request) {
        JsonNode params = request.params();
        String                                  taskId = params != null && params.has("id") ? params.get("id").asText() : null;
        if (taskId == null || taskId.isBlank()) {
            return Response.ok(jsonRpcErrorNode(request, JsonRpcError.INVALID_PARAMS,
                                                "id is required")).type(MediaType.APPLICATION_JSON).build();
        }

        List<Message> messages = messageService.findAllByCorrelationId(taskId);
        if (messages.isEmpty()) {
            return Response.ok(jsonRpcErrorNode(request, JsonRpcError.INVALID_PARAMS,
                                                "Task not found: " + taskId)).type(MediaType.APPLICATION_JSON).build();
        }

        Channel channel = channelService.findById(messages.get(0).channelId())
                                        .orElseThrow(() -> new IllegalStateException("Channel not found for task " + taskId));

        io.casehub.qhorus.api.message.Commitment commitment =
                commitmentService.findByCorrelationId(taskId).orElse(null);
        String stateStr = (commitment != null && commitment.state() != CommitmentState.OPEN)
                          ? A2ATaskStateMapper.fromCommitmentState(commitment.state())
                          : A2ATaskStateMapper.fromMessageHistory(messages);

        A2ATaskState state = A2ATaskState.fromWireValue(stateStr);

        List<A2AMessage> history = messages.stream()
                                                                .map(m -> new A2AMessage(
                                                                        m.sender(),
                                                                        m.content() != null
                                                                        ? List.of(new A2APart.TextPart(m.content()))
                                                                        : List.of(),
                                                                        null, m.correlationId(), channel.name(), null))
                                                                .toList();

        var task = new A2ATask(taskId, channel.name(),
                                                    new A2ATaskStatus(state, null), null, history);
        return Response.ok(jsonRpcSuccessNode(request, task)).type(MediaType.APPLICATION_JSON).build();
    }

    // -----------------------------------------------------------------------
    // tasks/cancel — sync
    // -----------------------------------------------------------------------

    private Response handleTasksCancel(JsonRpcRequest request) {
        JsonNode params = request.params();
        String                                  taskId = params != null && params.has("id") ? params.get("id").asText() : null;
        if (taskId == null || taskId.isBlank()) {
            return Response.ok(jsonRpcErrorNode(request, JsonRpcError.INVALID_PARAMS,
                                                "id is required")).type(MediaType.APPLICATION_JSON).build();
        }

        var declined = commitmentService.decline(taskId);
        if (declined.isEmpty()) {
            return Response.ok(jsonRpcErrorNode(request, JsonRpcError.INVALID_PARAMS,
                                                "Task not found: " + taskId)).type(MediaType.APPLICATION_JSON).build();
        }

        List<Message> messages    = messageService.findAllByCorrelationId(taskId);
        String        channelName = null;
        if (!messages.isEmpty()) {
            channelName = channelService.findById(messages.get(0).channelId())
                                        .map(Channel::name).orElse(null);
        }

        var task = new A2ATask(taskId, channelName,
                                                    new A2ATaskStatus(A2ATaskState.CANCELED, null),
                                                    null, null);
        return Response.ok(jsonRpcSuccessNode(request, task)).type(MediaType.APPLICATION_JSON).build();
    }

    // -----------------------------------------------------------------------
    // message/send — SSE stream
    // -----------------------------------------------------------------------

    private void handleMessageSendStream(JsonRpcRequest request,
                                         SseEventSink sink, Sse sse) throws Exception {
        JsonNode params = request.params();
        if (params == null || !params.has("message")) {
            sendErrorEvent(sink, sse, null, "message is required");
            return;
        }

        A2AMessage msg;
        try {
            msg = mapper.treeToValue(params.get("message"), A2AMessage.class);
        } catch (Exception e) {
            sendErrorEvent(sink, sse, null, "Invalid message: " + e.getMessage());
            return;
        }

        String taskId = (msg.taskId() != null && !msg.taskId().isBlank())
                        ? msg.taskId()
                        : UUID.randomUUID().toString();
        final String corrId = taskId.toLowerCase();

        try {
            UUID.fromString(taskId);
        } catch (IllegalArgumentException e) {
            sendErrorEvent(sink, sse, taskId, "Invalid task ID format — expected UUID");
            return;
        }

        // Short-lived transactional reads
        final java.util.concurrent.atomic.AtomicBoolean           notFound            = new java.util.concurrent.atomic.AtomicBoolean(false);
        final java.util.concurrent.atomic.AtomicReference<String> stateRef            = new java.util.concurrent.atomic.AtomicReference<>("submitted");
        final java.util.concurrent.atomic.AtomicReference<UUID>   channelIdRef        = new java.util.concurrent.atomic.AtomicReference<>();
        final java.util.concurrent.atomic.AtomicReference<String> channelNameRef      = new java.util.concurrent.atomic.AtomicReference<>();
        final java.util.concurrent.atomic.AtomicReference<String> consumerMemberIdRef = new java.util.concurrent.atomic.AtomicReference<>();
        final java.util.concurrent.atomic.AtomicBoolean           trackDeliveryRef    = new java.util.concurrent.atomic.AtomicBoolean(false);

        // First try to find existing messages for this taskId
        QuarkusTransaction.requiringNew().run(() -> {
            List<Message> messages = messageService.findAllByCorrelationId(taskId);
            if (!messages.isEmpty()) {
                io.casehub.qhorus.api.message.Commitment commitment =
                        commitmentService.findByCorrelationId(taskId).orElse(null);
                String state = (commitment != null && commitment.state() != CommitmentState.OPEN)
                               ? A2ATaskStateMapper.fromCommitmentState(commitment.state())
                               : A2ATaskStateMapper.fromMessageHistory(messages);
                stateRef.set(state);
                Message first = messages.get(0);
                channelIdRef.set(first.channelId());
                consumerMemberIdRef.set(first.sender());
                Channel ch = channelService.findById(first.channelId()).orElse(null);
                if (ch != null) {
                    channelNameRef.set(ch.name());
                    trackDeliveryRef.set(io.casehub.qhorus.runtime.channel.ChannelService.isDeliveryTrackingEnabled(ch));
                }
            } else if (msg.contextId() != null) {
                Channel ch = channelService.findByName(msg.contextId()).orElse(null);
                if (ch != null) {
                    channelIdRef.set(ch.id());
                    channelNameRef.set(ch.name());
                }
            }
        });

        if (A2ATaskStateMapper.TERMINAL_STATES.contains(stateRef.get())) {
            sendStatusEvent(sink, sse, taskId, stateRef.get());
            return;
        }

        // Ensure A2AChannelBackend is registered
        UUID   channelId   = channelIdRef.get();
        String channelName = channelNameRef.get();
        if (channelId != null && channelName != null) {
            a2aBackend.ensureRegistered(channelId, new ChannelRef(channelId, channelName));
        }

        // Register consumer
        final java.util.concurrent.LinkedBlockingQueue<OutboundMessage> queue    = new java.util.concurrent.LinkedBlockingQueue<>();
        final java.util.function.Consumer<OutboundMessage>              consumer = queue::offer;
        a2aBackend.registerStream(corrId, consumer);

        try {
            // Re-check after registration
            final java.util.concurrent.atomic.AtomicReference<String> recheckRef = new java.util.concurrent.atomic.AtomicReference<>("submitted");
            QuarkusTransaction.requiringNew().run(() -> {
                List<Message> messages = messageService.findAllByCorrelationId(taskId);
                io.casehub.qhorus.api.message.Commitment commitment =
                        commitmentService.findByCorrelationId(taskId).orElse(null);
                String state = (commitment != null && commitment.state() != CommitmentState.OPEN)
                               ? A2ATaskStateMapper.fromCommitmentState(commitment.state())
                               : A2ATaskStateMapper.fromMessageHistory(messages);
                recheckRef.set(state);
            });
            if (A2ATaskStateMapper.TERMINAL_STATES.contains(recheckRef.get())) {
                sendStatusEvent(sink, sse, taskId, recheckRef.get());
                return;
            }

            // Keepalive loop
            long heartbeatMs = config.a2a().sse().heartbeatIntervalSeconds() * 1000L;
            long deadline    = System.currentTimeMillis() + (long) config.a2a().sse().maxDurationSeconds() * 1000L;

            try {
                while (true) {
                    if (sink.isClosed()) {break;}
                    long remaining = deadline - System.currentTimeMillis();
                    if (remaining <= 0) {break;}

                    OutboundMessage outMsg;
                    try {
                        outMsg = queue.poll(Math.min(heartbeatMs, remaining), java.util.concurrent.TimeUnit.MILLISECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }

                    if (outMsg == null) {
                        sink.send(sse.newEventBuilder().name("keepalive").data("").build());
                        continue;
                    }

                    boolean terminal = A2ATaskStateMapper.TERMINAL_TYPES.contains(outMsg.type());
                    String  state    = A2ATaskStateMapper.fromMessageType(outMsg.type());
                    String json = "{\"id\":\"%s\",\"status\":{\"state\":\"%s\"},\"final\":%b}"
                                          .formatted(taskId, state, terminal);
                    java.util.concurrent.CompletionStage<?> send = sink.send(
                            sse.newEventBuilder().name("task_status_update").data(json).build());
                    if (trackDeliveryRef.get() && outMsg.sequenceId() != null
                        && channelIdRef.get() != null && consumerMemberIdRef.get() != null) {
                        UUID   chId     = channelIdRef.get();
                        String memberId = consumerMemberIdRef.get();
                        Long   seqId    = outMsg.sequenceId();
                        QuarkusTransaction.requiringNew().run(() ->
                                                                      channelMembershipStore.updateLastDeliveredMessageId(chId, memberId, seqId));
                    }
                    if (terminal) {
                        send.toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS);
                        break;
                    }
                }
            } catch (Exception e) {
                LOG.warnf(e, "SSE stream error for task %s", taskId);
            }
        } finally {
            a2aBackend.deregisterStream(corrId, consumer);
            if (!sink.isClosed()) {sink.close();}
        }
    }

    // -----------------------------------------------------------------------
    // JSON-RPC response helpers
    // -----------------------------------------------------------------------


// -----------------------------------------------------------------------
    // Push notification config CRUD
    // -----------------------------------------------------------------------

    private Response handlePushConfigSet(JsonRpcRequest request) {
        if (!pushNotificationConfigStore.isResolvable()) {
            return Response.ok(jsonRpcErrorNode(request, JsonRpcError.METHOD_NOT_FOUND,
                                                "Push notifications not available — add casehub-qhorus-a2a-push-notification to classpath"))
                           .type(MediaType.APPLICATION_JSON).build();
        }
        JsonNode params = request.params();
        if (params == null || !params.has("taskId") || !params.has("url")) {
            return Response.ok(jsonRpcErrorNode(request, JsonRpcError.INVALID_PARAMS,
                                                "taskId and url are required")).type(MediaType.APPLICATION_JSON).build();
        }

        String taskId             = params.get("taskId").asText();
        String url                = params.get("url").asText();
        String token              = params.has("token") ? params.get("token").asText() : null;
        String authScheme         = params.has("authScheme") ? params.get("authScheme").asText() : null;
        String authCredentialsRef = params.has("authCredentialsRef") ? params.get("authCredentialsRef").asText() : null;

        UUID channelId;
        if (params.has("channelId")) {
            channelId = UUID.fromString(params.get("channelId").asText());
        } else {
            var commitment = commitmentService.findByCorrelationId(taskId).orElse(null);
            if (commitment == null) {
                return Response.ok(jsonRpcErrorNode(request, JsonRpcError.INVALID_PARAMS,
                                                    "invalid params: unknown task " + taskId)).type(MediaType.APPLICATION_JSON).build();
            }
            channelId = commitment.channelId();
        }

        var store = pushNotificationConfigStore.get();
        var existing = store.findByTaskId(taskId).stream()
                            .filter(c -> c.url().equals(url)).findFirst();
        UUID configId = existing.map(io.casehub.qhorus.api.a2a.PushNotificationConfig::id).orElse(UUID.randomUUID());

        var cfg = new io.casehub.qhorus.api.a2a.PushNotificationConfig(
                configId, taskId, channelId, url, token, authScheme, authCredentialsRef,
                currentPrincipal.tenancyId(),
                java.time.Instant.now(), null);
        store.put(cfg);

        if (pushNotificationRegistrar.isResolvable()) {
            pushNotificationRegistrar.get().onConfigCreated(channelId, taskId);
        }

        return Response.ok(jsonRpcSuccessNode(request, cfg)).type(MediaType.APPLICATION_JSON).build();
    }

    private Response handlePushConfigGet(JsonRpcRequest request) {
        if (!pushNotificationConfigStore.isResolvable()) {
            return Response.ok(jsonRpcErrorNode(request, JsonRpcError.METHOD_NOT_FOUND,
                                                "Push notifications not available")).type(MediaType.APPLICATION_JSON).build();
        }
        JsonNode params = request.params();
        if (params == null || !params.has("id")) {
            return Response.ok(jsonRpcErrorNode(request, JsonRpcError.INVALID_PARAMS,
                                                "id is required")).type(MediaType.APPLICATION_JSON).build();
        }
        UUID id    = UUID.fromString(params.get("id").asText());
        var  found = pushNotificationConfigStore.get().findById(id);
        if (found.isEmpty()) {
            return Response.ok(jsonRpcErrorNode(request, JsonRpcError.INVALID_PARAMS,
                                                "push config not found: " + id)).type(MediaType.APPLICATION_JSON).build();
        }
        return Response.ok(jsonRpcSuccessNode(request, found.get())).type(MediaType.APPLICATION_JSON).build();
    }

    private Response handlePushConfigList(JsonRpcRequest request) {
        if (!pushNotificationConfigStore.isResolvable()) {
            return Response.ok(jsonRpcErrorNode(request, JsonRpcError.METHOD_NOT_FOUND,
                                                "Push notifications not available")).type(MediaType.APPLICATION_JSON).build();
        }
        JsonNode params = request.params();
        if (params == null || !params.has("taskId")) {
            return Response.ok(jsonRpcErrorNode(request, JsonRpcError.INVALID_PARAMS,
                                                "taskId is required")).type(MediaType.APPLICATION_JSON).build();
        }
        String taskId  = params.get("taskId").asText();
        var    configs = pushNotificationConfigStore.get().findByTaskId(taskId);
        return Response.ok(jsonRpcSuccessNode(request, configs)).type(MediaType.APPLICATION_JSON).build();
    }

    private Response handlePushConfigDelete(JsonRpcRequest request) {
        if (!pushNotificationConfigStore.isResolvable()) {
            return Response.ok(jsonRpcErrorNode(request, JsonRpcError.METHOD_NOT_FOUND,
                                                "Push notifications not available")).type(MediaType.APPLICATION_JSON).build();
        }
        JsonNode params = request.params();
        if (params == null || !params.has("id")) {
            return Response.ok(jsonRpcErrorNode(request, JsonRpcError.INVALID_PARAMS,
                                                "id is required")).type(MediaType.APPLICATION_JSON).build();
        }
        UUID id = UUID.fromString(params.get("id").asText());
        pushNotificationConfigStore.get().delete(id);
        return Response.ok(jsonRpcSuccessNode(request, Map.of("deleted", true))).type(MediaType.APPLICATION_JSON).build();
    }

    private com.fasterxml.jackson.databind.node.ObjectNode jsonRpcSuccessNode(
            JsonRpcRequest request, Object result) {
        com.fasterxml.jackson.databind.node.ObjectNode node = mapper.createObjectNode();
        node.put("jsonrpc", "2.0");
        if (request != null && request.id() != null) {
            node.put("id", request.id());
        }
        node.set("result", mapper.valueToTree(result));
        return node;
    }

    private com.fasterxml.jackson.databind.node.ObjectNode jsonRpcErrorNode(
            JsonRpcRequest request,
            JsonRpcError error,
            String data) {
        com.fasterxml.jackson.databind.node.ObjectNode node = mapper.createObjectNode();
        node.put("jsonrpc", "2.0");
        if (request != null && request.id() != null) {
            node.put("id", request.id());
        }
        node.set("error", error.toJsonNode(mapper, data));
        return node;
    }

    // -----------------------------------------------------------------------
    // SSE helpers
    // -----------------------------------------------------------------------

    private static void sendStatusEvent(SseEventSink sink, Sse sse,
                                        String taskId, String state) throws Exception {
        String json = "{\"id\":\"%s\",\"status\":{\"state\":\"%s\"},\"final\":true}"
                              .formatted(taskId, state);
        try {
            sink.send(sse.newEventBuilder().name("task_status_update").data(json).build())
                .toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS);
        } finally {
            if (!sink.isClosed()) {sink.close();}
        }
    }

    private static void sendErrorEvent(SseEventSink sink, Sse sse,
                                       String taskId, String error) throws Exception {
        String id   = taskId != null ? taskId : "unknown";
        String json = "{\"id\":\"%s\",\"error\":\"%s\",\"final\":true}".formatted(id, error);
        try {
            sink.send(sse.newEventBuilder().name("error").data(json).build())
                .toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS);
        } finally {
            if (!sink.isClosed()) {sink.close();}
        }
    }
}
