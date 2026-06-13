package io.casehub.qhorus.runtime.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseEventSink;

import io.casehub.qhorus.api.gateway.OutboundMessage;

import io.casehub.qhorus.api.gateway.ChannelRef;
import io.casehub.qhorus.api.message.CommitmentState;
import io.casehub.qhorus.runtime.channel.Channel;
import io.casehub.qhorus.runtime.channel.ChannelService;
import io.casehub.qhorus.runtime.config.QhorusConfig;
import io.casehub.qhorus.runtime.message.Commitment;
import io.casehub.qhorus.runtime.message.CommitmentService;
import io.casehub.qhorus.runtime.message.Message;
import io.casehub.qhorus.runtime.message.MessageService;
import io.quarkus.arc.properties.UnlessBuildProperty;

/**
 * Optional A2A-compatible REST endpoint layer.
 *
 * <p>
 * When {@code casehub.qhorus.a2a.enabled=true}, exposes two endpoints that let external
 * A2A orchestrators delegate tasks to Qhorus without knowing it is an MCP server:
 * <ul>
 * <li>{@code POST /a2a/message:send} — thin adapter delegating to {@link A2AChannelBackend}</li>
 * <li>{@code GET /a2a/tasks/{id}} — returns A2A Task status via CommitmentStore lookup,
 *     falling back to message-history via A2ATaskState.fromMessageHistory; always includes history</li>
 * </ul>
 *
 * <p>
 * Both endpoints return HTTP 501 Not Implemented when the flag is off, protecting
 * existing deployments from unintended exposure.
 *
 * @see <a href="https://google.github.io/A2A/">Google A2A Protocol</a>
 */
@UnlessBuildProperty(name = "casehub.qhorus.reactive.enabled", stringValue = "true", enableIfMissing = true)
@Path("/a2a")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
public class A2AResource {

    private static final Response A2A_DISABLED = Response
            .status(Response.Status.NOT_IMPLEMENTED)
            .entity("{\"error\":\"A2A endpoint is disabled. Set casehub.qhorus.a2a.enabled=true to activate.\"}")
            .type(MediaType.APPLICATION_JSON)
            .build();

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

    @POST
    @Path("/message:send")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response sendMessage(SendMessageRequest request, @Context HttpHeaders headers) {
        if (!config.a2a().enabled()) {
            return A2A_DISABLED;
        }

        // Validate inbound request
        if (request == null || request.message() == null) {
            return error400("message is required");
        }
        A2AMessage msg = request.message();

        if (msg.contextId() == null || msg.contextId().isBlank()) {
            return error400("message.contextId (channel name) is required");
        }
        if (msg.parts() == null || msg.parts().isEmpty()) {
            return error400("message.parts must contain at least one text part");
        }
        String text = msg.parts().stream()
                .filter(p -> "text".equals(p.kind()) && p.text() != null)
                .map(A2APart::text)
                .findFirst()
                .orElse(null);
        if (text == null) {
            return error400("message.parts must contain at least one text part with kind=text");
        }

        // Look up channel
        Channel channel = channelService.findByName(msg.contextId()).orElse(null);
        if (channel == null) {
            return error400("channel not found: " + msg.contextId());
        }

        // Register A2A backend on this channel (idempotent)
        a2aBackend.ensureRegistered(channel.id, new ChannelRef(channel.id, channel.name));

        // Extract actor-type override header
        String actorTypeHeader = headers.getHeaderString("x-qhorus-actor-type");

        // Delegate to backend — gets full pipeline (type resolution, ledger, commitment)
        Map<String, String> metadata = msg.metadata() != null ? msg.metadata() : Map.of();
        String taskId = (msg.taskId() != null && !msg.taskId().isBlank())
                ? msg.taskId()
                : UUID.randomUUID().toString();

        try {
            a2aBackend.receive(msg.contextId(), msg.role(), text, taskId, metadata, actorTypeHeader);
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            return error400(cause.getMessage());
        }

        Task task = new Task(taskId, msg.contextId(), new TaskStatus("submitted"), null);
        return Response.ok(new SendMessageResponse(task)).build();
    }

    /**
     * Returns the A2A task status for the given task ID.
     *
     * <p><strong>Tenant asymmetry hazard:</strong> messages are stored with the tenant in effect
     * when they were sent (from {@code X-Tenancy-ID} header or default tenant). This endpoint
     * filters results by the tenant in effect at query time. Calling without {@code X-Tenancy-ID}
     * when the task was sent with one, or with a different header value, returns HTTP 404 even
     * though the task exists — it just exists in a different tenant bucket.
     *
     * <p>Always include {@code X-Tenancy-ID} consistently on both
     * {@code POST /a2a/message:send} and {@code GET /a2a/tasks/{id}} for the same task.
     */
    @GET
    @Path("/tasks/{id}")
    @Transactional
    public Response getTask(@PathParam("id") String taskId) {
        if (!config.a2a().enabled()) {
            return A2A_DISABLED;
        }

        // Get all messages (needed for history AND as fallback for state)
        List<Message> messages = messageService.findAllByCorrelationId(taskId);
        if (messages.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse("Task not found: " + taskId))
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }

        Channel channel = channelService.findById(messages.get(0).channelId)
                .orElseThrow(() -> new IllegalStateException("Channel not found for task " + taskId));

        // Determine state: CommitmentStore for non-OPEN states (terminal/acknowledged give
        // definitive results); fall back to message history for OPEN commitments and the
        // no-commitment case (message history is more informative, e.g. HANDOFF → "working").
        Commitment commitment = commitmentService.findByCorrelationId(taskId).orElse(null);
        String state = (commitment != null && commitment.state != CommitmentState.OPEN)
                ? A2ATaskState.fromCommitmentState(commitment.state)
                : A2ATaskState.fromMessageHistory(messages);

        // Build history — ALWAYS include (existing tests depend on this)
        List<A2AMessage> history = messages.stream()
                .map(m -> new A2AMessage(
                        m.sender,
                        m.content != null ? List.of(new A2APart("text", m.content)) : List.of(),
                        null,
                        m.correlationId,
                        channel.name,
                        null))
                .toList();

        return Response.ok(new Task(taskId, channel.name, new TaskStatus(state), history)).build();
    }

    /**
     * SSE stream endpoint — pushes {@code task_status_update} events as messages arrive
     * on the task's channel, then closes when a terminal type is received.
     *
     * <p><strong>Thread model:</strong> Quarkus dispatches this method on a virtual thread
     * (blocking-capable). This is load-bearing: {@code @Transactional} works correctly here
     * because virtual threads can block on JTA/JDBC without stalling the Vert.x I/O thread.
     * The {@link SseEventSink} is held open after the method returns — subsequent SSE events
     * are pushed from the virtual thread that executes {@link A2AChannelBackend#post} via
     * {@link io.casehub.qhorus.runtime.gateway.ChannelGateway#fanOut}.
     *
     * <p><strong>Immediate-close paths:</strong> A2A disabled, invalid task ID, task not
     * found, and already-terminal tasks all send a single event and close immediately.
     *
     * <p><strong>{@code @Transactional} semantics:</strong> the initial CommitmentStore
     * and message-history reads are atomic (same requirement as {@link #getTask}). With
     * a void SSE method, the transaction commits when the method body returns — before the
     * sink stays open. Events then flow without a transaction.
     *
     * <p><strong>Timing note:</strong> {@link io.casehub.qhorus.runtime.gateway.ChannelGateway#fanOut}
     * dispatches {@link A2AChannelBackend#post} on a virtual thread inside the enclosing
     * {@code @Transactional} dispatch. The DB may not have committed when the SSE event fires.
     * Clients must treat SSE events as notification triggers, not consistency guarantees.
     *
     * <p><strong>Server restart:</strong> SSE subscriptions do not survive restarts.
     * Clients must re-subscribe after a restart. A keepalive/timeout mechanism is tracked
     * in qhorus#278.
     */
    @GET
    @Path("/tasks/{id}/stream")
    @Produces("text/event-stream")
    @Transactional
    public void streamTask(
            @PathParam("id") final String taskId,
            @Context final SseEventSink sink,
            @Context final Sse sse) {

        if (!config.a2a().enabled()) {
            sendErrorEvent(sink, sse, taskId, "A2A endpoint is disabled");
            return;
        }

        UUID corrId;
        try {
            corrId = UUID.fromString(taskId);
        } catch (final IllegalArgumentException e) {
            sendErrorEvent(sink, sse, taskId, "Invalid task ID format — expected UUID");
            return;
        }

        final List<Message> messages = messageService.findAllByCorrelationId(taskId);
        if (messages.isEmpty()) {
            sendErrorEvent(sink, sse, taskId, "Task not found: " + taskId);
            return;
        }

        // Already terminal? Send immediate final event and close — no dangling connection.
        final Commitment commitment = commitmentService.findByCorrelationId(taskId).orElse(null);
        final String currentState = (commitment != null && commitment.state != CommitmentState.OPEN)
                ? A2ATaskState.fromCommitmentState(commitment.state)
                : A2ATaskState.fromMessageHistory(messages);

        if ("completed".equals(currentState) || "failed".equals(currentState)
                || "cancelled".equals(currentState)) {
            sendStatusEvent(sink, sse, taskId, currentState);
            return;
        }

        // Task is in-progress — register a consumer and keep the sink open.
        // AtomicReference allows the consumer lambda to reference itself for deregistration.
        final AtomicReference<Consumer<OutboundMessage>> ref = new AtomicReference<>();
        final Consumer<OutboundMessage> consumer = msg -> {
            if (sink.isClosed()) {
                a2aBackend.deregisterStream(corrId, ref.get());
                return;
            }
            final boolean terminal = A2ATaskState.TERMINAL_TYPES.contains(msg.type());
            final String state = A2ATaskState.fromMessageType(msg.type());
            final String json = """
                    {"id":"%s","status":{"state":"%s"},"final":%b}""".formatted(taskId, state, terminal);
            // sink.send() is async (CompletionStage) — chain close and deregister after it completes.
            // whenComplete handles both send-success (terminal → close) and send-failure (deregister).
            // Never call sink.close() synchronously after send() — the response may not yet be written.
            sink.send(sse.newEventBuilder().name("task_status_update").data(json).build())
                    .whenComplete((v, ex) -> {
                        if (ex != null) {
                            // Broken pipe or I/O error — deregister so future post() calls skip us
                            a2aBackend.deregisterStream(corrId, ref.get());
                        } else if (terminal) {
                            // Send succeeded — close sink and deregister
                            if (!sink.isClosed()) sink.close();
                            a2aBackend.deregisterStream(corrId, ref.get());
                        }
                    });
        };
        ref.set(consumer);
        a2aBackend.registerStream(corrId, consumer);
        // Transaction commits here (method returns) — sink stays open for event-driven updates
    }

    /**
     * Sends a terminal status event and chains {@link SseEventSink#close()} asynchronously
     * after the send completes. Never calls close synchronously — that causes
     * {@code IllegalStateException: Response has already been written} in RESTEasy Reactive
     * when the async write hasn't finished.
     */
    private static void sendStatusEvent(final SseEventSink sink, final Sse sse,
            final String taskId, final String state) {
        final String json = """
                {"id":"%s","status":{"state":"%s"},"final":true}""".formatted(taskId, state);
        sink.send(sse.newEventBuilder().name("task_status_update").data(json).build())
                .thenRun(() -> { if (!sink.isClosed()) sink.close(); });
    }

    /**
     * Sends an error event and chains {@link SseEventSink#close()} asynchronously.
     * HTTP 200 is returned with {@code text/event-stream} content type. This is deliberate:
     * SSE void methods cannot return a different HTTP status code. The {@code event:error}
     * event type allows clients to distinguish this from status updates.
     *
     * <p>Note: POST /a2a/message:send and GET /a2a/tasks/{id} return HTTP 501 when A2A is
     * disabled. This endpoint returns HTTP 200 + {@code event:error} — an intentional
     * difference dictated by the JAX-RS void SSE method constraint.
     */
    private static void sendErrorEvent(final SseEventSink sink, final Sse sse,
            final String taskId, final String error) {
        final String json = """
                {"id":"%s","error":"%s","final":true}""".formatted(taskId, error);
        sink.send(sse.newEventBuilder().name("error").data(json).build())
                .thenRun(() -> { if (!sink.isClosed()) sink.close(); });
    }

    private static Response error400(final String message) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse(message))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }

    // -----------------------------------------------------------------------
    // A2A request / response model records
    // -----------------------------------------------------------------------

    /** Inbound A2A SendMessageRequest body. */
    public record SendMessageRequest(String id, A2AMessage message) {
    }

    /** A2A Message as defined by the A2A protocol. */
    public record A2AMessage(
            String role,
            java.util.List<A2APart> parts,
            String messageId,
            String taskId,
            String contextId,
            java.util.Map<String, String> metadata) {
    }

    /** A2A content part — only text kind is supported. */
    public record A2APart(String kind, String text) {
    }

    /** A2A Task returned by send and get endpoints. */
    public record Task(String id, String contextId, TaskStatus status,
            java.util.List<A2AMessage> history) {
    }

    /** A2A TaskStatus — state is one of: submitted, working, completed, failed. */
    public record TaskStatus(String state) {
    }

    /** Top-level response wrapper for the send endpoint. */
    public record SendMessageResponse(Task task) {
    }
}
