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
import com.phonepe.drove.models.application.devices.DetailedDeviceSpec;
import com.phonepe.drove.models.info.resources.PhysicalLayout;
import com.phonepe.drove.statemachine.StateData;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.phonepe.drove.executor.ExecutorTestingUtils.*;
import static com.phonepe.drove.models.instance.InstanceState.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 *
 */
@ExtendWith(ContainerHelperExtension.class)
class ApplicationInstanceRunActionTest extends AbstractTestBase {

    @Test
    void testRun() {
        val spec = testAppInstanceSpec();
        val resourceManager = mock(ResourceManager.class);
        when(resourceManager.currentState())
            .thenReturn(new ResourceInfo(null, null,
                                        new PhysicalLayout(Map.of(0, Set.of(0, 1, 2, 3)), Map.of(0, 1024L))));

        val action = new ApplicationInstanceRunAction(new ResourceConfig(),
                                                      ExecutorOptions.DEFAULT,
                                                      CommonTestUtils.httpCaller(),
                                                      MAPPER,
                                                      SharedMetricRegistries.getOrCreate("test"),
                                                      resourceManager);
        val context = new InstanceActionContext<>(EXECUTOR_ID, spec, DOCKER_CLIENT, false);
        val resp = action.execute(context, StateData.create(STARTING, createExecutorAppInstanceInfo(spec, 8080)));
        assertEquals(UNREADY, resp.getState());
    }

    @Test
    void testRunFail() {
        val spec = testAppInstanceSpec(CommonTestUtils.APP_IMAGE_NAME + "-invalid")
                .withDevices(List.of(DetailedDeviceSpec.builder()
                                             .driver("random")
                                             .deviceIds(List.of("/dev/random"))
                                             .build()));
        val resourceManager = mock(ResourceManager.class);
        when(resourceManager.currentState())
                .thenReturn(new ResourceInfo(null, null,
                                             new PhysicalLayout(Map.of(0, Set.of(0, 1, 2, 3)), Map.of(0, 1024L))));
        val action = new ApplicationInstanceRunAction(new ResourceConfig(),
                                                      ExecutorOptions.DEFAULT,
                                                      CommonTestUtils.httpCaller(),
                                                      MAPPER,
                                                      SharedMetricRegistries.getOrCreate("test"),
                                                      resourceManager);
        val context = new InstanceActionContext<>(EXECUTOR_ID, spec, DOCKER_CLIENT, false);
        val resp = action.execute(context, StateData.create(STARTING, createExecutorAppInstanceInfo(spec, 8080)));
        assertEquals(START_FAILED, resp.getState());
    }

}