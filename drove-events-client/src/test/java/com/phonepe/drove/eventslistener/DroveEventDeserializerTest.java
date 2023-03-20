package com.phonepe.drove.eventslistener;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import com.phonepe.drove.models.api.ApiResponse;
import com.phonepe.drove.models.events.DroveEvent;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;

/**
 *
 */
@SuppressWarnings({"rawtypes", "deprecation"})
class DroveEventDeserializerTest {

    @Test
    @SneakyThrows
    void testDeser() {
        val json = Files.readString(Paths.get(Objects.requireNonNull(
                getClass().getClassLoader().getResource("events-response.json")).toURI()));

        val mapper = new ObjectMapper();
        mapper.registerModule(new ParameterNamesModule());
        mapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS);
        mapper.registerModule(new SimpleModule().addDeserializer(DroveEvent.class, new DroveEventDeserializer(mapper)));
        val res = mapper.readValue(json, new TypeReference<ApiResponse<List<DroveEvent>>>() {});
        System.out.println(res);
    }

}