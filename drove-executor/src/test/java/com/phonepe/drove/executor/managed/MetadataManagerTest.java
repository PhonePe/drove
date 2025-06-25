/*
 *  Copyright (c) 2025 Original Author(s), PhonePe India Pvt. Ltd.
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

package com.phonepe.drove.executor.managed;

import com.codahale.metrics.MetricRegistry;
import com.phonepe.drove.executor.resourcemgmt.metadata.MetadataConfig;
import com.phonepe.drove.executor.resourcemgmt.metadata.config.*;
import com.phonepe.drove.executor.resourcemgmt.metadata.providers.*;
import lombok.val;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Stream;

class MetadataManagerTest {

    private static final Integer VALUE_MAX_LIMIT = 50;
    private static final String LONG_VALUE_TO_FAIL = "abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmn";

    static MetadataManager getMetadataManager(List<String> blacklistedKeys, Map<String, String> mockedEnv,
                                              ConfiguredMetadataProviderConfig inlineConfigured,
                                              EnvironmentBasedMetadataProviderConfig envBasedConfig,
                                              DynamicCommandBasedMetadataProviderConfig dynamicConfig
    ) {

        Map<String, MetadataProviderConfig> configs = Map.of(
                "inlinedName", inlineConfigured,
                "envName", envBasedConfig,
                "dynCmd", dynamicConfig
        );

        val m = new MetricRegistry();
        Map<String, MetadataProvider> providers = Map.of(
                "inlinedName", new ConfiguredMetadataProvider(m, inlineConfigured),
                "envName", new EnvironmentBasedMetadataProvider(m, envBasedConfig, () -> mockedEnv),
                "dynCmd", new DynamicCommandBasedMetadataProvider(m, dynamicConfig)
        );

        var cfg = MetadataConfig.builder()
                .metadataProviders(configs)
                .blacklistedKeys(blacklistedKeys)
                .valueMaxLimit(VALUE_MAX_LIMIT)
                .build();

        return new MetadataManager(cfg, providers);
    }

    @ParameterizedTest(name = "name: {0} | blackListKeys: {1} | inlineVars: {2} | envVars: {3} | envFilterKeys: {4} | staticCmds: {5} | dynamicCmds: {6} | timeWaitInMillis: {7} | output: {8}")
    @MethodSource("testBasicCase")
    void testBasicMetadata(String testDescription, List<String> blackListKeys, Map<String, String> inlineVars,  Map<String, String> envVars, List<String> envFilterKeys, Map<String, String> staticCmds, Map<String, String> dynamicCmds, long timeWaitInMillis, Map<String, String> output) throws Exception {

        Map<String, CommandBasedConfig> cmds = new HashMap<>();
        staticCmds.entrySet().stream().forEach(e -> cmds.put(e.getKey(), CommandBasedConfig.builder().type(CommandType.STATIC).command(e.getValue()).defaultValue(e.getKey() + ":DEFAULT").build()));
        dynamicCmds.entrySet().stream().forEach(e -> cmds.put(e.getKey(), CommandBasedConfig.builder().type(CommandType.DYNAMIC).command(e.getValue()).defaultValue(e.getKey() + ":DEFAULT").build()));

        var mm = getMetadataManager(blackListKeys,
                envVars,
                ConfiguredMetadataProviderConfig.builder()
                    .metadata(inlineVars)
                    .build(),
                EnvironmentBasedMetadataProviderConfig.builder()
                    .whitelistedVariables(envFilterKeys)
                    .build(),
                DynamicCommandBasedMetadataProviderConfig.builder()
                        .commandRerunInSecs(1)
                        .commands(cmds)
                        .build()
                );

        mm.start();
        if ( timeWaitInMillis > 0 ) {
            Thread.sleep(timeWaitInMillis);
        }
        var res = mm.fetchMetadata();
        Assertions.assertEquals(output.keySet(), res.keySet(), "TESTCASE:" + testDescription);
        output.keySet().stream().forEach(s -> {
            Assertions.assertEquals(output.get(s), res.get(s), "TESTCASE:" + s + ":" + testDescription);
        });
    }

    private static Stream<Arguments> testBasicCase() {
        return Stream.of(
                Arguments.of("basic case", List.of(), Map.of("a", "aVal"), Map.of("b", "bVal"), List.of("b"), Map.of(), Map.of(), 0, Map.of("a", "aVal", "b", "bVal")),
                Arguments.of("basic case with override", List.of(), Map.of("a", "aVal"), Map.of( "a", "aVal2", "b", "bVal"), List.of("a","b"), Map.of(), Map.of(), 0, Map.of("b", "bVal")),
                Arguments.of("PASSWORD filter", List.of(".*PASSWORD.*"), Map.of("a", "aVal", "SOME_PASSWORD", "p"), Map.of("b", "bVal"), List.of("b"), Map.of(), Map.of(), 0, Map.of("a", "aVal", "b", "bVal")),
                Arguments.of("PASSWORD filter with Env prefix filter", List.of(".*PASSWORD.*"), Map.of("a", "aVal", "SOME_PASSWORD", "p"), Map.of("b", "bVal", "b1", "b1Val"), List.of("^b.*"), Map.of(), Map.of(), 0, Map.of("a", "aVal", "b", "bVal", "b1", "b1Val")),
                Arguments.of("basic case with value max limit ignored", List.of(), Map.of("a", "aVal", "aLong", LONG_VALUE_TO_FAIL), Map.of("b", "bVal"), List.of("b"), Map.of(), Map.of(), 0, Map.of("a", "aVal", "b", "bVal")),
                Arguments.of("basic case with basic static and dynamic command", List.of(), Map.of("a", "aVal"), Map.of("b", "bVal"), List.of("b"), Map.of("Y", "date +%Y", "NE_STAT", "NON_EXISTANT_STATIC_CMD"), Map.of("E", "echo 123", "NE_DYN", "NON_EXISTANT_CMD"), 100, Map.of("a", "aVal", "b", "bVal", "Y", "" + LocalDate.now().getYear(), "E", "123","NE_DYN", "NE_DYN:DEFAULT", "NE_STAT", "NE_STAT:DEFAULT"))
        );
    }
}
