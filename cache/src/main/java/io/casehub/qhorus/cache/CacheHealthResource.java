package io.casehub.qhorus.cache;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/")
@Produces(MediaType.APPLICATION_JSON)
public class CacheHealthResource {

    @Inject
    Instance<CachingMessageStore> cachingStore;

    @Inject
    Instance<FullSyncService> fullSyncService;

    @GET
    @Path("/health/cache")
    public CacheHealthResponse health() {
        if (!cachingStore.isResolvable()) {
            return new CacheHealthResponse("DISABLED", 0, 0, null, 0, 0);
        }
        CachingMessageStore store = cachingStore.get();
        String syncStatus = null;
        int channelsSynced = 0;
        int channelsTotal = 0;
        if (fullSyncService.isResolvable()) {
            FullSyncService sync = fullSyncService.get();
            syncStatus = sync.status().name();
            channelsSynced = sync.channelsSynced();
            channelsTotal = sync.channelsTotal();
        }
        return new CacheHealthResponse(
                "UP",
                store.channelsCached(),
                store.messagesCached(),
                syncStatus,
                channelsSynced,
                channelsTotal);
    }
}