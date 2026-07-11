package io.casehub.qhorus.runtime.message;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.qhorus.api.message.ArtefactRef;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.List;

@Converter
public class ArtefactRefListConverter implements AttributeConverter<List<ArtefactRef>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<ArtefactRef>> TYPE_REF = new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(List<ArtefactRef> refs) {
        if (refs == null || refs.isEmpty()) return null;
        try {
            return MAPPER.writeValueAsString(refs);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize ArtefactRef list", e);
        }
    }

    @Override
    public List<ArtefactRef> convertToEntityAttribute(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return MAPPER.readValue(json, TYPE_REF);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize ArtefactRef list", e);
        }
    }
}
