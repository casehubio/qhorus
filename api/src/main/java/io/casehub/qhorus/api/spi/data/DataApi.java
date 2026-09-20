package io.casehub.qhorus.api.spi.data;

import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.qhorus.api.data.SharedData;
import io.casehub.qhorus.api.message.ArtefactRef;

import java.util.List;
import java.util.UUID;

@McpDomain("data")
public interface DataApi {

    @PlatformQuery("Retrieve a shared artefact by key or UUID")
    SharedData artefact(String key, UUID id);

    @PlatformQuery("Get artefact references attached to a message")
    List<ArtefactRef> artefactRefs(Long messageId);

    @PlatformQuery("List all artefacts with metadata")
    List<SharedData> artefacts();

    @PlatformQuery("Check if an artefact is eligible for garbage collection")
    boolean isGcEligible(UUID artefactId);

    @PlatformMutation("Store a shared artefact by key")
    SharedData shareArtefact(String key, String description, String createdBy, String content);

    @PlatformMutation("Begin a chunked artefact upload")
    SharedData beginArtefact(String key, String description, String createdBy, String content);

    @PlatformMutation("Append a chunk to an in-progress artefact upload")
    SharedData appendChunk(String key, String content);

    @PlatformMutation("Finalize a chunked artefact upload")
    SharedData finalizeArtefact(String key, String content);

    @PlatformMutation("Manually claim an artefact reference")
    void claimArtefact(UUID artefactId, UUID instanceId);

    @PlatformMutation("Release an artefact claim")
    void releaseArtefact(UUID artefactId, UUID instanceId);

    @PlatformMutation("Force-delete a shared artefact and release all claims")
    boolean revokeArtefact(UUID artefactId);
}
