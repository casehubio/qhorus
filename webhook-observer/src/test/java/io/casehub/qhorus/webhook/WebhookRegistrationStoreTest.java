package io.casehub.qhorus.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class WebhookRegistrationStoreTest {

    @Inject
    WebhookRegistrationStore store;

    @Test
    @TestTransaction
    void saveAndFindById() {
        var entity = entity("https://example.com/hook", null);
        store.save(entity);

        var found = store.findById(entity.id);
        assertThat(found).isPresent();
        assertThat(found.get().url).isEqualTo("https://example.com/hook");
    }

    @Test
    @TestTransaction
    void findByChannelIdReturnsOnlyChannelSpecific() {
        UUID channelId = UUID.randomUUID();
        store.save(entity("https://example.com/specific", channelId));
        store.save(entity("https://example.com/global", null));

        assertThat(store.findByChannelId(channelId)).hasSize(1);
        assertThat(store.findByChannelId(channelId).get(0).url).isEqualTo("https://example.com/specific");
    }

    @Test
    @TestTransaction
    void findGlobalReturnsNullChannelIdOnly() {
        store.save(entity("https://example.com/global", null));
        store.save(entity("https://example.com/specific", UUID.randomUUID()));

        assertThat(store.findGlobal()).hasSize(1);
        assertThat(store.findGlobal().get(0).url).isEqualTo("https://example.com/global");
    }

    @Test
    @TestTransaction
    void findAllIsCrossTenant() {
        var e1 = entity("https://t1.com/hook", null);
        store.save(e1);

        var e2 = entity("https://t2.com/hook", null);
        e2.tenancyId = "other-tenant-" + UUID.randomUUID();
        store.save(e2);

        assertThat(store.findAll()).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    @TestTransaction
    void deleteRemovesEntity() {
        var entity = entity("https://example.com/hook", null);
        store.save(entity);

        assertThat(store.delete(entity.id)).isTrue();
        assertThat(store.findById(entity.id)).isEmpty();
    }

    @Test
    @TestTransaction
    void deleteUnknownReturnsFalse() {
        assertThat(store.delete(UUID.randomUUID())).isFalse();
    }

    @Test
    @TestTransaction
    void deleteByChannelIdRemovesAllForChannel() {
        UUID channelId = UUID.randomUUID();
        store.save(entity("https://a.com/hook", channelId));
        store.save(entity("https://b.com/hook", channelId));

        store.deleteByChannelId(channelId);
        assertThat(store.findByChannelId(channelId)).isEmpty();
    }

    @Test
    @TestTransaction
    void headersSerializedAsJson() {
        var entity = entity("https://example.com/hook", null);
        entity.headers = Map.of("Authorization", "Bearer tok", "X-Custom", "val");
        store.save(entity);

        var found = store.findById(entity.id).orElseThrow();
        assertThat(found.headers).containsEntry("Authorization", "Bearer tok");
        assertThat(found.headers).containsEntry("X-Custom", "val");
    }

    @Test
    @TestTransaction
    void nullHeadersRemainNullAfterRoundTrip() {
        var entity = entity("https://example.com/hook", null);
        entity.headers = null;
        store.save(entity);

        var found = store.findById(entity.id).orElseThrow();
        assertThat(found.headers).isNull();
    }

    @Test
    @TestTransaction
    void secretRefIsPersisted() {
        var entity = entity("https://example.com/hook", null);
        entity.secretRef = "my-webhook-cred";
        store.save(entity);

        var found = store.findById(entity.id).orElseThrow();
        assertThat(found.secretRef).isEqualTo("my-webhook-cred");
    }

    private static WebhookRegistrationEntity entity(String url, UUID channelId) {
        var e = new WebhookRegistrationEntity();
        e.id = UUID.randomUUID();
        e.channelId = channelId;
        e.url = url;
        e.tenancyId = "278776f9-e1b0-46fb-9032-8bddebdcf9ce";
        e.createdAt = Instant.now();
        return e;
    }
}
