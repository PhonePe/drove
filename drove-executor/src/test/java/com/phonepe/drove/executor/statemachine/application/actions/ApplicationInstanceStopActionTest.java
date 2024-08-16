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

package com.phonepe.drove.executor.statemachine.application.actions;

import com.codahale.metrics.SharedMetricRegistries;
import com.phonepe.drove.common.AbstractTestBase;
import com.phonepe.drove.common.CommonTestUtils;
import com.phonepe.drove.executor.ContainerHelperExtension;
import com.phonepe.drove.executor.ExecutorOptions;
import com.phonepe.drove.executor.resourcemgmt.ResourceConfig;
import com.phonepe.drove.executor.resourcemgmt.ResourceInfo;
import com.phonepe.drove.executor.resourcemgmt.ResourceManager;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.models.info.resources.PhysicalLayout;
import com.phonepe.drove.models.instance.InstanceState;
import com.phonepe.drove.statemachine.StateData;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Map;
import java.util.Set;

import static com.phonepe.drove.executor.ExecutorTestingUtils.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 *
 */
@ExtendWith(ContainerHelperExtension.class)
class ApplicationInstanceStopActionTest extends AbstractTestBase {

    @Test
    void testStopProper() {
        val spec = testAppInstanceSpec();
        val action = new ApplicationInstanceStopAction();
        val context = new InstanceActionContext<>(EXECUTOR_ID, spec, DOCKER_CLIENT, false);
        val resourceManager = mock(ResourceManager.class);
        when(resourceManager.currentState())
                .thenReturn(new ResourceInfo(null, null,
                                             new PhysicalLayout(Map.of(0, Set.of(0, 1, 2, 3)), Map.of(0, 1024L))));
        val startAction = new ApplicationInstanceRunAction(new ResourceConfig(), ExecutorOptions.DEFAULT,
                                                           CommonTestUtils.httpCaller(),
                                                           MAPPER,
                                                           SharedMetricRegistries.getOrCreate("test"),
                                                           resourceManager);
        val state = startAction.execute(context,
                                        StateData.create(InstanceState.PROVISIONING, createExecutorAppInstanceInfo(spec, 8080)));
        assertEquals(InstanceState.DEPROVISIONING,
                     action.execute(context, StateData.from(state, InstanceState.HEALTHY)).getState());
    }

    @Test
    void testStopInvalidContainer() {
        val spec = testAppInstanceSpec();
        val action = new ApplicationInstanceStopAction();
        val context = new InstanceActionContext<>(EXECUTOR_ID, spec, DOCKER_CLIENT, false);
        context.setDockerInstanceId("INVALID_CONTAINER_ID");
        assertEquals(InstanceState.DEPROVISIONING,
                     action.execute(context, StateData.create(InstanceState.HEALTHY, createExecutorAppInstanceInfo(spec, 8080)))
                             .getState());
    }
    @Test
    void testStopNoContainer() {
        val spec = testAppInstanceSpec();
        val action = new ApplicationInstanceStopAction();
        val context = new InstanceActionContext<>(EXECUTOR_ID, spec, DOCKER_CLIENT, false);
        assertEquals(InstanceState.DEPROVISIONING,
                     action.execute(context, StateData.create(InstanceState.HEALTHY, createExecutorAppInstanceInfo(spec, 8080)))
                             .getState());
    }
}