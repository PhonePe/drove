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

package com.phonepe.drove.executor.dockerauth;

import com.phonepe.drove.executor.utils.DockerUtils;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.phonepe.drove.executor.ExecutorTestingUtils.DOCKER_CLIENT;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 */
class DockerAuthConfigTest {
    @Test
    void testDockerAuthConfig() {
        val pullCmd = DOCKER_CLIENT.pullImageCmd("testrepo.io/test-image");
        val authConfig = new DockerAuthConfig()
                .setEntries(Map.of("testrepo.io", new CredentialsDockerAuthConfigEntry("blah", "blahpwd")));
        DockerUtils.populateDockerRegistryAuth(authConfig, pullCmd);
        val pullCmdAuthConfig = pullCmd.getAuthConfig();
        assertNotNull(pullCmdAuthConfig);
        assertEquals("blah", pullCmdAuthConfig.getUsername());
        assertEquals("blahpwd", pullCmdAuthConfig.getPassword());
    }

    @Test
    void testDockerAuthNoConfig() {
        val pullCmd = DOCKER_CLIENT.pullImageCmd("testrepo.io/test-image");
        val authConfig = new DockerAuthConfig();
        DockerUtils.populateDockerRegistryAuth(authConfig, pullCmd);
        val pullCmdAuthConfig = pullCmd.getAuthConfig();
        assertNull(pullCmdAuthConfig);
    }
}