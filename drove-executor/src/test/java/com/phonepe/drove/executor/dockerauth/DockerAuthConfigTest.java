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