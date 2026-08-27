package io.casehub.qhorus.compliance.graphql;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.qhorus.compliance.graphql.dto.ComplianceReportRecordType;
import io.casehub.qhorus.compliance.graphql.dto.ComplianceReportScheduleType;
import io.casehub.qhorus.compliance.graphql.dto.ComplianceScheduleInput;
import io.casehub.qhorus.compliance.graphql.dto.ComplianceScheduleUpdateInput;
import io.casehub.qhorus.compliance.schedule.ComplianceReportSchedule;
import io.casehub.qhorus.compliance.schedule.ComplianceReportScheduleStore;
import io.casehub.qhorus.compliance.storage.ComplianceReportStorageService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.graphql.Description;
import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.Mutation;

import java.util.UUID;

@GraphQLApi
@McpDomain("qhorus")
@ApplicationScoped
public class ComplianceMutationResolver {

    @Inject ComplianceReportScheduleStore scheduleStore;
    @Inject ComplianceReportStorageService storageService;
    @Inject CurrentPrincipal currentPrincipal;

    @Mutation
    @Description("Create a new scheduled compliance report generation")
    public ComplianceReportScheduleType createComplianceSchedule(ComplianceScheduleInput input) {
        ComplianceReportSchedule schedule = new ComplianceReportSchedule();
        schedule.reportType = input.reportType();
        schedule.channelId = input.channelId();
        schedule.scheduleJson = input.scheduleJson();
        schedule.format = input.format();
        schedule.tenancyId = currentPrincipal.tenancyId();
        schedule.enabled = true;
        ComplianceReportSchedule saved = scheduleStore.save(schedule);
        return ComplianceReportScheduleType.from(saved);
    }

    @Mutation
    @Description("Update a compliance report schedule — toggle enabled or change schedule expression")
    public ComplianceReportScheduleType updateComplianceSchedule(ComplianceScheduleUpdateInput input) {
        ComplianceReportSchedule schedule = scheduleStore.findById(input.id())
                .orElseThrow(() -> new IllegalArgumentException("Schedule not found: " + input.id()));
        if (input.enabled() != null) schedule.enabled = input.enabled();
        if (input.scheduleJson() != null) schedule.scheduleJson = input.scheduleJson();
        return ComplianceReportScheduleType.from(scheduleStore.save(schedule));
    }

    @Mutation
    @Description("Delete a compliance report schedule")
    public boolean deleteComplianceSchedule(UUID id) {
        scheduleStore.delete(id);
        return true;
    }

    @Mutation
    @Description("Delete a stored compliance report artefact")
    public boolean deleteComplianceReport(UUID id) {
        storageService.delete(id);
        return true;
    }
}
