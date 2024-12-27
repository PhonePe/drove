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

package com.phonepe.drove.executor.statemachine.localservice.actions;

import com.codahale.metrics.SharedMetricRegistries;
import com.phonepe.drove.common.AbstractTestBase;
import com.phonepe.drove.executor.ContainerHelperExtension;
import com.phonepe.drove.executor.ExecutorTestingUtils;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.models.instance.LocalServiceInstanceState;
import com.phonepe.drove.statemachine.StateData;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests {@link LocalServiceInstanceRecoveryAction}
 */
@ExtendWith(ContainerHelperExtension.class)
class LocalServiceInstanceRecoveryActionTest {
    @Test
    @SneakyThrows
    void testRecovery() {
        val spec = ExecutorTestingUtils.testLocalServiceInstanceSpec();

        var containerId = "";

        val instanceData = ExecutorTestingUtils.createExecutorLocaLServiceInstanceInfo(spec, 8080);

        val instanceId = spec.getInstanceId();
        ExecutorTestingUtils.startTestLocalServiceContainer(spec, instanceData, AbstractTestBase.MAPPER);
        val ir = new LocalServiceInstanceRecoveryAction(SharedMetricRegistries.getOrCreate("test"));
        val ctx = new InstanceActionContext<>("E1", spec, ExecutorTestingUtils.DOCKER_CLIENT, false);
        val r = ir.execute(ctx,
                           StateData.create(LocalServiceInstanceState.UNKNOWN, instanceData));
        assertEquals(LocalServiceInstanceState.UNREADY, r.getState());
        ir.stop();
    }

    @Test
    @SneakyThrows
    void testRecoverNone() {
        val spec = ExecutorTestingUtils.testLocalServiceInstanceSpec();

        val ir = new LocalServiceInstanceRecoveryAction(SharedMetricRegistries.getOrCreate("test"));
        val ctx = new InstanceActionContext<>("E1", spec, ExecutorTestingUtils.DOCKER_CLIENT, false);
        val r = ir.execute(ctx,
                           StateData.create(LocalServiceInstanceState.UNKNOWN,
                                            ExecutorTestingUtils.createExecutorLocaLServiceInstanceInfo(spec, 8080)));
        assertEquals(LocalServiceInstanceState.STOPPED, r.getState());
    }
}