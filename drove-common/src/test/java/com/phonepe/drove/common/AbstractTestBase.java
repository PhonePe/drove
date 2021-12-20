package com.phonepe.drove.common;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.appform.functionmetrics.FunctionMetricsManager;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeAll;

import java.nio.file.Files;
import java.nio.file.Paths;

import static com.phonepe.drove.common.CommonUtils.configureMapper;

/**
 *
 */
public class AbstractTestBase {
    protected static final ObjectMapper MAPPER = new ObjectMapper();
    protected static final MetricRegistry METRIC_REGISTRY = SharedMetricRegistries.getOrCreate("test");

    @BeforeAll
    static void setupBase() {
        configureMapper(MAPPER);
        FunctionMetricsManager.initialize("drove.test", METRIC_REGISTRY);
    }

    @SneakyThrows
    protected  <T> T readJsonResource(final String jsonFile, final Class<T> clazz) {
        return MAPPER.readValue(Files.readAllBytes(Paths.get(getClass().getResource(jsonFile).toURI())),
                                clazz);
    }
}
