package io.casehub.qhorus.graphql.data;

import io.casehub.qhorus.api.data.DataManager;
import io.casehub.qhorus.api.data.SharedData;
import io.casehub.qhorus.api.message.ArtefactRef;
import io.casehub.qhorus.api.message.ConsumerMessaging;
import io.casehub.qhorus.api.message.Message;
import io.casehub.qhorus.api.spi.data.DataApi;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class DataServiceImpl implements DataApi {

    private final DataManager dataManager;
    private final ConsumerMessaging consumerMessaging;

    public DataServiceImpl(DataManager dataManager, ConsumerMessaging consumerMessaging) {
        this.dataManager = dataManager;
        this.consumerMessaging = consumerMessaging;
    }

    @Override
    public SharedData artefact(String key, UUID id) {
        boolean hasKey = key != null && !key.isBlank();
        boolean hasId = id != null;
        if (!hasKey && !hasId) {
            throw new IllegalArgumentException("Either 'key' or 'id' must be provided");
        }
        return hasKey
                ? dataManager.getByKey(key)
                        .orElseThrow(() -> new IllegalArgumentException("Artefact not found: key=" + key))
                : dataManager.getByUuid(id)
                        .orElseThrow(() -> new IllegalArgumentException("Artefact not found: id=" + id));
    }

    @Override
    public List<ArtefactRef> artefactRefs(Long messageId) {
        Message msg = consumerMessaging.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Message not found: " + messageId));
        return msg.artefactRefs() != null ? msg.artefactRefs() : List.of();
    }

    @Override
    public List<SharedData> artefacts() {
        return dataManager.listAll();
    }

    @Override
    public boolean isGcEligible(UUID artefactId) {
        return dataManager.isGcEligible(artefactId);
    }

    @Override
    public SharedData shareArtefact(String key, String description, String createdBy, String content) {
        return dataManager.store(key, description, createdBy, content, false, true);
    }

    @Override
    public SharedData beginArtefact(String key, String description, String createdBy, String content) {
        return dataManager.store(key, description, createdBy, content, false, false);
    }

    @Override
    public SharedData appendChunk(String key, String content) {
        return dataManager.store(key, null, null, content, true, false);
    }

    @Override
    public SharedData finalizeArtefact(String key, String content) {
        String chunk = content != null ? content : "";
        return dataManager.store(key, null, null, chunk, true, true);
    }

    @Override
    public void claimArtefact(UUID artefactId, UUID instanceId) {
        dataManager.claim(artefactId, instanceId);
    }

    @Override
    public void releaseArtefact(UUID artefactId, UUID instanceId) {
        dataManager.release(artefactId, instanceId);
    }

    @Override
    public boolean revokeArtefact(UUID artefactId) {
        var data = dataManager.getByUuid(artefactId);
        if (data.isEmpty()) {
            return false;
        }
        dataManager.delete(artefactId);
        return true;
    }
}
