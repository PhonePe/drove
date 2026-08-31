/*
 *  Copyright (c) 2025 Original Author(s), PhonePe India Pvt. Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  You may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express, implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.phonepe.drove.controller.model;

import com.phonepe.drove.common.AbstractTestBase;
import com.phonepe.drove.models.api.DryRunRuleRequest;
import com.phonepe.drove.models.application.ApplicationSpec;
import com.phonepe.drove.models.application.JobType;
import com.phonepe.drove.models.application.placement.policies.RuleBasedPlacementPolicy;
import com.phonepe.drove.models.interfaces.DeploymentSpec;
import com.phonepe.drove.models.localservice.LocalServiceSpec;
import com.phonepe.drove.models.task.TaskSpec;
import lombok.SneakyThrows;
import lombok.val;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Verifies polymorphic deserialization of {@link DeploymentSpec} subtypes
 * and full deserialization of {@link DryRunRuleRequest} from a JSON payload.
 */
class DeploymentSpecDeserializationTest extends AbstractTestBase {

    static Stream<Arguments> specTypeProvider() {
        return Stream.of(
                Arguments.of(JobType.SERVICE, ApplicationSpec.class),
                Arguments.of(JobType.COMPUTATION, TaskSpec.class),
                Arguments.of(JobType.LOCAL_SERVICE, LocalServiceSpec.class)
        );
    }

    @ParameterizedTest
    @MethodSource("specTypeProvider")
    @SneakyThrows
    void testDeploymentSpecSubtypeResolution(final JobType jobType, final Class<? extends DeploymentSpec> expectedClass) {
        val json = """
                {
                  "name": "test-spec",
                  "version": "00001",
                  "executable": {
                    "type": "DOCKER",
                    "url": "test-image:latest",
                    "dockerPullTimeout": "60 seconds"
                  },
                  "exposedPorts": [
                    {
                      "name": "main",
                      "port": 8080,
                      "type": "HTTP"
                    }
                  ],
                  "resources": [
                    {
                      "type": "CPU",
                      "count": 1
                    },
                    {
                      "type": "MEMORY",
                      "sizeInMB": 256
                    }
                  ],
                  "placementPolicy": {
                    "type": "ANY"
                  },
                  "healthcheck": {
                    "mode": {
                      "type": "HTTP",
                      "protocol": "HTTP",
                      "portName": "main",
                      "path": "/",
                      "verb": "GET",
                      "successCodes": [200],
                      "connectionTimeout": "1 second",
                      "insecure": false
                    },
                    "timeout": "1 second",
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
                      "connectionTimeout": "1 second",
                      "insecure": false
                    },
                    "timeout": "1 second",
                    "interval": "3 seconds",
                    "attempts": 3,
                    "initialDelay": "1 second"
                  },
                  "type": "%s"
                }
                """.formatted(jobType.name());

        val spec = MAPPER.readValue(json, DeploymentSpec.class);

        assertNotNull(spec, "Deserialized spec must not be null");
        assertEquals(expectedClass, spec.getClass(),
                      "DeploymentSpec must resolve to the correct subtype for type=" + jobType);
        assertEquals(jobType, spec.getType(),
                      "getType() must return the correct JobType for type=" + jobType);
    }

    @ParameterizedTest
    @MethodSource("specTypeProvider")
    @SneakyThrows
    void testDeploymentSpecRoundTrip(final JobType jobType, final Class<? extends DeploymentSpec> expectedClass) {
        val json = """
                {
                  "name": "round-trip-spec",
                  "version": "00001",
                  "executable": {
                    "type": "DOCKER",
                    "url": "test-image:latest",
                    "dockerPullTimeout": "60 seconds"
                  },
                  "exposedPorts": [
                    {
                      "name": "main",
                      "port": 8080,
                      "type": "HTTP"
                    }
                  ],
                  "resources": [
                    {
                      "type": "CPU",
                      "count": 1
                    },
                    {
                      "type": "MEMORY",
                      "sizeInMB": 256
                    }
                  ],
                  "placementPolicy": {
                    "type": "ANY"
                  },
                  "healthcheck": {
                    "mode": {
                      "type": "HTTP",
                      "protocol": "HTTP",
                      "portName": "main",
                      "path": "/",
                      "verb": "GET",
                      "successCodes": [200],
                      "connectionTimeout": "1 second",
                      "insecure": false
                    },
                    "timeout": "1 second",
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
                      "connectionTimeout": "1 second",
                      "insecure": false
                    },
                    "timeout": "1 second",
                    "interval": "3 seconds",
                    "attempts": 3,
                    "initialDelay": "1 second"
                  },
                  "type": "%s"
                }
                """.formatted(jobType.name());

        val deserialized = MAPPER.readValue(json, DeploymentSpec.class);
        val reserialized = MAPPER.writeValueAsString(deserialized);
        val reDeserialized = MAPPER.readValue(reserialized, DeploymentSpec.class);

        assertEquals(expectedClass, reDeserialized.getClass(),
                      "Round-trip deserialization must preserve the subtype for type=" + jobType);
        assertEquals(jobType, reDeserialized.getType(),
                      "Round-trip must preserve the JobType for type=" + jobType);
    }

    @Test
    void testDryRunRuleRequestDeserialization() {
        val request = readJsonResource("/dry-run-rule-request.json", DryRunRuleRequest.class);

        assertNotNull(request, "DryRunRuleRequest must not be null");
        assertNotNull(request.getPolicy(), "Policy must not be null");
        assertNotNull(request.getContext(), "Context must not be null");

        val policy = request.getPolicy();
        assertEquals(RuleBasedPlacementPolicy.class, policy.getClass(),
                      "Policy must deserialize as RuleBasedPlacementPolicy");
        assertEquals(RuleBasedPlacementPolicy.RuleType.CEL,
                      ((RuleBasedPlacementPolicy) policy).getRuleType(),
                      "Rule type must be CEL");
        assertEquals("hostname != 'bad-host'",
                      ((RuleBasedPlacementPolicy) policy).getRule(),
                      "Rule expression must match");

        val allocation = request.getContext().getAllocation();
        assertNotNull(allocation, "Node allocation must not be null");
        assertEquals(ApplicationSpec.class, allocation.getSpec().getClass(),
                      "DeploymentSpec in allocation must resolve to ApplicationSpec");
        assertEquals(JobType.SERVICE, allocation.getSpec().getType(),
                      "DeploymentSpec type must be SERVICE");
        assertEquals("AI-00001", allocation.getInstanceId(),
                      "Instance ID must match");

        val session = request.getContext().getSession();
        assertNotNull(session, "Scheduling session must not be null");
        assertEquals("test-session-1", session.getSessionId(),
                      "Session ID must match");

        val environment = request.getContext().getEnvironment();
        assertNotNull(environment, "Scheduling environment must not be null");
        assertEquals(1, environment.getLiveExecutors().size(),
                      "Live executors map must contain one entry");
        assertEquals("us-east-1", environment.getVariables().get("region"),
                      "Environment variable 'region' must match");
    }
}
