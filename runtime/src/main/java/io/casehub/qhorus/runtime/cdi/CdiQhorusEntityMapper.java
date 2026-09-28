package io.casehub.qhorus.runtime.cdi;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.qhorus.runtime.QhorusEntityMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class CdiQhorusEntityMapper extends QhorusEntityMapper {

    @Inject
    public CdiQhorusEntityMapper(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    CdiQhorusEntityMapper() {}
}
