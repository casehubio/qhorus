package io.casehub.qhorus.api.channel;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ThreadSummary(
        UUID id,
        UUID channelId,
        String correlationId,
        String content,
        Map<String, String> annotations,
        Instant updatedAt,
        String updatedBy,
        String tenancyId) {

    public ThreadSummary {
        annotations = annotations != null ? Map.copyOf(annotations) : Map.of();
    }

    public Builder toBuilder() {
        return new Builder(channelId, correlationId)
                       .id(id).content(content).annotations(annotations)
                       .updatedAt(updatedAt).updatedBy(updatedBy)
                       .tenancyId(tenancyId);
    }

    public static Builder builder(UUID channelId, String correlationId) {
        return new Builder(channelId, correlationId);
    }

    public static final class Builder {
        private final UUID   channelId;
        private final String correlationId;
        private UUID                id;
        private String              content;
        private Map<String, String> annotations;
        private Instant             updatedAt;
        private String              updatedBy;
        private String              tenancyId;

        private Builder(UUID channelId, String correlationId) {
            this.channelId = channelId;
            this.correlationId = correlationId;
        }

        public Builder id(UUID v)                         { this.id = v; return this; }
        public Builder content(String v)                  { this.content = v; return this; }
        public Builder annotations(Map<String, String> v) { this.annotations = v; return this; }
        public Builder updatedAt(Instant v)               { this.updatedAt = v; return this; }
        public Builder updatedBy(String v)                { this.updatedBy = v; return this; }
        public Builder tenancyId(String v)                { this.tenancyId = v; return this; }

        public ThreadSummary build() {
            return new ThreadSummary(id, channelId, correlationId, content,
                    annotations, updatedAt, updatedBy, tenancyId);
        }
    }
}
