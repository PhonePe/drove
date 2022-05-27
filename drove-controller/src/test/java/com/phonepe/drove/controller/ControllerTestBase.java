package com.phonepe.drove.controller;

import com.codahale.metrics.SharedMetricRegistries;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.appform.functionmetrics.FunctionMetricsManager;
import org.junit.jupiter.api.BeforeAll;

import static com.phonepe.drove.common.CommonUtils.configureMapper;

/**
 *
 */
public class ControllerTestBase {
    protected static final ObjectMapper MAPPER = new ObjectMapper();
    @BeforeAll
    public static void init() {
        configureMapper(MAPPER);
        FunctionMetricsManager.initialize("com.phonepe.drove.controller",
                                          SharedMetricRegistries.getOrCreate("test"));
    }
}
