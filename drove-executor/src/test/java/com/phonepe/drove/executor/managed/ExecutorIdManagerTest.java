package com.phonepe.drove.executor.managed;

import com.phonepe.drove.common.CommonUtils;
import io.dropwizard.lifecycle.setup.LifecycleEnvironment;
import io.dropwizard.setup.Environment;
import lombok.SneakyThrows;
import lombok.val;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 *
 */
class ExecutorIdManagerTest {

    @Test
    @SneakyThrows
    void testManager() {
        val env = mock(Environment.class);
        val lifecycle = mock(LifecycleEnvironment.class);
        when(env.lifecycle()).thenReturn(lifecycle);
        doNothing().when(lifecycle).addServerLifecycleListener(any());

        val eim = new ExecutorIdManager(env);
        eim.start();
        val server = mock(Server.class);
        val connector = mock(ServerConnector.class);
        when(connector.getLocalPort()).thenReturn(8080);
        when(server.getConnectors()).thenReturn(new Connector[]{connector});
        assertTrue(eim.executorId().isEmpty());
        eim.serverStarted(server);
        assertEquals(CommonUtils.executorId(8080), eim.executorId().orElse(null));
        eim.stop();
    }

}