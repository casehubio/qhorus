package io.casehub.a2a.jsonrpc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public record JsonRpcRequest(String jsonrpc, String id, String method, JsonNode params) {

    public static Builder builder(String method) {
        return new Builder(method);
    }

    public static final class Builder {
        private final String method;
        private String id;
        private final ObjectMapper mapper = new ObjectMapper();
        private ObjectNode params;

        private Builder(String method) {
            this.method = method;
            this.params = mapper.createObjectNode();
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder param(String key, Object value) {
            params.set(key, mapper.valueToTree(value));
            return this;
        }

        public Builder params(ObjectNode params) {
            this.params = params;
            return this;
        }

        public ObjectNode toJsonNode() {
            ObjectNode root = mapper.createObjectNode();
            root.put("jsonrpc", "2.0");
            if (id != null) {
                root.put("id", id);
            }
            root.put("method", method);
            root.set("params", params);
            return root;
        }

        public JsonRpcRequest build() {
            return new JsonRpcRequest("2.0", id, method, params);
        }
    }
}
