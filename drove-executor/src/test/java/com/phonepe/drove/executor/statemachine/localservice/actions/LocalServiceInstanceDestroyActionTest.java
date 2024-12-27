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

import com.phonepe.drove.executor.ExecutorTestingUtils;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.models.instance.LocalServiceInstanceState;
import com.phonepe.drove.statemachine.StateData;
import lombok.val;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests {@link LocalServiceInstanceDestroyAction}
 */
class LocalServiceInstanceDestroyActionTest {

    @Test
    void test() {
        val spec = ExecutorTestingUtils.testLocalServiceInstanceSpec("hello-world");
        val ctx = new InstanceActionContext<>(ExecutorTestingUtils.EXECUTOR_ID, spec, null, false);
        val action = new LocalServiceInstanceDestroyAction();
        val response = action.execute(ctx,
                                      StateData.create(LocalServiceInstanceState.HEALTHY,
                                                       ExecutorTestingUtils.createExecutorLocaLServiceInstanceInfo(spec, 8080)));
        assertEquals(LocalServiceInstanceState.DEPROVISIONING, response.getState());
    }
}