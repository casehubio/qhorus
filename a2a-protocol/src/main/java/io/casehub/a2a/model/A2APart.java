package io.casehub.a2a.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.JsonNode;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = A2APart.TextPart.class, name = "text"),
    @JsonSubTypes.Type(value = A2APart.DataPart.class, name = "data"),
    @JsonSubTypes.Type(value = A2APart.FilePart.class, name = "file")
})
public sealed interface A2APart {
    record TextPart(String text) implements A2APart {}
    record DataPart(String mimeType, JsonNode data) implements A2APart {}
    record FilePart(String uri, String name, String mimeType) implements A2APart {}
}
