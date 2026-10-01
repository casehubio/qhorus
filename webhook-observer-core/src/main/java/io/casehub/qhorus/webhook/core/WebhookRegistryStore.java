package io.casehub.qhorus.webhook.core;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public interface WebhookRegistryStore {

    WebhookRegistration register(UUID channelId, String tenancyId, String url,
                                  String secretRef, Map<String, String> headers);

    boolean deregister(UUID registrationId);

    Collection<WebhookRegistration> findByChannelId(UUID channelId);

    Collection<WebhookRegistration> listAll(String tenancyId);
}
