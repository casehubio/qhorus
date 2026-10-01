package io.casehub.qhorus.compliance.storage;

import io.casehub.qhorus.api.compliance.report.ReportType;
import io.casehub.qhorus.compliance.core.storage.ComplianceReportRecord;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class ComplianceReportRecordStore
        implements io.casehub.qhorus.compliance.core.storage.ComplianceReportRecordStore {

    @PersistenceContext(unitName = "qhorus")
    EntityManager em;

    @Override
    @Transactional
    public ComplianceReportRecord save(ComplianceReportRecord record) {
        em.persist(record);
        return record;
    }

    @Override
    public Optional<ComplianceReportRecord> findById(UUID id) {
        return Optional.ofNullable(em.find(ComplianceReportRecord.class, id));
    }

    @Override
    public List<ComplianceReportRecord> findByType(ReportType type, String tenancyId, int limit) {
        return em.createQuery(
                        "SELECT r FROM ComplianceReportRecord r WHERE r.reportType = :type AND r.tenancyId = :tid ORDER BY r.generatedAt DESC",
                        ComplianceReportRecord.class)
                .setParameter("type", type)
                .setParameter("tid", tenancyId)
                .setMaxResults(limit)
                .getResultList();
    }

    @Override
    public List<ComplianceReportRecord> findByTimeRange(Instant from, Instant to, String tenancyId, int limit) {
        return em.createQuery(
                        "SELECT r FROM ComplianceReportRecord r WHERE r.tenancyId = :tid AND r.generatedAt >= :from AND r.generatedAt <= :to ORDER BY r.generatedAt DESC",
                        ComplianceReportRecord.class)
                .setParameter("tid", tenancyId)
                .setParameter("from", from)
                .setParameter("to", to)
                .setMaxResults(limit)
                .getResultList();
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        ComplianceReportRecord record = em.find(ComplianceReportRecord.class, id);
        if (record != null) {
            em.remove(record);
        }
    }

    @Override
    public List<ComplianceReportRecord> findOlderThan(Instant cutoff) {
        return em.createQuery(
                         "SELECT r FROM ComplianceReportRecord r WHERE r.generatedAt < :cutoff ORDER BY r.generatedAt ASC",
                         ComplianceReportRecord.class)
                 .setParameter("cutoff", cutoff)
                 .getResultList();
    }
}
