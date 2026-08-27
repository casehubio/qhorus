package io.casehub.qhorus.compliance.schedule;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class ComplianceReportScheduleStore {

    @PersistenceContext(unitName = "qhorus")
    EntityManager em;

    @Transactional
    public ComplianceReportSchedule save(ComplianceReportSchedule schedule) {
        return em.merge(schedule);
    }

    public Optional<ComplianceReportSchedule> findById(UUID id) {
        return Optional.ofNullable(em.find(ComplianceReportSchedule.class, id));
    }

    public List<ComplianceReportSchedule> findByTenancy(String tenancyId) {
        return em.createQuery(
                        "SELECT s FROM ComplianceReportSchedule s WHERE s.tenancyId = :tid",
                        ComplianceReportSchedule.class)
                .setParameter("tid", tenancyId)
                .getResultList();
    }

    public List<ComplianceReportSchedule> findEnabled() {
        return em.createQuery(
                        "SELECT s FROM ComplianceReportSchedule s WHERE s.enabled = true",
                        ComplianceReportSchedule.class)
                .getResultList();
    }

    @Transactional
    public void updateLastRunAt(UUID id, Instant lastRunAt) {
        em.createQuery("UPDATE ComplianceReportSchedule s SET s.lastRunAt = :at WHERE s.id = :id")
                .setParameter("at", lastRunAt)
                .setParameter("id", id)
                .executeUpdate();
    }

    @Transactional
    public void delete(UUID id) {
        ComplianceReportSchedule schedule = em.find(ComplianceReportSchedule.class, id);
        if (schedule != null) {
            em.remove(schedule);
        }
    }
}
