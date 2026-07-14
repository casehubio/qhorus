package io.casehub.qhorus.webhook;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "webhook_registration")
public class WebhookRegistrationEntity {

    @Id
    public UUID id;

    @Column(name = "channel_id")
    public UUID channelId;

    @Column(nullable = false, length = 2048)
    public String url;

    @Column(name = "secret_ref")
    public String secretRef;

    @Convert(converter = MapToJsonConverter.class)
    public Map<String, String> headers;

    @Column(name = "tenancy_id", nullable = false)
    public String tenancyId;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;
}
