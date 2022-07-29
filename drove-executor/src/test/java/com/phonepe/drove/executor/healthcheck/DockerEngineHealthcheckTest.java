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