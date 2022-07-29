package com.phonepe.drove.auth.filters;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import org.junit.jupiter.api.BeforeAll;

/**
 *
 */
public class AbstractAuthTestBase {
    protected static final ObjectMapper MAPPER = new ObjectMapper();

    @BeforeAll
    static void configureMapper() {
        MAPPER.registerModule(new ParameterNamesModule());
        MAPPER.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
        MAPPER.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        MAPPER.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        MAPPER.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        MAPPER.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS);
    }
}
