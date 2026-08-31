package io.casehub.qhorus.compliance.format;

import io.casehub.qhorus.compliance.model.ReportFormat;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReportRenderingServiceTest {

    @Test
    void render_selectsCorrectRenderer() {
        var jsonRenderer = mock(ReportRenderer.class);
        when(jsonRenderer.supports(ReportFormat.JSON)).thenReturn(true);
        when(jsonRenderer.render(any())).thenReturn(new byte[]{1, 2});
        var service = new ReportRenderingService(List.of(jsonRenderer));
        byte[] result = service.render("report", ReportFormat.JSON);
        assertThat(result).containsExactly(1, 2);
    }

    @Test
    void render_noRenderer_throws() {
        var service = new ReportRenderingService(List.of());
        assertThatThrownBy(() -> service.render("report", ReportFormat.PDF))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PDF");
    }

    @Test
    void render_skipsNonMatchingRenderers() {
        var htmlRenderer = mock(ReportRenderer.class);
        when(htmlRenderer.supports(ReportFormat.HTML)).thenReturn(true);
        when(htmlRenderer.supports(ReportFormat.JSON)).thenReturn(false);

        var jsonRenderer = mock(ReportRenderer.class);
        when(jsonRenderer.supports(ReportFormat.JSON)).thenReturn(true);
        when(jsonRenderer.render(any())).thenReturn(new byte[]{3, 4});

        var service = new ReportRenderingService(List.of(htmlRenderer, jsonRenderer));
        byte[] result = service.render("report", ReportFormat.JSON);
        assertThat(result).containsExactly(3, 4);
    }
}
