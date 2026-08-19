package io.casehub.a2a.client;

import java.util.concurrent.ConcurrentHashMap;

public class A2AClientRegistry {

    private final ConcurrentHashMap<String, A2AClient> clients = new ConcurrentHashMap<>();

    public A2AClient getOrCreate(String endpoint, AuthConfig auth) {
        String key = normalizeEndpoint(endpoint);
        return clients.computeIfAbsent(key, k -> new A2AClient(k, auth));
    }

    public void evict(String endpoint) {
        String key = normalizeEndpoint(endpoint);
        A2AClient removed = clients.remove(key);
        if (removed != null) {
            removed.close();
        }
    }

    public void shutdown() {
        clients.values().forEach(A2AClient::close);
        clients.clear();
    }

    private String normalizeEndpoint(String endpoint) {
        return endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
    }
}
