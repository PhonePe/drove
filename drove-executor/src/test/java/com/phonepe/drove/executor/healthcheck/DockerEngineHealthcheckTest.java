/*
 *  Copyright (c) 2022 Original Author(s), PhonePe India Pvt. Ltd.
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

package com.phonepe.drove.executor.healthcheck;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.PingCmd;
import com.phonepe.drove.common.AbstractTestBase;
import com.phonepe.drove.executor.ExecutorTestingUtils;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 *
 */
class DockerEngineHealthcheckTest extends AbstractTestBase {

    @Test
    @SneakyThrows
    void testHealthy() {
        val hc = new DockerEngineHealthcheck(ExecutorTestingUtils.DOCKER_CLIENT);
        assertTrue(hc.check().isHealthy());
        assertEquals("docker-engine", hc.getName());
    }

    @Test
    @SneakyThrows
    void testUnhealthy() {
        val client = mock(DockerClient.class);
        val pingCmd = mock(PingCmd.class);
        when(client.pingCmd()).thenReturn(pingCmd);
        when(pingCmd.exec()).thenThrow(new IllegalArgumentException("Error for testing"));
        val hc = new DockerEngineHealthcheck(client);
        assertFalse(hc.check().isHealthy());
    }

}