package io.casehub.qhorus.api.data;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DataManager {

    SharedData store(String key, String description, String createdBy,
                     String content, boolean append, boolean lastChunk);

    Optional<SharedData> getByKey(String key);

    Optional<SharedData> getByUuid(UUID id);

    List<SharedData> listAll();

    void claim(UUID artefactId, UUID instanceId);

    void release(UUID artefactId, UUID instanceId);

    boolean isGcEligible(UUID artefactId);

    int countClaims(UUID artefactId);

    void delete(UUID artefactId);
}
