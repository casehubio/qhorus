package io.casehub.qhorus.playbook.bridge;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity(name = "ProtocolDefinition")
@Table(name = "protocol_definition",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_protocol_def_channel_name",
        columnNames = {"channel_id", "protocol_name", "tenancy_id"}))
public class ProtocolDefinitionEntity {

    @Id
    public UUID id;

    @Column(name = "channel_id", nullable = false)
    public UUID channelId;

    @Column(name = "protocol_name", nullable = false, length = 200)
    public String protocolName;

    @Column(name = "yaml_content", nullable = false, columnDefinition = "TEXT")
    public String yamlContent;

    @Column(name = "tenancy_id", nullable = false)
    public String tenancyId;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
    }

    public static ProtocolDefinitionEntity fromDomain(ProtocolDefinition def) {
        ProtocolDefinitionEntity e = new ProtocolDefinitionEntity();
        e.id = def.id();
        e.channelId = def.channelId();
        e.protocolName = def.protocolName();
        e.yamlContent = def.yamlContent();
        e.tenancyId = def.tenancyId();
        e.createdAt = def.createdAt();
        return e;
    }

    public ProtocolDefinition toDomain() {
        return new ProtocolDefinition(id, channelId, protocolName,
                yamlContent, tenancyId, createdAt);
    }
}
