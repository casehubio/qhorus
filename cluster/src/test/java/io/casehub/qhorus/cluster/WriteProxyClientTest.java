package io.casehub.qhorus.cluster;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.qhorus.api.message.DispatchResult;
import io.casehub.qhorus.api.message.MessageDispatch;
import io.casehub.qhorus.api.message.MessageType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class WriteProxyClientTest {

    private HttpClient httpClient;
    private WriteProxyClient proxyClient;
    private ObjectMapper objectMapper;
    private static final NodeInfo TARGET = new NodeInfo("node-2", "node-2:8080");
    private static final UUID CHANNEL_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        httpClient = mock(HttpClient.class);
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        proxyClient = new WriteProxyClient(httpClient, objectMapper, Duration.ofSeconds(5));
    }

    @Test
    void dispatchSendsPostAndDeserializesResult() throws Exception {
        DispatchResult expected = new DispatchResult(
                1L, CHANNEL_ID, "agent-1", MessageType.STATUS,
                null, null, List.of(), null, null, null, null, 0, null, List.of());
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(objectMapper.writeValueAsString(expected));
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        MessageDispatch dispatch = MessageDispatch.builder()
                .channelId(CHANNEL_ID)
                .sender("agent-1")
                .type(MessageType.STATUS)
                .content("test")
                .actorType(ActorType.AGENT)
                .build();

        DispatchResult result = proxyClient.dispatch(TARGET, dispatch);

        assertThat(result.messageId()).isEqualTo(1L);
        assertThat(result.sender()).isEqualTo("agent-1");
    }

    @Test
    void dispatchThrowsProxyDispatchExceptionOnHttpError() throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(500);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        MessageDispatch dispatch = MessageDispatch.builder()
                                                  .channelId(CHANNEL_ID)
                                                  .sender("agent-1")
                                                  .type(MessageType.STATUS)
                                                  .content("test")
                                                  .actorType(ActorType.AGENT)
                                                  .build();

        assertThatThrownBy(() -> proxyClient.dispatch(TARGET, dispatch))
                .isInstanceOf(ProxyDispatchException.class)
                .hasMessageContaining("HTTP 500");
    }

    @Test
    void dispatchThrowsProxyTimeoutOnConnectionFailure() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new java.io.IOException("Connection refused"));

        MessageDispatch dispatch = MessageDispatch.builder()
                                                  .channelId(CHANNEL_ID)
                                                  .sender("agent-1")
                                                  .type(MessageType.STATUS)
                                                  .content("test")
                                                  .actorType(ActorType.AGENT)
                                                  .build();

        assertThatThrownBy(() -> proxyClient.dispatch(TARGET, dispatch))
                .isInstanceOf(ProxyTimeoutException.class)
                .hasMessageContaining("Proxy call to node-2 failed");
    }

    @Test
    void dispatchThrowsProxyAuthExceptionOn401() throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(401);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        MessageDispatch dispatch = MessageDispatch.builder()
                                                  .channelId(CHANNEL_ID)
                                                  .sender("agent-1")
                                                  .type(MessageType.STATUS)
                                                  .content("test")
                                                  .actorType(ActorType.AGENT)
                                                  .build();

        assertThatThrownBy(() -> proxyClient.dispatch(TARGET, dispatch))
                .isInstanceOf(ProxyAuthException.class)
                .hasMessageContaining("Authentication rejected");
    }

    @Test
    void dispatchThrowsProxyAuthExceptionOn403() throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(403);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        MessageDispatch dispatch = MessageDispatch.builder()
                                                  .channelId(CHANNEL_ID)
                                                  .sender("agent-1")
                                                  .type(MessageType.STATUS)
                                                  .content("test")
                                                  .actorType(ActorType.AGENT)
                                                  .build();

        assertThatThrownBy(() -> proxyClient.dispatch(TARGET, dispatch))
                .isInstanceOf(ProxyAuthException.class);
    }


    @Test
    void primaryConstructorCreatesWorkingClient() {
        WriteProxyClient client = new WriteProxyClient(Duration.ofSeconds(10), null);
        assertThat(client).isNotNull();
    }
}