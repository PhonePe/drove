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

import com.github.dockerjava.api.model.Image;
import com.google.common.base.Strings;
import com.phonepe.drove.common.CommonTestUtils;
import com.phonepe.drove.executor.ExecutorTestingUtils;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.statemachine.StateData;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.junit.jupiter.api.Test;

import static com.phonepe.drove.models.instance.LocalServiceInstanceState.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests {@link LocalServiceInstanceExecutableFetchAction}
 */
@Slf4j
class LocalServiceInstanceExecutableFetchActionTest {
    @Test
    void testFetchSuccess() {
        val action = new LocalServiceInstanceExecutableFetchAction(null);
        val spec = ExecutorTestingUtils.testLocalServiceInstanceSpec("hello-world");
        val ctx = new InstanceActionContext<>(ExecutorTestingUtils.EXECUTOR_ID,
                                              spec,
                                              ExecutorTestingUtils.DOCKER_CLIENT,
                                              false);
        try {
            val response = action.execute(ctx,
                                          StateData.create(PENDING,
                                                           ExecutorTestingUtils.createExecutorLocaLServiceInstanceInfo(
                                                                   spec, 8080)));
            assertEquals(STARTING, response.getState(), "Error:" + response.getError());
        }
        finally {
            action.stop();
            val imageId = ExecutorTestingUtils.DOCKER_CLIENT.listImagesCmd()
                    .withImageNameFilter("hello-world")
                    .exec()
                    .stream()
                    .findAny()
                    .map(Image::getId)
                    .orElse(null);
            log.info("Hello world image id: {}", imageId);
            if (!Strings.isNullOrEmpty(imageId)) {
                ExecutorTestingUtils.DOCKER_CLIENT.removeImageCmd(imageId).withForce(true).exec();
            }
        }
    }

    @Test
    void testStop() {
        val action = new LocalServiceInstanceExecutableFetchAction(null);
        val spec = ExecutorTestingUtils.testLocalServiceInstanceSpec("hello-world");
        val ctx = new InstanceActionContext<>(ExecutorTestingUtils.EXECUTOR_ID,
                                              spec,
                                              ExecutorTestingUtils.DOCKER_CLIENT,
                                              false);
        ctx.getAlreadyStopped().set(true);

        try {
            val response = action.execute(ctx,
                                          StateData.create(PENDING,
                                                           ExecutorTestingUtils.createExecutorLocaLServiceInstanceInfo(
                                                                   spec,
                                                                   8080)));
            assertEquals(STOPPED, response.getState(), "Error:" + response.getError());
        }
        finally {
            action.stop();
        }
    }

    @Test
    void testFetchWrongImage() {
        val spec = ExecutorTestingUtils.testLocalServiceInstanceSpec(CommonTestUtils.LOCAL_SERVICE_IMAGE_NAME +
                                                                             "-invalid");
        val ctx = new InstanceActionContext<>(ExecutorTestingUtils.EXECUTOR_ID,
                                              spec,
                                              ExecutorTestingUtils.DOCKER_CLIENT, false);
        val action = new LocalServiceInstanceExecutableFetchAction(null);
        val response = action.execute(ctx,
                                      StateData.create(PENDING,
                                                       ExecutorTestingUtils.createExecutorLocaLServiceInstanceInfo(spec,
                                                                                                                   8080)));
        assertEquals(PROVISIONING_FAILED, response.getState());
    }

    @Test
    void testFetchInterrupt() {
        val spec = ExecutorTestingUtils.testLocalServiceInstanceSpec(CommonTestUtils.LOCAL_SERVICE_IMAGE_NAME +
                                                                             "-invalid");
        val ctx = new InstanceActionContext<>(ExecutorTestingUtils.EXECUTOR_ID,
                                              spec,
                                              ExecutorTestingUtils.DOCKER_CLIENT, false);
        val action = new LocalServiceInstanceExecutableFetchAction(null);
        Thread.currentThread().interrupt();
        val response = action.execute(ctx,
                                      StateData.create(PENDING,
                                                       ExecutorTestingUtils.createExecutorLocaLServiceInstanceInfo(spec,
                                                                                                                   8080)));
        assertEquals(PROVISIONING_FAILED, response.getState());
    }
}