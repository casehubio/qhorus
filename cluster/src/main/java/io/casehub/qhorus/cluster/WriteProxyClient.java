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
    private final String                                      internalSecret;

    public WriteProxyClient(java.time.Duration timeout, String internalSecret) {
        this.httpClient   = java.net.http.HttpClient.newBuilder()
                                                    .connectTimeout(timeout)
                                                    .build();
        this.objectMapper = new com.fasterxml.jackson.databind.ObjectMapper()
                                    .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                                    .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.timeout      = timeout;
        this.internalSecret = internalSecret;
    }

    WriteProxyClient(java.net.http.HttpClient httpClient,
                     com.fasterxml.jackson.databind.ObjectMapper objectMapper,
                     java.time.Duration timeout) {
        this.httpClient   = httpClient;
        this.objectMapper = objectMapper;
        this.timeout      = timeout;
        this.internalSecret = null;
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
        return post(target, "/internal/channel/" + channelId + "/pause", null, Channel.class);
    }

    public Channel resumeChannel(NodeInfo target, UUID channelId) {
        return post(target, "/internal/channel/" + channelId + "/resume", null, Channel.class);
    }

    public Channel channelConfig(NodeInfo target, UUID channelId, ChannelConfigRequest request) {
        return post(target, "/internal/channel/" + channelId + "/config", request, Channel.class);
    }


    public HeartbeatResponse heartbeat(NodeInfo target) {
        return get(target, "/internal/heartbeat", HeartbeatResponse.class);
    }

    public void sendLeave(NodeInfo target, String localNodeId) {
        post(target, "/internal/leave", new LeaveRequest(localNodeId), Void.class);
    }

    private <T> T get(NodeInfo target, String path, Class<T> responseType) {
        try {
            String url = "http://" + target.address() + path;
            java.net.http.HttpRequest.Builder reqBuilder = java.net.http.HttpRequest.newBuilder()
                                                                                    .uri(java.net.URI.create(url))
                                                                                    .timeout(timeout)
                                                                                    .GET();
            if (internalSecret != null) {
                reqBuilder.header("X-Internal-Secret", internalSecret);
            }
            java.net.http.HttpRequest req = reqBuilder.build();
            java.net.http.HttpResponse<String> response = httpClient.send(
                    req, java.net.http.HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 401 || response.statusCode() == 403) {
                throw new ProxyAuthException(target.nodeId(), response.statusCode());
            }
            if (response.statusCode() >= 400) {
                throw new ProxyDispatchException(target.nodeId(), response.statusCode(),
                                                 "HTTP " + response.statusCode() + " from " + url);
            }
            return objectMapper.readValue(response.body(), responseType);
        } catch (ProxyAuthException | ProxyDispatchException e) {
            throw e;
        } catch (java.io.IOException | InterruptedException e) {
            throw new ProxyTimeoutException(
                    "GET " + target.nodeId() + " failed: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new ProxyDispatchException(target.nodeId(), -1,
                                             "GET " + target.nodeId() + " failed: " + e.getMessage());
        }
    }


    private <T> T post(NodeInfo target, String path, Object body, Class<T> responseType) {
        try {
            String url = "http://" + target.address() + path;
            java.net.http.HttpRequest.Builder reqBuilder = java.net.http.HttpRequest.newBuilder()
                                                                                    .uri(java.net.URI.create(url))
                                                                                    .timeout(timeout)
                                                                                    .header("Content-Type", "application/json");
            if (internalSecret != null) {
                reqBuilder.header("X-Internal-Secret", internalSecret);
            }
            if (body != null) {
                reqBuilder.POST(java.net.http.HttpRequest.BodyPublishers.ofString(
                        objectMapper.writeValueAsString(body)));
            } else {
                reqBuilder.POST(java.net.http.HttpRequest.BodyPublishers.noBody());
            }
            java.net.http.HttpResponse<String> response = httpClient.send(
                    reqBuilder.build(),
                    java.net.http.HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 401 || response.statusCode() == 403) {
                throw new ProxyAuthException(target.nodeId(), response.statusCode());
            }
            if (response.statusCode() >= 400) {
                throw new ProxyDispatchException(target.nodeId(), response.statusCode(),
                                                 "HTTP " + response.statusCode() + " from " + url);
            }
            if (responseType == Void.class || response.body() == null || response.body().isEmpty()) {
                return null;
            }
            return objectMapper.readValue(response.body(), responseType);
        } catch (ProxyAuthException | ProxyDispatchException e) {
            throw e;
        } catch (java.io.IOException | InterruptedException e) {
            throw new ProxyTimeoutException(
                    "Proxy call to " + target.nodeId() + " failed: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new ProxyDispatchException(target.nodeId(), -1,
                                             "Proxy call to " + target.nodeId() + " failed: " + e.getMessage());
        }
    }

}
