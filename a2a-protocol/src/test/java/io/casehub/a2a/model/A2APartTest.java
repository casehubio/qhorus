package io.casehub.a2a.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class A2APartTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void textPart_serialisesWithTypeDiscriminator() throws Exception {
        A2APart part = new A2APart.TextPart("hello world");
        String json = MAPPER.writeValueAsString(part);
        assertThat(json).contains("\"type\":\"text\"");
        assertThat(json).contains("\"text\":\"hello world\"");
    }

    @Test
    void dataPart_serialisesWithMimeTypeAndData() throws Exception {
        A2APart part = new A2APart.DataPart("application/json",
            MAPPER.createObjectNode().put("score", 0.95));
        String json = MAPPER.writeValueAsString(part);
        assertThat(json).contains("\"type\":\"data\"");
        assertThat(json).contains("\"mimeType\":\"application/json\"");
        assertThat(json).contains("\"score\"");
    }

    @Test
    void filePart_serialisesWithUriAndName() throws Exception {
        A2APart part = new A2APart.FilePart(
            "https://example.com/report.pdf", "report.pdf", "application/pdf");
        String json = MAPPER.writeValueAsString(part);
        assertThat(json).contains("\"type\":\"file\"");
        assertThat(json).contains("\"uri\":\"https://example.com/report.pdf\"");
        assertThat(json).contains("\"name\":\"report.pdf\"");
    }

    @Test
    void roundTrip_textPart() throws Exception {
        A2APart original = new A2APart.TextPart("hello");
        String json = MAPPER.writeValueAsString(original);
        A2APart parsed = MAPPER.readValue(json, A2APart.class);
        assertThat(parsed).isEqualTo(original);
    }

    @Test
    void roundTrip_dataPart() throws Exception {
        A2APart original = new A2APart.DataPart("application/json",
            MAPPER.createObjectNode().put("key", "value"));
        String json = MAPPER.writeValueAsString(original);
        A2APart parsed = MAPPER.readValue(json, A2APart.class);
        assertThat(parsed).isInstanceOf(A2APart.DataPart.class);
        A2APart.DataPart data = (A2APart.DataPart) parsed;
        assertThat(data.mimeType()).isEqualTo("application/json");
        assertThat(data.data().get("key").asText()).isEqualTo("value");
    }

    @Test
    void roundTrip_filePart() throws Exception {
        A2APart original = new A2APart.FilePart(
            "https://example.com/file.txt", "file.txt", "text/plain");
        String json = MAPPER.writeValueAsString(original);
        A2APart parsed = MAPPER.readValue(json, A2APart.class);
        assertThat(parsed).isEqualTo(original);
    }
}
