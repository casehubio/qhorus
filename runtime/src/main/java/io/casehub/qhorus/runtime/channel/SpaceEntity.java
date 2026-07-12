package io.casehub.qhorus.runtime.channel;

import io.casehub.platform.api.identity.TenancyConstants;
import io.casehub.qhorus.api.channel.Space;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity(name = "Space")
@Table(name = "space", uniqueConstraints = @UniqueConstraint(name = "uq_space_name_parent_tenancy", columnNames = {"tenancy_id", "parent_space_id", "name"}))
public class SpaceEntity extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(nullable = false)
    public String name;

    public String description;

    @Column(name = "parent_space_id")
    public UUID parentSpaceId;

    @Column(name = "tenancy_id", nullable = false, updatable = false)
    public String tenancyId = TenancyConstants.DEFAULT_TENANT_ID;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) {id = UUID.randomUUID();}
        if (createdAt == null) {createdAt = Instant.now();}
    }

    public static SpaceEntity fromDomain(Space space) {
        SpaceEntity e = new SpaceEntity();
        e.id            = space.id();
        e.name          = space.name();
        e.description   = space.description();
        e.parentSpaceId = space.parentSpaceId();
        e.tenancyId     = space.tenancyId() != null ? space.tenancyId() : TenancyConstants.DEFAULT_TENANT_ID;
        e.createdAt     = space.createdAt();
        return e;
    }

    public Space toDomain() {
        return new Space(id, name, description, parentSpaceId, tenancyId, createdAt);
    }
}
