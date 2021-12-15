package com.phonepe.drove.common;

import com.codahale.metrics.SharedMetricRegistries;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.appform.functionmetrics.FunctionMetricsManager;
import org.junit.jupiter.api.BeforeAll;

import static com.phonepe.drove.common.CommonUtils.configureMapper;

/**
 *
 */
public class AbstractTestBase {
    protected static final ObjectMapper MAPPER = new ObjectMapper();

    @BeforeAll
    static void setupBase() {
        configureMapper(MAPPER);
        FunctionMetricsManager.initialize("drove.test", SharedMetricRegistries.getOrCreate("test"));
    }
}
