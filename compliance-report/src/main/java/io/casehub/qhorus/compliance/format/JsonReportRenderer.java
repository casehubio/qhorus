package io.casehub.qhorus.compliance.format;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.casehub.qhorus.compliance.model.ReportFormat;
import jakarta.enterprise.context.ApplicationScoped;

import java.nio.charset.StandardCharsets;

@ApplicationScoped
public class JsonReportRenderer implements ReportRenderer {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(SerializationFeature.INDENT_OUTPUT);

    @Override
    public String contentType() {
        return "application/json";
    }

    @Override
    public byte[] render(Object report) {
        try {
            return MAPPER.writeValueAsBytes(report);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize report to JSON", e);
        }
    }

    @Override
    public boolean supports(ReportFormat format) {
        return format == ReportFormat.JSON;
    }
}
