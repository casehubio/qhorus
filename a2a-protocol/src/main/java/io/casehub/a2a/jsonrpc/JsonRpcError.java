package io.casehub.a2a.jsonrpc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public enum JsonRpcError {
    PARSE_ERROR(-32700, "Parse error"),
    INVALID_REQUEST(-32600, "Invalid Request"),
    METHOD_NOT_FOUND(-32601, "Method not found"),
    INVALID_PARAMS(-32602, "Invalid params"),
    INTERNAL_ERROR(-32603, "Internal error");

    private final int code;
    private final String message;

    JsonRpcError(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int code() {
        return code;
    }

    public String message() {
        return message;
    }

    public ObjectNode toJsonNode(ObjectMapper mapper) {
        return toJsonNode(mapper, null);
    }

    public ObjectNode toJsonNode(ObjectMapper mapper, String data) {
        ObjectNode node = mapper.createObjectNode();
        node.put("code", code);
        node.put("message", message);
        if (data != null) {
            node.put("data", data);
        }
        return node;
    }
}
