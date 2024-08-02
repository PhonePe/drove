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

package com.phonepe.drove.executor.managed;

import com.phonepe.drove.common.CommonTestUtils;
import com.phonepe.drove.common.CommonUtils;
import com.phonepe.drove.executor.ExecutorOptions;
import com.phonepe.drove.models.info.nodedata.NodeTransportType;
import io.dropwizard.lifecycle.setup.LifecycleEnvironment;
import io.dropwizard.setup.Environment;
import lombok.SneakyThrows;
import lombok.val;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

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

        val eim = new ExecutorIdManager(env, ExecutorOptions.DEFAULT);
        eim.start();
        val server = mock(Server.class);
        try(val connector = mock(ServerConnector.class)) {
            when(connector.getLocalPort()).thenReturn(8080);
            when(server.getConnectors()).thenReturn(new Connector[]{connector});
            assertTrue(eim.executorId().isEmpty());
            eim.serverStarted(server);
            assertEquals(CommonUtils.executorId(8080, CommonUtils.hostname()),
                         eim.executorId().orElse(null));
            eim.stop();
        }
    }

    @Test
    @SneakyThrows
    void testManagerDefaultHost() {
        val env = mock(Environment.class);
        val lifecycle = mock(LifecycleEnvironment.class);
        when(env.lifecycle()).thenReturn(lifecycle);
        doNothing().when(lifecycle).addServerLifecycleListener(any());

        val eim = new ExecutorIdManager(env, ExecutorOptions.DEFAULT.withHostname("test-host"));
        eim.start();
        val server = mock(Server.class);
        try(val connector = mock(ServerConnector.class)) {
            when(connector.getLocalPort()).thenReturn(8080);
            when(server.getConnectors()).thenReturn(new Connector[]{connector});
            assertTrue(eim.executorId().isEmpty());
            val callbackDone = new AtomicBoolean(false);
            eim.onHostInfoGenerated().connect(hostInfo -> {
                callbackDone.set(true);
                assertEquals(8080, hostInfo.getPort());
                assertEquals("test-host", hostInfo.getHostname());
                assertEquals(NodeTransportType.HTTP, hostInfo.getTransportType());
                assertEquals(CommonUtils.executorId(8080, "test-host"), hostInfo.getExecutorId());
            });
            eim.serverStarted(server);
            CommonTestUtils.waitUntil(callbackDone::get);
            assertTrue(callbackDone.get());
            eim.stop();
        }
    }

}