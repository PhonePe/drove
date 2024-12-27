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

package com.phonepe.drove.executor.statemachine.localservice;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Provides;
import com.google.inject.Stage;
import com.phonepe.drove.common.CommonTestUtils;
import com.phonepe.drove.executor.ExecutorOptions;
import com.phonepe.drove.executor.ExecutorTestingUtils;
import com.phonepe.drove.executor.InjectingLocalServiceInstanceActionFactory;
import com.phonepe.drove.executor.model.ExecutorLocalServiceInstanceInfo;
import com.phonepe.drove.models.instance.LocalInstanceInfo;
import com.phonepe.drove.models.instance.LocalServiceInstanceState;
import com.phonepe.drove.statemachine.StateData;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.junit.jupiter.api.Test;

import javax.inject.Singleton;
import java.util.*;
import java.util.concurrent.Executors;

import static com.phonepe.drove.models.instance.LocalServiceInstanceState.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests {@link LocalServiceInstanceStateMachine}
 */
@Slf4j
class LocalServiceInstanceStateMachineTest {
    @Test
    void test() {
        val instanceSpec = ExecutorTestingUtils.testLocalServiceInstanceSpec();
        val executorId = "EX1";
        val sm = new LocalServiceInstanceStateMachine(UUID.randomUUID().toString(),
                                                     instanceSpec,
                                                     StateData.create(LocalServiceInstanceState.PROVISIONING,
                                                                      new ExecutorLocalServiceInstanceInfo(instanceSpec.getServiceId(),
                                                                                                           instanceSpec.getServiceName(),
                                                                                                           instanceSpec.getInstanceId(),
                                                                                                           executorId,
                                                                                                           new LocalInstanceInfo(
                                                                                                       "localhost",
                                                                                                       Map.of()),
                                                                                                           List.of(),
                                                                                                           Map.of(),
                                                                                                           new Date(),
                                                                                                           new Date())),
                                                     new InjectingLocalServiceInstanceActionFactory(Guice.createInjector(
                                                             Stage.DEVELOPMENT, new AbstractModule() {
                                                                 @Provides
                                                                 @Singleton
                                                                 public CloseableHttpClient httpClient() {
                                                                     return HttpClients.createDefault();
                                                                 }

                                                                 @Provides
                                                                 @Singleton
                                                                 public ExecutorOptions executorOptions() {
                                                                     return ExecutorOptions.DEFAULT;
                                                                 }
                                                             })),
                                                     ExecutorTestingUtils.DOCKER_CLIENT,
                                                     false);
        val stateChanges = new HashSet<>();
        sm.onStateChange().connect(sd -> stateChanges.add(sd.getState()));
        Executors.newSingleThreadExecutor()
                .submit(() -> {
                    try {
                        while (!sm.execute().isTerminal()) {

                        }
                        log.info("State machine execution completed");
                    }
                    catch (Exception e) {
                        log.error("Error running SM: ", e);
                    }
                });
        CommonTestUtils.waitUntil(() -> stateChanges.contains(HEALTHY));
        sm.stop();
        CommonTestUtils.waitUntil(() -> stateChanges.contains(STOPPED));
        assertEquals(EnumSet.of(STOPPED, STOPPING, STARTING, READY, DEPROVISIONING, HEALTHY, UNREADY), stateChanges);
    }
}