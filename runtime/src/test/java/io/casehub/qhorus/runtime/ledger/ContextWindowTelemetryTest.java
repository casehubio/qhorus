package io.casehub.qhorus.runtime.ledger;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ContextWindowTelemetryTest {

    private LedgerWriteService service;
    private Method populateTelemetry;

    @BeforeEach
    void setUp() throws Exception {
        service = new LedgerWriteService();
        service.objectMapper = new ObjectMapper();
        populateTelemetry = LedgerWriteService.class.getDeclaredMethod(
                "populateTelemetry", MessageLedgerEntry.class, String.class);
        populateTelemetry.setAccessible(true);
    }

    @Test
    void parsesContextWindowPct() throws Exception {
        MessageLedgerEntry entry = new MessageLedgerEntry();
        populateTelemetry.invoke(service, entry, "{\"tool_name\":\"search\",\"context_window_pct\":75}");

        assertThat(entry.contextWindowPct).isEqualTo(75);
        assertThat(entry.toolName).isEqualTo("search");
    }

    @Test
    void missingContextWindowPct_remainsNull() throws Exception {
        MessageLedgerEntry entry = new MessageLedgerEntry();
        populateTelemetry.invoke(service, entry, "{\"tool_name\":\"search\",\"duration_ms\":42}");

        assertThat(entry.contextWindowPct).isNull();
        assertThat(entry.toolName).isEqualTo("search");
    }

    @Test
    void contextWindowPctZero() throws Exception {
        MessageLedgerEntry entry = new MessageLedgerEntry();
        populateTelemetry.invoke(service, entry, "{\"context_window_pct\":0}");

        assertThat(entry.contextWindowPct).isEqualTo(0);
    }

    @Test
    void contextWindowPct100() throws Exception {
        MessageLedgerEntry entry = new MessageLedgerEntry();
        populateTelemetry.invoke(service, entry, "{\"context_window_pct\":100}");

        assertThat(entry.contextWindowPct).isEqualTo(100);
    }

    @Test
    void domainContentBytes_includesContextWindowPct() {
        MessageLedgerEntry entry = new MessageLedgerEntry();
        entry.contextWindowPct = 85;
        entry.channelId = UUID.randomUUID();
        entry.messageId = 1L;
        entry.messageType = "EVENT";

        byte[] bytes = entry.domainContentBytes();
        String canonical = new String(bytes);

        assertThat(canonical).contains("85");
    }

    @Test
    void domainContentBytes_nullContextWindowPct_emptySegment() {
        MessageLedgerEntry entry = new MessageLedgerEntry();
        entry.contextWindowPct = null;
        entry.channelId = UUID.randomUUID();
        entry.messageId = 1L;
        entry.messageType = "EVENT";

        byte[] bytes = entry.domainContentBytes();
        assertThat(bytes).isNotEmpty();
    }
}
