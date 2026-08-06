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

package com.phonepe.drove.executor.statemachine.task.actions;

import static com.phonepe.drove.executor.ExecutorTestingUtils.DOCKER_CLIENT;
import static com.phonepe.drove.executor.ExecutorTestingUtils.EXECUTOR_ID;
import static com.phonepe.drove.executor.ExecutorTestingUtils.createExecutorTaskInfo;
import static com.phonepe.drove.executor.ExecutorTestingUtils.testTaskInstanceSpec;
import static com.phonepe.drove.models.taskinstance.TaskState.RUN_FAILED;
import static com.phonepe.drove.models.taskinstance.TaskState.RUNNING;
import static com.phonepe.drove.models.taskinstance.TaskState.STARTING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;

import com.codahale.metrics.SharedMetricRegistries;
import com.phonepe.drove.common.AbstractTestBase;
import com.phonepe.drove.common.CommonTestUtils;
import com.phonepe.drove.executor.ContainerHelperExtension;
import com.phonepe.drove.executor.ExecutorOptions;
import com.phonepe.drove.executor.discovery.LocalNodeDataManager;
import com.phonepe.drove.executor.resourcemgmt.ResourceConfig;
import com.phonepe.drove.executor.resourcemgmt.ResourceInfo;
import com.phonepe.drove.executor.resourcemgmt.ResourceManager;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.models.info.resources.PhysicalLayout;
import com.phonepe.drove.statemachine.StateData;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import lombok.val;

/**
 * Tests {@link TaskRunAction}
 */
@ExtendWith(ContainerHelperExtension.class)
class TaskRunActionTest extends AbstractTestBase {

    @Test
    void testRun() {
        val spec = testTaskInstanceSpec();
        val resourceManager = mock(ResourceManager.class);
        when(resourceManager.currentState())
                .thenReturn(new ResourceInfo(null, null, null,
                                             new PhysicalLayout(Map.of(0, java.util.Set.of(0, 1, 2, 3)),
                                                                Map.of(0, 1024L),
                                                                Map.of(),
                                                                PhysicalLayout.DEFAULT_CPU_SCALING_FACTOR,
                                                                PhysicalLayout.DEFAULT_MEMORY_SCALING_FACTOR)));
        val nodeStore = mock(LocalNodeDataManager.class);
        when(nodeStore.currentData()).thenReturn(Optional.empty());
        val action = new TaskRunAction(new ResourceConfig(),
                                       ExecutorOptions.DEFAULT,
                                       CommonTestUtils.httpCaller(),
                                       MAPPER,
                                       SharedMetricRegistries.getOrCreate("test"),
                                       resourceManager,
                                       nodeStore);

        val context = new InstanceActionContext<>(EXECUTOR_ID, spec, DOCKER_CLIENT, false);
        val resp = action.execute(context, StateData.create(STARTING, createExecutorTaskInfo(spec)));
        assertEquals(RUNNING, resp.getState());
    }

    @Test
    void testRunFail() {
        val spec = testTaskInstanceSpec(CommonTestUtils.TASK_IMAGE_NAME + "-invalid", Map.of("ITERATIONS", "3"));
        val resourceManager = mock(ResourceManager.class);
        when(resourceManager.currentState())
                .thenReturn(new ResourceInfo(null, null, null,
                                             new PhysicalLayout(Map.of(0, java.util.Set.of(0, 1, 2, 3)),
                                                                Map.of(0, 1024L),
                                                                Map.of(),
                                                                PhysicalLayout.DEFAULT_CPU_SCALING_FACTOR,
                                                                PhysicalLayout.DEFAULT_MEMORY_SCALING_FACTOR)));
        val nodeStore = mock(LocalNodeDataManager.class);
        when(nodeStore.currentData()).thenReturn(Optional.empty());
        val action = new TaskRunAction(new ResourceConfig(),
                                       ExecutorOptions.DEFAULT,
                                       CommonTestUtils.httpCaller(),
                                       MAPPER,
                                       SharedMetricRegistries.getOrCreate("test"),
                                       resourceManager,
                                       nodeStore);
        val context = new InstanceActionContext<>(EXECUTOR_ID, spec, DOCKER_CLIENT, false);
        val resp = action.execute(context, StateData.create(STARTING, createExecutorTaskInfo(spec)));
        assertEquals(RUN_FAILED, resp.getState());
    }
}
