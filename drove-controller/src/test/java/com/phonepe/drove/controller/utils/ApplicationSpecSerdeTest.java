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

package com.phonepe.drove.controller.utils;

import com.phonepe.drove.controller.ControllerTestBase;
import com.phonepe.drove.models.application.ApplicationSpec;
import lombok.val;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Backwards compat test
 */
class ApplicationSpecSerdeTest extends ControllerTestBase {

    private static final String BASE_JSON_TEMPLATE = """
            {
              "name": "test-app",
              "version": "v1-0-0",
              "executable": {
                "type": "DOCKER",
                "url": "test-image:latest",
                "dockerPullTimeout": "15 minutes"
              },
              "exposedPorts": [
                {"name": "main", "port": 8000, "type": "HTTP"}
              ],
              "type": "SERVICE",
              "logging": {
                "type": "LOCAL",
                "maxSize": "10m",
                "maxFiles": 3,
                "compress": true
              },
              "resources": [
                {"type": "CPU", "count": 1},
                {"type": "MEMORY", "sizeInMB": 512}
              ],
              "placementPolicy": {"type": "ANY"},
              "healthcheck": {
                "mode": {
                  "type": "HTTP",
                  "protocol": "HTTP",
                  "portName": "main",
                  "path": "/",
                  "verb": "GET",
                  "successCodes": [200],
                  "connectionTimeout": "1 seconds",
                  "insecure": false
                },
                "timeout": "1 seconds",
                "interval": "3 seconds",
                "attempts": 3,
                "initialDelay": "0 seconds"
              },
              "readiness": {
                "mode": {
                  "type": "HTTP",
                  "protocol": "HTTP",
                  "portName": "main",
                  "path": "/",
                  "verb": "GET",
                  "successCodes": [200],
                  "connectionTimeout": "1 seconds",
                  "insecure": false
                },
                "timeout": "1 seconds",
                "interval": "3 seconds",
                "attempts": 3,
                "initialDelay": "1 seconds"
              }%s
            }""";

    static Stream<Arguments> exposureSpecCases() {
        return Stream.of(
                Arguments.of(
                        "single vhost (old format - object not array)",
                        """
                        , "exposureSpec": {"vhost": "test.appform.io", "portName": "main", "mode": "ALL"}
                        """,
                        1),
                Arguments.of(
                        "multiple vhosts (array format)",
                        """
                        , "exposureSpec": [
                          {"vhost": "test.appform.io", "portName": "main", "mode": "ALL"},
                          {"vhost": "test2.appform.io", "portName": "main", "mode": "ALL"}
                        ]
                        """,
                        2),
                Arguments.of(
                        "explicit null",
                        """
                        , "exposureSpec": null
                        """,
                        0),
                Arguments.of(
                        "field omitted",
                        "",
                        0)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("exposureSpecCases")
    void testExposureSpecSerde(String description, String exposureSpecJson, int expectedCount) throws Exception {
        val json = BASE_JSON_TEMPLATE.formatted(exposureSpecJson);
        val spec = MAPPER.readValue(json, ApplicationSpec.class);

        // Deserialization: verify the field is not null and has expected size
        assertNotNull(spec.getExposureSpec());
        assertEquals(expectedCount, spec.getExposureSpec().size());

        // Serialization: verify the 1.33.x wire format is kept for old clients
        val root = MAPPER.readTree(MAPPER.writeValueAsString(spec));
        val exposureSpecNode = root.get("exposureSpec");
        assertNotNull(exposureSpecNode, "exposureSpec must stay present as JSON null for old clients");
        if (expectedCount == 0) {
            assertTrue(exposureSpecNode.isNull(), "empty exposureSpec must serialize as JSON null");
        }
        else if (expectedCount == 1) {
            assertTrue(exposureSpecNode.isObject(), "single exposureSpec must serialize as a JSON object");
        }
        else {
            assertTrue(exposureSpecNode.isArray(), "multiple exposureSpecs must serialize as a JSON array");
            assertEquals(expectedCount, exposureSpecNode.size());
        }

        // Round-trip: serialize then deserialize, verify exposureSpec survives
        val serialized = MAPPER.writeValueAsString(spec);
        val roundTripped = MAPPER.readValue(serialized, ApplicationSpec.class);
        assertEquals(spec.getExposureSpec(), roundTripped.getExposureSpec());
    }
}
