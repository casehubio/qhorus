package io.casehub.qhorus.compliance.storage;

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
@Table(name = "compliance_report")
public class ComplianceReportRecord extends PanacheEntityBase {

    @Id
    @GeneratedValue
    public UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", nullable = false, length = 50)
    public ReportType reportType;

    @Column(name = "tenancy_id", nullable = false)
    public String tenancyId;

    @Column(name = "generated_at", nullable = false)
    public Instant generatedAt;

    @Column(name = "schedule_id")
    public UUID scheduleId;

    @Column(name = "artefact_id", nullable = false)
    public UUID artefactId;

    @Column(name = "schema_version", nullable = false)
    public int schemaVersion = 1;

    @Column(name = "format", nullable = false, length = 10)
    @Enumerated(EnumType.STRING)
    public ReportFormat format;
}
