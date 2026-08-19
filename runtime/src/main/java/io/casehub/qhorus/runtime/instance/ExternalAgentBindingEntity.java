package io.casehub.qhorus.runtime.instance;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import io.casehub.qhorus.api.instance.ExternalAgentBinding;

@Entity(name = "ExternalAgentBinding")
@Table(name = "external_agent_binding",
    uniqueConstraints = @UniqueConstraint(name = "uq_eab_instance_id", columnNames = "instance_id"))
public class ExternalAgentBindingEntity {

    @Id
    public UUID id;

    @Column(name = "instance_id", nullable = false)
    public String instanceId;

    @Column(nullable = false, length = 1024)
    public String endpoint;

    @Column(name = "auth_config_key")
    public String authConfigKey;

    @Column(name = "protocol_version", nullable = false)
    public String protocolVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
        if (protocolVersion == null) protocolVersion = "1.0";
    }

    public static ExternalAgentBindingEntity fromDomain(ExternalAgentBinding binding) {
        ExternalAgentBindingEntity e = new ExternalAgentBindingEntity();
        e.id = binding.id();
        e.instanceId = binding.instanceId();
        e.endpoint = binding.endpoint();
        e.authConfigKey = binding.authConfigKey();
        e.protocolVersion = binding.protocolVersion();
        e.createdAt = binding.createdAt();
        return e;
    }

    public ExternalAgentBinding toDomain() {
        return new ExternalAgentBinding(id, instanceId, endpoint, authConfigKey, protocolVersion, createdAt);
    }
}
