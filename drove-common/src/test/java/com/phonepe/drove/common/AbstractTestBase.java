/*
 *  Copyright (c) 2024 Original Author(s), PhonePe India Pvt. Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.phonepe.drove.common;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.appform.functionmetrics.FunctionMetricsManager;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeAll;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import static com.phonepe.drove.common.CommonUtils.configureMapper;

/**
 *
 */
public class AbstractTestBase {
    public static final ObjectMapper MAPPER = new ObjectMapper();
    protected static final MetricRegistry METRIC_REGISTRY = SharedMetricRegistries.getOrCreate("test");

    @BeforeAll
    static void setupBase() {
        configureMapper(MAPPER);
        FunctionMetricsManager.initialize("drove.test", METRIC_REGISTRY);
    }

    @SneakyThrows
    protected <T> T readJsonResource(final String jsonFile, final Class<T> clazz) {
        return MAPPER.readValue(Files.readAllBytes(Paths.get(getClass().getResource(jsonFile).toURI())),
                                                     clazz);
    }

    @SneakyThrows
    protected List<String> readLinesFromFile(String fileName) {
        return Files.readAllLines(Paths.get(getClass().getResource(fileName).toURI()));
    }
}
