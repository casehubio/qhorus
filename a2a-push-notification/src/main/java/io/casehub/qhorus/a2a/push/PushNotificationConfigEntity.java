package io.casehub.qhorus.a2a.push;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import io.casehub.qhorus.api.a2a.PushNotificationConfig;

@Entity(name = "PushNotificationConfig")
@Table(name = "push_notification_config",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_push_config_task_url",
        columnNames = {"task_id", "url"}))
public class PushNotificationConfigEntity {

    @Id
    public UUID id;

    @Column(name = "task_id", nullable = false)
    public String taskId;

    @Column(name = "channel_id", nullable = false)
    public UUID channelId;

    @Column(nullable = false, length = 1024)
    public String url;

    @Column
    public String token;

    @Column(name = "auth_scheme")
    public String authScheme;

    @Column(name = "auth_credentials_ref")
    public String authCredentialsRef;

    @Column(name = "tenancy_id", nullable = false)
    public String tenancyId;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @Column(name = "last_pushed_at")
    public Instant lastPushedAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
    }

    public static PushNotificationConfigEntity fromDomain(PushNotificationConfig config) {
        PushNotificationConfigEntity e = new PushNotificationConfigEntity();
        e.id = config.id();
        e.taskId = config.taskId();
        e.channelId = config.channelId();
        e.url = config.url();
        e.token = config.token();
        e.authScheme = config.authScheme();
        e.authCredentialsRef = config.authCredentialsRef();
        e.tenancyId = config.tenancyId();
        e.createdAt = config.createdAt();
        e.lastPushedAt = config.lastPushedAt();
        return e;
    }

    public PushNotificationConfig toDomain() {
        return new PushNotificationConfig(id, taskId, channelId, url, token,
                authScheme, authCredentialsRef, tenancyId, createdAt, lastPushedAt);
    }
}
