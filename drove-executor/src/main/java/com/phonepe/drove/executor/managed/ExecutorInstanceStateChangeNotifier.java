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

package com.phonepe.drove.executor.managed;

import com.phonepe.drove.common.model.MessageDeliveryStatus;
import com.phonepe.drove.common.model.MessageHeader;
import com.phonepe.drove.common.model.controller.InstanceStateReportMessage;
import com.phonepe.drove.common.model.controller.LocalServiceInstanceStateReportMessage;
import com.phonepe.drove.common.model.controller.TaskStateReportMessage;
import com.phonepe.drove.executor.engine.ApplicationInstanceEngine;
import com.phonepe.drove.executor.engine.ExecutorCommunicator;
import com.phonepe.drove.executor.engine.LocalServiceInstanceEngine;
import com.phonepe.drove.executor.engine.TaskInstanceEngine;
import com.phonepe.drove.executor.resourcemgmt.ResourceManager;
import com.phonepe.drove.executor.utils.ExecutorUtils;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.localservice.LocalServiceInstanceInfo;
import com.phonepe.drove.models.taskinstance.TaskInfo;
import io.dropwizard.lifecycle.Managed;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import ru.vyarus.dropwizard.guice.module.installer.order.Order;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 *
 */
@Slf4j
@Singleton
@Order(50)
public class ExecutorInstanceStateChangeNotifier implements Managed {
    private static final String STATE_CHANGE_HANDLER_NAME = "state-change-notifier";
    private final ResourceManager resourceDB;
    private final ExecutorCommunicator communicator;
    private final ApplicationInstanceEngine applicationInstanceEngine;
    private final TaskInstanceEngine taskInstanceEngine;
    private final LocalServiceInstanceEngine localServiceInstanceEngine;

    @Inject
    public ExecutorInstanceStateChangeNotifier(
            ResourceManager resourceDB,
            ExecutorCommunicator communicator,
            ApplicationInstanceEngine applicationInstanceEngine,
            TaskInstanceEngine taskInstanceEngine,
            LocalServiceInstanceEngine localServiceInstanceEngine) {
        this.resourceDB = resourceDB;
        this.communicator = communicator;
        this.applicationInstanceEngine = applicationInstanceEngine;
        this.taskInstanceEngine = taskInstanceEngine;
        this.localServiceInstanceEngine = localServiceInstanceEngine;
    }

    @Override
    public void start() throws Exception {
        applicationInstanceEngine.onStateChange().connect(STATE_CHANGE_HANDLER_NAME, this::handleStateChange);
        taskInstanceEngine.onStateChange().connect(STATE_CHANGE_HANDLER_NAME, this::handleStateChange);
        localServiceInstanceEngine.onStateChange().connect(STATE_CHANGE_HANDLER_NAME, this::handleStateChange);
        log.info("State updater started");
    }

    @Override
    public void stop() throws Exception {
        applicationInstanceEngine.onStateChange().disconnect(STATE_CHANGE_HANDLER_NAME);
        taskInstanceEngine.onStateChange().disconnect(STATE_CHANGE_HANDLER_NAME);
        localServiceInstanceEngine.onStateChange().disconnect(STATE_CHANGE_HANDLER_NAME);
        log.info("State updater stopped");
    }

    private void handleStateChange(final InstanceInfo instanceInfo) {
        log.debug("Received app instance state change notification: {}", instanceInfo);
        val executorId = instanceInfo.getExecutorId();
        val snapshot = ExecutorUtils.executorSnapshot(resourceDB.currentState(), executorId);
        val resp = communicator.send(new InstanceStateReportMessage(MessageHeader.executorRequest(),
                                                                    snapshot,
                                                                    instanceInfo)).getStatus();
        handleResponse(resp);
    }

    private void handleStateChange(final TaskInfo task) {
        log.debug("Received task state change notification: {}", task);
        val executorId = task.getExecutorId();
        val snapshot = ExecutorUtils.executorSnapshot(resourceDB.currentState(), executorId);
        val resp = communicator.send(new TaskStateReportMessage(MessageHeader.executorRequest(),
                                                                snapshot,
                                                                task)).getStatus();
        handleResponse(resp);
    }

    private void handleStateChange(LocalServiceInstanceInfo localServiceInstanceInfo) {
        log.debug("Received local service instance state change notification: {}", localServiceInstanceInfo);
        val executorId = localServiceInstanceInfo.getExecutorId();
        val snapshot = ExecutorUtils.executorSnapshot(resourceDB.currentState(), executorId);
        val resp = communicator.send(new LocalServiceInstanceStateReportMessage(
                MessageHeader.executorRequest(),
                snapshot,
                localServiceInstanceInfo)).getStatus();
        handleResponse(resp);
    }

    private static void handleResponse(MessageDeliveryStatus resp) {
        if (!resp.equals(MessageDeliveryStatus.ACCEPTED)) {
            log.info("Sending message to controller failed with status: {}.", resp);
        }
    }
}
