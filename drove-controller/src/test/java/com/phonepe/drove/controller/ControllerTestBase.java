package com.phonepe.drove.controller;

import com.codahale.metrics.SharedMetricRegistries;
import io.appform.functionmetrics.FunctionMetricsManager;
import org.junit.jupiter.api.BeforeAll;

/**
 *
 */
public class ControllerTestBase {
    @BeforeAll
    public static void init() {
        FunctionMetricsManager.initialize("com.phonepe.drove.controller",
                                          SharedMetricRegistries.getOrCreate("test"));
    }
}
