package io.casehub.qhorus.webhook;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class WebhookRegistry {

    private final ConcurrentHashMap<UUID, Set<WebhookRegistration>> channelHooks = new ConcurrentHashMap<>();
    private final Set<WebhookRegistration> globalHooks = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<UUID, WebhookRegistration> byId = new ConcurrentHashMap<>();

    public WebhookRegistration register(UUID channelId, String url, String secret, Map<String, String> headers) {
        WebhookRegistration reg = new WebhookRegistration(UUID.randomUUID(), channelId, url, secret, headers);
        byId.put(reg.id(), reg);
        if (channelId == null) {
            globalHooks.add(reg);
        } else {
            channelHooks.computeIfAbsent(channelId, k -> ConcurrentHashMap.newKeySet()).add(reg);
        }
        return reg;
    }

    public boolean deregister(UUID registrationId) {
        WebhookRegistration reg = byId.remove(registrationId);
        if (reg == null) return false;
        if (reg.channelId() == null) {
            globalHooks.remove(reg);
        } else {
            channelHooks.computeIfPresent(reg.channelId(), (k, hooks) -> {
                hooks.remove(reg);
                return hooks.isEmpty() ? null : hooks;
            });
        }
        return true;
    }

    public Set<WebhookRegistration> findForChannel(UUID channelId) {
        Set<WebhookRegistration> result = new HashSet<>(globalHooks);
        Set<WebhookRegistration> specific = channelHooks.get(channelId);
        if (specific != null) {
            result.addAll(specific);
        }
        return result;
    }

    public Set<WebhookRegistration> findByChannelId(UUID channelId) {
        return channelHooks.getOrDefault(channelId, Set.of());
    }

    public Collection<WebhookRegistration> listAll() {
        return byId.values();
    }
}
