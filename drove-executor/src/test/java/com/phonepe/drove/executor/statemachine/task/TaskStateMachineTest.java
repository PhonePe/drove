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

package com.phonepe.drove.executor.statemachine.task;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Provides;
import com.google.inject.Stage;
import com.phonepe.drove.common.CommonTestUtils;
import com.phonepe.drove.executor.ExecutorTestingUtils;
import com.phonepe.drove.executor.InjectingTaskActionFactory;
import com.phonepe.drove.executor.model.ExecutorTaskInfo;
import com.phonepe.drove.models.taskinstance.TaskState;
import com.phonepe.drove.statemachine.StateData;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.junit.jupiter.api.Test;

import javax.inject.Singleton;
import java.util.*;
import java.util.concurrent.Executors;

import static com.phonepe.drove.models.taskinstance.TaskState.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 *
 */
@Slf4j
class TaskStateMachineTest {

    @Test
    void test() {
        val instanceSpec = ExecutorTestingUtils.testTaskInstanceSpec();
        val sm = new TaskStateMachine(UUID.randomUUID().toString(),
                                                     instanceSpec,
                                                     StateData.create(TaskState.PROVISIONING,
                                                                      new ExecutorTaskInfo(instanceSpec.getTaskId(),
                                                                                           instanceSpec.getSourceAppName(),
                                                                                           instanceSpec.getInstanceId(),
                                                                                           "EX1",
                                                                                           "localhost",
                                                                                           instanceSpec.getExecutable(),
                                                                                           List.of(),
                                                                                           instanceSpec.getVolumes(),
                                                                                           instanceSpec.getLoggingSpec(),
                                                                                           instanceSpec.getEnv(),
                                                                                           Map.of(),
                                                                                           null,
                                                                                           new Date(),
                                                                                           new Date())),
                                                     new InjectingTaskActionFactory(Guice.createInjector(
                                                             Stage.DEVELOPMENT, new AbstractModule() {
                                                                 @Provides
                                                                 @Singleton
                                                                 public CloseableHttpClient httpClient() {
                                                                     return HttpClients.createDefault();
                                                                 }
                                                             })),
                                                     ExecutorTestingUtils.DOCKER_CLIENT);
        val stateChanges = new HashSet<TaskState>();
        sm.onStateChange().connect(sd -> stateChanges.add(sd.getState()));
        Executors.newSingleThreadExecutor()
                .submit(() -> {
                    try {
                        while (!sm.execute().isTerminal()) {
                            //Wait for changes
                        }
                        log.info("State machine execution completed");
                    }
                    catch (Exception e) {
                        log.error("Error running SM: ", e);
                    }
                });
        CommonTestUtils.waitUntil(() -> stateChanges.contains(TaskState.RUNNING));
        sm.stop();
        CommonTestUtils.waitUntil(() -> stateChanges.contains(STOPPED));
        assertEquals(EnumSet.of(RUNNING, RUN_COMPLETED, STARTING, STOPPED, DEPROVISIONING), stateChanges);
    }

}