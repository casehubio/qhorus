package io.casehub.a2a.jsonrpc;

import com.fasterxml.jackson.databind.JsonNode;

public record JsonRpcResponse(String jsonrpc, String id, JsonNode result, JsonNode error) {

    public boolean isError() {
        return error != null;
    }

    public int errorCode() {
        if (error == null || !error.has("code")) {
            return 0;
        }
        return error.get("code").asInt();
    }

    public String errorMessage() {
        if (error == null || !error.has("message")) {
            return null;
        }
        return error.get("message").asText();
    }

    public String errorData() {
        if (error == null || !error.has("data")) {
            return null;
        }
        return error.get("data").asText();
    }
}
