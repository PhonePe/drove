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

package com.phonepe.drove.executor.managed;

import com.codahale.metrics.SharedMetricRegistries;
import com.github.dockerjava.api.model.HostConfig;
import com.google.common.base.Strings;
import com.phonepe.drove.common.AbstractTestBase;
import com.phonepe.drove.common.CommonTestUtils;
import com.phonepe.drove.executor.AbstractExecutorEngineEnabledTestBase;
import com.phonepe.drove.executor.ContainerHelperExtension;
import com.phonepe.drove.executor.ExecutorTestingUtils;
import com.phonepe.drove.executor.discovery.ClusterClient;
import com.phonepe.drove.models.internal.KnownInstancesData;
import io.dropwizard.lifecycle.setup.LifecycleEnvironment;
import io.dropwizard.setup.Environment;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 *
 */
@ExtendWith(ContainerHelperExtension.class)
class InstanceRecoveryTest extends AbstractExecutorEngineEnabledTestBase {

    @Test
    @SneakyThrows
    void testNoContainers() {
        val clusterClient = mock(ClusterClient.class);
        when(clusterClient.lastKnownInstances()).thenReturn(KnownInstancesData.EMPTY);
        val env = mock(Environment.class);
        val lifecycle = new LifecycleEnvironment(SharedMetricRegistries.getOrCreate("test"));
        when(env.lifecycle()).thenReturn(lifecycle);

        val ir = new InstanceRecovery(applicationInstanceEngine,
                                      taskInstanceEngine,
                                      localServiceInstanceEngine,
                                      AbstractTestBase.MAPPER,
                                      ExecutorTestingUtils.DOCKER_CLIENT,
                                      clusterClient,
                                      env);
        ir.start();
        assertEquals(0, applicationInstanceEngine.currentState().size());
        assertEquals(0, taskInstanceEngine.currentState().size());
        ir.stop();
    }

    @Test
    @SneakyThrows
    void testRecoverAppContainers() {
        val appSpec = ExecutorTestingUtils.testAppInstanceSpec();
        val appInstanceData = ExecutorTestingUtils.createExecutorAppInstanceInfo(appSpec, 8080);
        ExecutorTestingUtils.startTestAppContainer(appSpec, appInstanceData, MAPPER);
        val clusterClient = mock(ClusterClient.class);
        when(clusterClient.lastKnownInstances()).thenReturn(KnownInstancesData.EMPTY);
        val env = mock(Environment.class);
        val lifecycle = new LifecycleEnvironment(SharedMetricRegistries.getOrCreate("test"));
        when(env.lifecycle()).thenReturn(lifecycle);

        val ir = new InstanceRecovery(applicationInstanceEngine,
                                      taskInstanceEngine,
                                      localServiceInstanceEngine,
                                      AbstractTestBase.MAPPER,
                                      ExecutorTestingUtils.DOCKER_CLIENT,
                                      clusterClient,
                                      env);
        ir.start();
        ir.serverStarted(null);
        assertEquals(1, applicationInstanceEngine.currentState().size());
        assertEquals(0, taskInstanceEngine.currentState().size());
        ir.stop();
    }


    @Test
    @SneakyThrows
    void testRecoverLocalServiceContainers() {
        val appSpec = ExecutorTestingUtils.testLocalServiceInstanceSpec();
        val appInstanceData = ExecutorTestingUtils.createExecutorLocaLServiceInstanceInfo(appSpec, 8080);
        ExecutorTestingUtils.startTestLocalServiceContainer(appSpec, appInstanceData, MAPPER);
        val clusterClient = mock(ClusterClient.class);
        when(clusterClient.lastKnownInstances()).thenReturn(KnownInstancesData.EMPTY);
        val env = mock(Environment.class);
        val lifecycle = new LifecycleEnvironment(SharedMetricRegistries.getOrCreate("test"));
        when(env.lifecycle()).thenReturn(lifecycle);

        val ir = new InstanceRecovery(applicationInstanceEngine,
                                      taskInstanceEngine,
                                      localServiceInstanceEngine,
                                      AbstractTestBase.MAPPER,
                                      ExecutorTestingUtils.DOCKER_CLIENT,
                                      clusterClient,
                                      env);
        ir.start();
        ir.serverStarted(null);
        assertEquals(0, applicationInstanceEngine.currentState().size());
        assertEquals(1, localServiceInstanceEngine.currentState().size());
        assertEquals(0, taskInstanceEngine.currentState().size());
        ir.stop();
    }

    @Test
    @SneakyThrows
    void testRecoverTaskContainers() {
        val taskSpec = ExecutorTestingUtils.testTaskInstanceSpec();
        val taskInstanceData = ExecutorTestingUtils.createExecutorTaskInfo(taskSpec);
        ExecutorTestingUtils.startTestTaskContainer(taskSpec, taskInstanceData, MAPPER);
        val clusterClient = mock(ClusterClient.class);
        when(clusterClient.lastKnownInstances()).thenReturn(KnownInstancesData.EMPTY);
        val env = mock(Environment.class);
        val lifecycle = new LifecycleEnvironment(SharedMetricRegistries.getOrCreate("test"));
        when(env.lifecycle()).thenReturn(lifecycle);

        val ir = new InstanceRecovery(applicationInstanceEngine,
                                      taskInstanceEngine,
                                      localServiceInstanceEngine,
                                      AbstractTestBase.MAPPER,
                                      ExecutorTestingUtils.DOCKER_CLIENT,
                                      clusterClient,
                                      env);
        ir.start();
        ir.serverStarted(null);
        assertEquals(0, applicationInstanceEngine.currentState().size());
        assertEquals(1, taskInstanceEngine.currentState().size());
        ir.stop();
    }

    @Test
    @SneakyThrows
    void testRecoverNonDroveContainers() {
        var containerId = "";
        try (val createCmd = ExecutorTestingUtils.DOCKER_CLIENT.createContainerCmd(CommonTestUtils.APP_IMAGE_NAME)) {
            containerId = createCmd.withName("test-container")
                    .withHostConfig(new HostConfig().withAutoRemove(true))
                    .exec()
                    .getId();
            ExecutorTestingUtils.DOCKER_CLIENT.startContainerCmd(containerId)
                    .exec();
            val clusterClient = mock(ClusterClient.class);
            when(clusterClient.lastKnownInstances()).thenReturn(KnownInstancesData.EMPTY);
            val env = mock(Environment.class);
            val lifecycle = new LifecycleEnvironment(SharedMetricRegistries.getOrCreate("test"));
            when(env.lifecycle()).thenReturn(lifecycle);

            val ir = new InstanceRecovery(applicationInstanceEngine,
                                          taskInstanceEngine,
                                          localServiceInstanceEngine,
                                          AbstractTestBase.MAPPER,
                                          ExecutorTestingUtils.DOCKER_CLIENT,
                                          clusterClient,
                                          env);
            ir.start();
            ir.serverStarted(null);
            assertEquals(0, applicationInstanceEngine.currentState().size());
            ir.stop();
        }
        finally {
            if (!Strings.isNullOrEmpty(containerId)) {
                ExecutorTestingUtils.DOCKER_CLIENT.stopContainerCmd(containerId).exec();
            }
        }
    }

}