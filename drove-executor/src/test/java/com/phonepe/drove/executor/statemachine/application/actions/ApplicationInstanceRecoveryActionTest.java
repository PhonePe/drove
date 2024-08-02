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

package com.phonepe.drove.executor.statemachine.application.actions;

import com.codahale.metrics.SharedMetricRegistries;
import com.phonepe.drove.common.AbstractTestBase;
import com.phonepe.drove.executor.ContainerHelperExtension;
import com.phonepe.drove.executor.ExecutorTestingUtils;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.models.instance.InstanceState;
import com.phonepe.drove.statemachine.StateData;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 *
 */
@ExtendWith(ContainerHelperExtension.class)
class ApplicationInstanceRecoveryActionTest extends AbstractTestBase {

    @Test
    @SneakyThrows
    void testRecovery() {
        val spec = ExecutorTestingUtils.testAppInstanceSpec();

        var containerId = "";

        val instanceData = ExecutorTestingUtils.createExecutorAppInstanceInfo(spec, 8080);

        val instanceId = spec.getInstanceId();
        ExecutorTestingUtils.startTestAppContainer(spec, instanceData, AbstractTestBase.MAPPER);
        val ir = new ApplicationInstanceRecoveryAction(SharedMetricRegistries.getOrCreate("test"));
        val ctx = new InstanceActionContext<>("E1", spec, ExecutorTestingUtils.DOCKER_CLIENT, false);
        val r = ir.execute(ctx, StateData.create(InstanceState.UNKNOWN, instanceData));
        assertEquals(InstanceState.UNREADY, r.getState());
        ir.stop();
    }

    @Test
    @SneakyThrows
    void testRecoverNone() {
        val spec = ExecutorTestingUtils.testAppInstanceSpec();
        val ir = new ApplicationInstanceRecoveryAction(SharedMetricRegistries.getOrCreate("test"));
        val ctx = new InstanceActionContext<>("E1", spec, ExecutorTestingUtils.DOCKER_CLIENT, false);
        val r = ir.execute(ctx,
                           StateData.create(InstanceState.UNKNOWN,
                                            ExecutorTestingUtils.createExecutorAppInstanceInfo(spec, 8080)));
        assertEquals(InstanceState.STOPPED, r.getState());
    }

}