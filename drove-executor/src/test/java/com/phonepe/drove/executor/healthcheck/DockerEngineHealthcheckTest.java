package com.phonepe.drove.executor.healthcheck;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.PingCmd;
import com.phonepe.drove.executor.AbstractExecutorBaseTest;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

/**
 *
 */
class DockerEngineHealthcheckTest extends AbstractExecutorBaseTest {

    @Test
    @SneakyThrows
    void testHealthy() {
        val hc = new DockerEngineHealthcheck(DOCKER_CLIENT);
        assertTrue(hc.check().isHealthy());

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