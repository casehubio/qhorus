package io.casehub.qhorus.compliance.schedule;

import io.casehub.qhorus.compliance.model.ReportFormat;
import io.casehub.qhorus.compliance.model.ReportType;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "compliance_report_schedule")
public class ComplianceReportSchedule extends PanacheEntityBase {

    @Id
    @GeneratedValue
    public UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", nullable = false, length = 50)
    public ReportType reportType;

    @Column(name = "channel_id")
    public UUID channelId;

    @Column(name = "schedule_json", nullable = false, columnDefinition = "TEXT")
    public String scheduleJson;

    @Column(name = "format", nullable = false, length = 10)
    @Enumerated(EnumType.STRING)
    public ReportFormat format;

    @Column(name = "tenancy_id", nullable = false)
    public String tenancyId;

    @Column(nullable = false)
    public boolean enabled = true;

    @Column(name = "last_run_at")
    public Instant lastRunAt;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @jakarta.persistence.PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
