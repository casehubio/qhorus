package io.casehub.qhorus.runtime.channel;

import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.channel.ChannelSemantic;
import io.casehub.qhorus.api.store.ChannelStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyOverridesTest {

    private final Map<UUID, Channel> store = new ConcurrentHashMap<>();
    private final ChannelStore channelStore = new ChannelStore() {
        @Override public Channel put(Channel ch) { store.put(ch.id(), ch); return ch; }
        @Override public Optional<Channel> find(UUID id) { return Optional.ofNullable(store.get(id)); }
        @Override public Optional<Channel> findByName(String name) { return Optional.empty(); }
        @Override public List<Channel> scan(io.casehub.qhorus.api.store.query.ChannelQuery q) { return List.of(); }
        @Override public void delete(UUID id) { store.remove(id); }
        @Override public void updateLastActivity(UUID id, String t) {}
        @Override public void updateTrackDelivery(UUID id, Boolean td) {}
        @Override public List<Channel> findByIds(Collection<UUID> ids) { return List.of(); }
    };
    private ChannelService channelService;

    @BeforeEach
    void setup() {
        channelService = new ChannelService();
        channelService.channelStore = channelStore;
    }

    private Channel createChannel(String name) {
        return channelStore.put(Channel.builder(name)
                .id(UUID.randomUUID())
                .semantic(ChannelSemantic.APPEND)
                .build());
    }

    @Test
    void setPolicyOverrides_storesAndRetrieves() {
        Channel ch = createChannel("policy-test-" + UUID.randomUUID());
        channelService.setPolicyOverrides(ch.id(), Map.of("max_open_queries", "5"));
        Channel updated = channelStore.find(ch.id()).orElseThrow();
        assertThat(updated.policyOverrides()).containsEntry("max_open_queries", "5");
    }

    @Test
    void setPolicyOverrides_mergesWithExisting() {
        Channel ch = createChannel("policy-merge-" + UUID.randomUUID());
        channelService.setPolicyOverrides(ch.id(), Map.of("key1", "val1"));
        channelService.setPolicyOverrides(ch.id(), Map.of("key2", "val2"));
        Channel updated = channelStore.find(ch.id()).orElseThrow();
        assertThat(updated.policyOverrides())
                .containsEntry("key1", "val1")
                .containsEntry("key2", "val2");
    }

    @Test
    void setPolicyOverrides_nullValueRemovesKey() {
        Channel ch = createChannel("policy-remove-" + UUID.randomUUID());
        channelService.setPolicyOverrides(ch.id(), Map.of("key1", "val1", "key2", "val2"));
        Map<String, String> update = new LinkedHashMap<>();
        update.put("key1", null);
        channelService.setPolicyOverrides(ch.id(), update);
        Channel updated = channelStore.find(ch.id()).orElseThrow();
        assertThat(updated.policyOverrides())
                .doesNotContainKey("key1")
                .containsEntry("key2", "val2");
    }

    @Test
    void setPolicyOverrides_nullMapClearsAll() {
        Channel ch = createChannel("policy-clear-" + UUID.randomUUID());
        channelService.setPolicyOverrides(ch.id(), Map.of("key1", "val1"));
        channelService.setPolicyOverrides(ch.id(), null);
        Channel updated = channelStore.find(ch.id()).orElseThrow();
        assertThat(updated.policyOverrides()).isNull();
    }

    @Test
    void setPolicyOverrides_removingAllKeysReturnsNull() {
        Channel ch = createChannel("policy-empty-" + UUID.randomUUID());
        channelService.setPolicyOverrides(ch.id(), Map.of("key1", "val1"));
        Map<String, String> removeAll = new LinkedHashMap<>();
        removeAll.put("key1", null);
        channelService.setPolicyOverrides(ch.id(), removeAll);
        Channel updated = channelStore.find(ch.id()).orElseThrow();
        assertThat(updated.policyOverrides()).isNull();
    }

    @Test
    void channelRecord_policyOverridesDefensiveCopy() {
        var mutable = new LinkedHashMap<String, String>();
        mutable.put("key", "val");
        Channel ch = Channel.builder("test").id(UUID.randomUUID()).semantic(ChannelSemantic.APPEND)
                .policyOverrides(mutable).build();
        mutable.put("key2", "val2");
        assertThat(ch.policyOverrides()).doesNotContainKey("key2");
    }

    @Test
    void channelRecord_nullPolicyOverridesRemains() {
        Channel ch = Channel.builder("test").id(UUID.randomUUID()).semantic(ChannelSemantic.APPEND).build();
        assertThat(ch.policyOverrides()).isNull();
    }
}
