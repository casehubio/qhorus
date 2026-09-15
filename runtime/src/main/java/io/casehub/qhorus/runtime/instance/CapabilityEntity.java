package io.casehub.qhorus.runtime.instance;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;


@Entity(name = "Capability")
@Table(name = "capability")
public class CapabilityEntity {

    @Id
    public UUID id;

    @Column(name = "instance_id", nullable = false)
    public UUID instanceId;

    @Column(nullable = false)
    public String tag;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }
}
