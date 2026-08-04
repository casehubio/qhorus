package io.casehub.qhorus.runtime.channel;

import io.casehub.platform.api.identity.TenancyConstants;
import io.casehub.qhorus.api.channel.ThreadSummary;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity(name = "ThreadSummary")
@Table(name = "thread_summary",
       uniqueConstraints = @UniqueConstraint(
               columnNames = {"channel_id", "correlation_id"}))
public class ThreadSummaryEntity extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "channel_id", nullable = false)
    public UUID channelId;

    @Column(name = "correlation_id", nullable = false)
    public String correlationId;

    @Column(name = "content", columnDefinition = "TEXT")
    public String content;

    @Column(name = "annotations", columnDefinition = "TEXT")
    public String annotations;

    @Column(name = "updated_at")
    public Instant updatedAt;

    @Column(name = "updated_by")
    public String updatedBy;

    @Column(name = "tenancy_id", nullable = false, updatable = false)
    public String tenancyId = TenancyConstants.DEFAULT_TENANT_ID;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }

    public static ThreadSummaryEntity fromDomain(ThreadSummary s) {
        ThreadSummaryEntity e = new ThreadSummaryEntity();
        e.id            = s.id();
        e.channelId     = s.channelId();
        e.correlationId = s.correlationId();
        e.content       = s.content();
        e.annotations   = serializeAnnotations(s.annotations());
        e.updatedAt     = s.updatedAt();
        e.updatedBy     = s.updatedBy();
        e.tenancyId     = s.tenancyId() != null
                ? s.tenancyId() : TenancyConstants.DEFAULT_TENANT_ID;
        return e;
    }

    public ThreadSummary toDomain() {
        return new ThreadSummary(id, channelId, correlationId, content,
                deserializeAnnotations(annotations), updatedAt, updatedBy, tenancyId);
    }

    private static String serializeAnnotations(Map<String, String> annotations) {
        if (annotations == null || annotations.isEmpty()) { return null; }
        var sb = new StringBuilder("{");
        boolean first = true;
        for (var entry : annotations.entrySet()) {
            if (!first) { sb.append(","); }
            first = false;
            sb.append("\"").append(escapeJson(entry.getKey())).append("\":\"")
              .append(escapeJson(entry.getValue())).append("\"");
        }
        return sb.append("}").toString();
    }

    private static Map<String, String> deserializeAnnotations(String json) {
        if (json == null || json.isBlank()) { return Map.of(); }
        var result = new java.util.LinkedHashMap<String, String>();
        String content = json.substring(1, json.length() - 1);
        if (content.isEmpty()) { return Map.of(); }
        int i = 0;
        while (i < content.length()) {
            int keyStart = content.indexOf('"', i) + 1;
            int keyEnd   = findUnescapedQuote(content, keyStart);
            String key   = unescapeJson(content.substring(keyStart, keyEnd));
            int valStart = content.indexOf('"', keyEnd + 1) + 1;
            int valEnd   = findUnescapedQuote(content, valStart);
            String val   = unescapeJson(content.substring(valStart, valEnd));
            result.put(key, val);
            i = valEnd + 1;
            if (i < content.length() && content.charAt(i) == ',') { i++; }
        }
        return Map.copyOf(result);
    }

    private static int findUnescapedQuote(String s, int from) {
        for (int i = from; i < s.length(); i++) {
            if (s.charAt(i) == '"' && (i == 0 || s.charAt(i - 1) != '\\')) { return i; }
        }
        return s.length();
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String unescapeJson(String s) {
        return s.replace("\\\"", "\"").replace("\\\\", "\\");
    }
}
