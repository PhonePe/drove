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

import com.github.dockerjava.api.model.Image;
import com.google.common.base.Strings;
import com.phonepe.drove.common.AbstractTestBase;
import com.phonepe.drove.common.CommonTestUtils;
import com.phonepe.drove.executor.ExecutorTestingUtils;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.statemachine.StateData;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.junit.jupiter.api.Test;

import static com.phonepe.drove.models.instance.InstanceState.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 *
 */
@Slf4j
class ApplicationExecutableFetchActionTest extends AbstractTestBase {

    @Test
    void testFetchSuccess() {
        val spec = ExecutorTestingUtils.testAppInstanceSpec("hello-world");
        val ctx = new InstanceActionContext<>(ExecutorTestingUtils.EXECUTOR_ID, spec, ExecutorTestingUtils.DOCKER_CLIENT,
                                              false);
        val action = new ApplicationExecutableFetchAction(null);
        val response = action.execute(ctx,
                                      StateData.create(PENDING, ExecutorTestingUtils.createExecutorAppInstanceInfo(spec, 8080)));
        try {
            assertEquals(STARTING, response.getState(), "Error:" + response.getError());
        }
        finally {
            val imageId = ExecutorTestingUtils.DOCKER_CLIENT.listImagesCmd()
                    .withImageNameFilter("hello-world")
                    .exec()
                    .stream()
                    .findAny()
                    .map(Image::getId)
                    .orElse(null);
            log.info("Hello world image id: {}", imageId);
            if(!Strings.isNullOrEmpty(imageId)) {
                ExecutorTestingUtils.DOCKER_CLIENT.removeImageCmd(imageId).withForce(true).exec();
            }
        }
    }

    @Test
    void testFetchWrongImage() {
        val spec = ExecutorTestingUtils.testAppInstanceSpec(CommonTestUtils.APP_IMAGE_NAME + "-invalid");
        val ctx = new InstanceActionContext<>(ExecutorTestingUtils.EXECUTOR_ID,
                                              spec,
                                              ExecutorTestingUtils.DOCKER_CLIENT, false);
        val action = new ApplicationExecutableFetchAction(null);
        val response = action.execute(ctx,
                                      StateData.create(PENDING, ExecutorTestingUtils.createExecutorAppInstanceInfo(spec, 8080)));
        assertEquals(PROVISIONING_FAILED, response.getState());
    }

    @Test
    void testFetchInterrupt() {
        val spec = ExecutorTestingUtils.testAppInstanceSpec(CommonTestUtils.APP_IMAGE_NAME + "-invalid");
        val ctx = new InstanceActionContext<>(ExecutorTestingUtils.EXECUTOR_ID,
                                              spec,
                                              ExecutorTestingUtils.DOCKER_CLIENT, false);
        val action = new ApplicationExecutableFetchAction(null);
        Thread.currentThread().interrupt();
        val response = action.execute(ctx,
                                      StateData.create(PENDING, ExecutorTestingUtils.createExecutorAppInstanceInfo(spec, 8080)));
        assertEquals(PROVISIONING_FAILED, response.getState());
    }
}