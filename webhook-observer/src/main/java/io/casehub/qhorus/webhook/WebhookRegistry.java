package io.casehub.qhorus.webhook;

import io.casehub.qhorus.api.gateway.ChannelClosedEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@ApplicationScoped
public class WebhookRegistry {

    private static final Logger LOG = Logger.getLogger(WebhookRegistry.class);

    private final ConcurrentHashMap<UUID, Set<WebhookRegistration>>   channelHooks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<WebhookRegistration>> globalHooks  = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, WebhookRegistration>        byId         = new ConcurrentHashMap<>();

    @Inject
    WebhookRegistrationStore store;

    WebhookRegistry() {}

    void onStart(@Observes StartupEvent ev) {
        for (WebhookRegistrationEntity e : store.findAll()) {
            registerInMemory(e.channelId, e.tenancyId, e.url, e.secretRef,
                             e.headers == null ? Map.of() : e.headers, e.id, e.createdAt);
        }
        LOG.infof("Loaded %d webhook registration(s) from database", byId.size());
    }

    public WebhookRegistration register(UUID channelId, String tenancyId, String url,
                                        String secretRef, Map<String, String> headers) {
        var entity = new WebhookRegistrationEntity();
        entity.id        = UUID.randomUUID();
        entity.channelId = channelId;
        entity.url       = url;
        entity.secretRef = secretRef;
        entity.headers   = headers;
        entity.tenancyId = tenancyId;
        entity.createdAt = Instant.now();
        store.save(entity);
        return registerInMemory(channelId, tenancyId, url, secretRef, headers, entity.id, entity.createdAt);
    }

    WebhookRegistration registerInMemory(UUID channelId, String tenancyId, String url,
                                         String secretRef, Map<String, String> headers) {
        return registerInMemory(channelId, tenancyId, url, secretRef, headers,
                                UUID.randomUUID(), Instant.now());
    }

    private WebhookRegistration registerInMemory(UUID channelId, String tenancyId, String url,
                                                 String secretRef, Map<String, String> headers,
                                                 UUID id, Instant createdAt) {
        var reg = new WebhookRegistration(id, channelId, tenancyId, url, secretRef, headers, createdAt);
        byId.put(reg.id(), reg);
        if (channelId == null) {
            globalHooks.computeIfAbsent(tenancyId, k -> ConcurrentHashMap.newKeySet()).add(reg);
        } else {
            channelHooks.computeIfAbsent(channelId, k -> ConcurrentHashMap.newKeySet()).add(reg);
        }
        return reg;
    }

    public boolean deregister(UUID registrationId) {
        store.delete(registrationId);
        return deregisterInMemory(registrationId);
    }

    boolean deregisterInMemory(UUID registrationId) {
        WebhookRegistration reg = byId.remove(registrationId);
        if (reg == null) {return false;}
        if (reg.channelId() == null) {
            globalHooks.computeIfPresent(reg.tenancyId(), (k, hooks) -> {
                hooks.remove(reg);
                return hooks.isEmpty() ? null : hooks;
            });
        } else {
            channelHooks.computeIfPresent(reg.channelId(), (k, hooks) -> {
                hooks.remove(reg);
                return hooks.isEmpty() ? null : hooks;
            });
        }
        return true;
    }

    public Set<WebhookRegistration> findForChannel(UUID channelId, String tenancyId) {
        Set<WebhookRegistration> result  = new HashSet<>();
        Set<WebhookRegistration> globals = globalHooks.get(tenancyId);
        if (globals != null) {result.addAll(globals);}
        Set<WebhookRegistration> specific = channelHooks.get(channelId);
        if (specific != null) {result.addAll(specific);}
        return result;
    }

    public Set<WebhookRegistration> findByChannelId(UUID channelId) {
        return channelHooks.getOrDefault(channelId, Set.of());
    }

    public Collection<WebhookRegistration> listAll(String tenancyId) {
        return byId.values().stream()
                   .filter(r -> tenancyId.equals(r.tenancyId()))
                   .collect(Collectors.toList());
    }

    void removeChannel(UUID channelId) {
        Set<WebhookRegistration> removed = channelHooks.remove(channelId);
        if (removed != null) {
            for (WebhookRegistration r : removed) {
                byId.remove(r.id());
            }
        }
    }

    void onChannelClosed(@Observes ChannelClosedEvent event) {
        removeChannel(event.channelId());
        store.deleteByChannelId(event.channelId());
    }
}
