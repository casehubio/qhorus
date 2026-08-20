package io.casehub.a2a.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.casehub.a2a.model.A2AArtifact;
import io.casehub.a2a.model.A2AMessage;
import io.casehub.a2a.model.A2ATask;
import io.casehub.a2a.model.A2ATaskState;
import io.casehub.a2a.model.A2ATaskStatus;
import io.casehub.a2a.model.AgentCard;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class A2AClient implements AutoCloseable {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String endpoint;
    private final AuthConfig auth;
    private final HttpClient httpClient;
    private final AtomicInteger requestIdCounter = new AtomicInteger(1);

    public A2AClient(String endpoint, AuthConfig auth) {
        this.endpoint = endpoint.endsWith("/") ? endpoint : endpoint + "/";
        this.auth = auth;
        this.httpClient = HttpClient.newBuilder().build();
    }

    public A2ATask send(A2AMessage message, String contextId)
            throws IOException, InterruptedException {
        ObjectNode request = buildJsonRpcRequest("message/send", message, contextId);
        HttpRequest httpRequest = buildHttpRequest(request);
        HttpResponse<String> response =
            httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        return handleResponse(response);
    }

    public Stream<A2ATask> stream(A2AMessage message, String contextId)
            throws IOException, InterruptedException {
        ObjectNode request = buildJsonRpcRequest("message/send", message, contextId);
        HttpRequest httpRequest = buildHttpRequest(request);
        HttpResponse<Stream<String>> response =
            httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofLines());
        if (response.statusCode() >= 400) {
            response.body().close();
            throw new IOException("HTTP " + response.statusCode() + " from " + endpoint);
        }
        return response.body()
            .filter(line -> line.startsWith("data: "))
            .map(line -> line.substring(6))
            .map(A2AClient::parseTaskEvent)
            .filter(t -> t != null);
    }

    public void cancel(String taskId) {
        try {
            ObjectNode root = MAPPER.createObjectNode();
            root.put("jsonrpc", "2.0");
            root.put("id", String.valueOf(requestIdCounter.getAndIncrement()));
            root.put("method", "tasks/cancel");
            root.putObject("params").put("id", taskId);
            HttpRequest request = buildHttpRequest(root);
            httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            // fire-and-forget
        }
    }

    public AgentCard fetchAgentCard() {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(endpoint + ".well-known/agent.json"))
                .GET()
                .timeout(java.time.Duration.ofSeconds(5));
            applyAuth(builder);
            HttpResponse<String> response =
                httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return MAPPER.readValue(response.body(), AgentCard.class);
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    public boolean checkHealth() {
        return fetchAgentCard() != null;
    }

    @Override
    public void close() {httpClient.close();}

    private ObjectNode buildJsonRpcRequest(String method, A2AMessage message, String contextId) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("jsonrpc", "2.0");
        root.put("id", String.valueOf(requestIdCounter.getAndIncrement()));
        root.put("method", method);

        ObjectNode params = root.putObject("params");
        params.set("message", MAPPER.valueToTree(message));
        if (contextId != null) {
            params.put("contextId", contextId);
        }
        return root;
    }

    private HttpRequest buildHttpRequest(ObjectNode body) throws JsonProcessingException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create(endpoint + "a2a"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)));
        applyAuth(builder);
        return builder.build();
    }

    private void applyAuth(HttpRequest.Builder builder) {
        if (auth.type() == AuthConfig.AuthType.NONE) {
            return;
        }
        String token = resolveToken();
        if (token == null) return;
        switch (auth.type()) {
            case BEARER -> builder.header("Authorization", "Bearer " + token);
            case API_KEY -> builder.header("X-API-Key", token);
            default -> {}
        }
    }

    private String resolveToken() {
        if (auth.resolvedToken() != null) {return auth.resolvedToken();}
        if (auth.tokenConfigKey() == null) {return null;}
        return System.getProperty(auth.tokenConfigKey(), System.getenv(auth.tokenConfigKey()));}

    private A2ATask handleResponse(HttpResponse<String> response) throws IOException {
        int status = response.statusCode();
        if (status >= 400) {
            throw new IOException("HTTP " + status + " from " + endpoint);
        }
        JsonNode root = MAPPER.readTree(response.body());
        JsonNode error = root.get("error");
        if (error != null) {
            String msg = error.has("message") ? error.get("message").asText() : "JSON-RPC error";
            throw new IOException("JSON-RPC error " + error.get("code").asInt() + ": " + msg);
        }
        return parseTaskFromResult(root.get("result"));
    }

    private A2ATask parseTaskFromResult(JsonNode result) {
        if (result == null) return null;
        String id = result.has("id") ? result.get("id").asText() : null;
        String contextId = result.has("contextId") ? result.get("contextId").asText() : null;
        A2ATaskStatus status = null;
        if (result.has("status")) {
            JsonNode statusNode = result.get("status");
            A2ATaskState state = A2ATaskState.fromWireValue(statusNode.get("state").asText());
            status = new A2ATaskStatus(state, null);
        }
        List<A2AArtifact> artifacts = List.of();
        return new A2ATask(id, contextId, status, artifacts, List.of());
    }

    private static A2ATask parseTaskEvent(String json) {
        try {
            JsonNode node = MAPPER.readTree(json);
            String id = node.has("id") ? node.get("id").asText() : null;
            A2ATaskStatus status = null;
            if (node.has("status")) {
                JsonNode statusNode = node.get("status");
                A2ATaskState state = A2ATaskState.fromWireValue(statusNode.get("state").asText());
                status = new A2ATaskStatus(state, null);
            }
            return new A2ATask(id, null, status, List.of(), List.of());
        } catch (Exception e) {
            return null;
        }
    }
}
