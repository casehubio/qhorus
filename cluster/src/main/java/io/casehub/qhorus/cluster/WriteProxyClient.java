package io.casehub.qhorus.cluster;

import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.channel.ChannelCreateRequest;
import io.casehub.qhorus.api.message.DispatchResult;
import io.casehub.qhorus.api.message.MessageDispatch;

import java.util.UUID;

public class WriteProxyClient {

    private static final org.jboss.logging.Logger LOG = org.jboss.logging.Logger.getLogger(WriteProxyClient.class);

    private final java.net.http.HttpClient                    httpClient;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final java.time.Duration                          timeout;

    public WriteProxyClient(java.time.Duration timeout) {
        this.httpClient   = java.net.http.HttpClient.newBuilder()
                                                    .connectTimeout(timeout)
                                                    .build();
        this.objectMapper = new com.fasterxml.jackson.databind.ObjectMapper()
                                    .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                                    .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.timeout      = timeout;
    }

    WriteProxyClient() {
        this(java.time.Duration.ofSeconds(10));
    }

    WriteProxyClient(java.net.http.HttpClient httpClient,
                     com.fasterxml.jackson.databind.ObjectMapper objectMapper,
                     java.time.Duration timeout) {
        this.httpClient   = httpClient;
        this.objectMapper = objectMapper;
        this.timeout      = timeout;
    }

    public DispatchResult dispatch(NodeInfo target, MessageDispatch dispatch) {
        InternalDispatchRequest body = InternalDispatchRequest.from(dispatch);
        return post(target, "/internal/dispatch", body, DispatchResult.class);
    }

    public Channel createChannel(NodeInfo target, ChannelCreateRequest request) {
        return post(target, "/internal/channel", request, Channel.class);
    }

    public long deleteChannel(NodeInfo target, UUID channelId, boolean force) {
        String path = "/internal/channel/" + channelId + "/delete?force=" + force;
        return post(target, path, null, Long.class);
    }

    public Channel pauseChannel(NodeInfo target, UUID channelId) {
        return post(target, path("/internal/channel/" + channelId + "/pause"), null, Channel.class);
    }

    public Channel resumeChannel(NodeInfo target, UUID channelId) {
        return post(target, path("/internal/channel/" + channelId + "/resume"), null, Channel.class);
    }

    private <T> T post(NodeInfo target, String path, Object body, Class<T> responseType) {
        try {
            String url = "http://" + target.address() + path;
            java.net.http.HttpRequest.Builder reqBuilder = java.net.http.HttpRequest.newBuilder()
                                                                                    .uri(java.net.URI.create(url))
                                                                                    .timeout(timeout)
                                                                                    .header("Content-Type", "application/json");
            if (body != null) {
                reqBuilder.POST(java.net.http.HttpRequest.BodyPublishers.ofString(
                        objectMapper.writeValueAsString(body)));
            } else {
                reqBuilder.POST(java.net.http.HttpRequest.BodyPublishers.noBody());
            }
            java.net.http.HttpResponse<String> response = httpClient.send(
                    reqBuilder.build(),
                    java.net.http.HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new RuntimeException("HTTP " + response.statusCode() + " from " + url);
            }
            if (responseType == Void.class || response.body() == null || response.body().isEmpty()) {
                return null;
            }
            return objectMapper.readValue(response.body(), responseType);
        } catch (Exception e) {
            throw new RuntimeException("Proxy call to " + target.nodeId() + " failed: " + e.getMessage(), e);
        }
    }

    private static String path(String p) {
        return p;
    }
}
