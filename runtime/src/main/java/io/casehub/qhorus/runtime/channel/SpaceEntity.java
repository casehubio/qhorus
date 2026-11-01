package io.casehub.qhorus.runtime.channel;

import io.casehub.platform.api.identity.TenancyConstants;
import io.casehub.qhorus.api.channel.Space;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Entity(name = "Space")
@Table(name = "space", uniqueConstraints = @UniqueConstraint(name = "uq_space_name_parent_tenancy", columnNames = {"tenancy_id", "parent_space_id", "name"}))
public class SpaceEntity {

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

    @Column(name = "default_protocols")
    public String defaultProtocols;

    @Column(name = "default_protocol_participants")
    public String defaultProtocolParticipants;

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
        e.defaultProtocols = joinCsv(space.defaultProtocols());
        e.defaultProtocolParticipants = joinCsv(space.defaultProtocolParticipants());
        return e;
    }

    public Space toDomain() {
        return new Space(id, name, description, parentSpaceId, tenancyId, createdAt,
                         splitCsv(defaultProtocols), splitCsv(defaultProtocolParticipants));
    }

    private static String joinCsv(List<String> items) {
        if (items == null || items.isEmpty()) {return null;}
        return String.join(",", items);
    }

    private static List<String> splitCsv(String csv) {
        if (csv == null || csv.isBlank()) {return List.of();}
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
