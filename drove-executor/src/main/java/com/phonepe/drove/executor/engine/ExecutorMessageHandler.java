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

package com.phonepe.drove.executor.engine;

import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.phonepe.drove.common.CommonUtils;
import com.phonepe.drove.common.model.MessageDeliveryStatus;
import com.phonepe.drove.common.model.MessageResponse;
import com.phonepe.drove.common.model.executor.BlacklistExecutorFinalizeMessage;
import com.phonepe.drove.common.model.executor.BlacklistExecutorMessage;
import com.phonepe.drove.common.model.executor.ExecutorMessageVisitor;
import com.phonepe.drove.common.model.executor.StartInstanceMessage;
import com.phonepe.drove.common.model.executor.StartLocalServiceInstanceMessage;
import com.phonepe.drove.common.model.executor.StartTaskMessage;
import com.phonepe.drove.common.model.executor.StopInstanceMessage;
import com.phonepe.drove.common.model.executor.StopLocalServiceInstanceMessage;
import com.phonepe.drove.common.model.executor.StopTaskMessage;
import com.phonepe.drove.common.model.executor.UnBlacklistExecutorMessage;
import com.phonepe.drove.executor.managed.ExecutorStateManager;
import com.phonepe.drove.models.info.nodedata.ExecutorState;

import lombok.val;
import lombok.extern.slf4j.Slf4j;

/**
 *
 */
@Slf4j
@Singleton
public class ExecutorMessageHandler implements ExecutorMessageVisitor<MessageResponse> {
    private final ApplicationInstanceEngine applicationInstanceEngine;
    private final TaskInstanceEngine taskInstanceEngine;
    private final LocalServiceInstanceEngine localServiceInstanceEngine;
    private final ExecutorStateManager executorStateManager;

    @Inject
    public ExecutorMessageHandler(
            ApplicationInstanceEngine applicationInstanceEngine,
            TaskInstanceEngine taskInstanceEngine,
            LocalServiceInstanceEngine localServiceInstanceEngine,
            ExecutorStateManager executorStateManager) {
        this.applicationInstanceEngine = applicationInstanceEngine;
        this.taskInstanceEngine = taskInstanceEngine;
        this.localServiceInstanceEngine = localServiceInstanceEngine;
        this.executorStateManager = executorStateManager;
    }

    @Override
    public MessageResponse visit(StartInstanceMessage startInstanceMessage) {
        val instanceId = startInstanceMessage.getSpec().getInstanceId();
        if (applicationInstanceEngine.exists(instanceId)) {
            return new MessageResponse(startInstanceMessage.getHeader(), MessageDeliveryStatus.FAILED);
        }
        if (!ensureRequiredState(ExecutorState.ACTIVE)) {
            log.error("Cannot start application instance {} since executor is not in ACTIVE state", instanceId);
            return new MessageResponse(startInstanceMessage.getHeader(), MessageDeliveryStatus.FAILED);
        }
        try {
            log.info("Starting application instance {}", instanceId);
            return new MessageResponse(startInstanceMessage.getHeader(),
                                       applicationInstanceEngine.startInstance(startInstanceMessage.getSpec())
                                       ? MessageDeliveryStatus.ACCEPTED
                                       : MessageDeliveryStatus.FAILED);
        }
        catch (Exception e) {
            log.error("Could not start application: ", e);
            return new MessageResponse(startInstanceMessage.getHeader(), MessageDeliveryStatus.FAILED);
        }
    }

    @Override
    public MessageResponse visit(StopInstanceMessage stopInstanceMessage) {
        val instanceId = stopInstanceMessage.getInstanceId();
        try {
            log.info("Stopping application instance {}", instanceId);
            return new MessageResponse(stopInstanceMessage.getHeader(),
                                       applicationInstanceEngine.stopInstance(instanceId)
                                       ? MessageDeliveryStatus.ACCEPTED
                                       : MessageDeliveryStatus.FAILED);
        }
        catch (Exception e) {
            log.error("Could not stop application: ", e);
            return new MessageResponse(stopInstanceMessage.getHeader(), MessageDeliveryStatus.FAILED);
        }
    }

    @Override
    public MessageResponse visit(StartTaskMessage startTaskMessage) {
        val instanceId = CommonUtils.instanceId(startTaskMessage.getSpec());
        if (taskInstanceEngine.exists(instanceId)) {
            return new MessageResponse(startTaskMessage.getHeader(), MessageDeliveryStatus.FAILED);
        }
        if (!ensureRequiredState(ExecutorState.ACTIVE)) {
            log.error("Cannot start task {} since executor is not in ACTIVE state", instanceId);
            return new MessageResponse(startTaskMessage.getHeader(), MessageDeliveryStatus.FAILED);
        }
        if (!ensureRequiredState(ExecutorState.ACTIVE)) {
            log.error("Cannot start task {} since executor is not in ACTIVE state", instanceId);
            return new MessageResponse(startTaskMessage.getHeader(), MessageDeliveryStatus.FAILED);
        }
        try {
            log.info("Starting task instance {}", instanceId);
            return new MessageResponse(startTaskMessage.getHeader(),
                                       taskInstanceEngine.startInstance(startTaskMessage.getSpec())
                                       ? MessageDeliveryStatus.ACCEPTED
                                       : MessageDeliveryStatus.FAILED);
        }
        catch (Exception e) {
            log.error("Could not start task: ", e);
            return new MessageResponse(startTaskMessage.getHeader(), MessageDeliveryStatus.FAILED);
        }
    }

    @Override
    public MessageResponse visit(StopTaskMessage stopTaskMessage) {
        val instanceId = stopTaskMessage.getInstanceId();
        try {
            log.info("Stopping task instance {}", instanceId);
            return new MessageResponse(stopTaskMessage.getHeader(),
                                       taskInstanceEngine.stopInstance(instanceId)
                                       ? MessageDeliveryStatus.ACCEPTED
                                       : MessageDeliveryStatus.FAILED);
        }
        catch (Exception e) {
            log.error("Could not stop task: ", e);
            return new MessageResponse(stopTaskMessage.getHeader(), MessageDeliveryStatus.FAILED);
        }
    }

    @Override
    public MessageResponse visit(BlacklistExecutorMessage blacklistExecutorMessage) {
        try {
            executorStateManager.requestBlacklist();
        }
        catch (Exception e) {
            return new MessageResponse(blacklistExecutorMessage.getHeader(), MessageDeliveryStatus.FAILED);
        }
        return new MessageResponse(blacklistExecutorMessage.getHeader(), MessageDeliveryStatus.ACCEPTED);
    }

    @Override
    public MessageResponse visit(BlacklistExecutorFinalizeMessage blacklistExecutorFinalizeMessage) {
        try {
            executorStateManager.markBlacklisted();
        }
        catch (Exception e) {
            return new MessageResponse(blacklistExecutorFinalizeMessage.getHeader(), MessageDeliveryStatus.FAILED);
        }
        return new MessageResponse(blacklistExecutorFinalizeMessage.getHeader(), MessageDeliveryStatus.ACCEPTED);
    }

    @Override
    public MessageResponse visit(UnBlacklistExecutorMessage unBlacklistExecutorMessage) {
        try {
            executorStateManager.unblacklist();
        }
        catch (Exception e) {
            return new MessageResponse(unBlacklistExecutorMessage.getHeader(), MessageDeliveryStatus.FAILED);
        }
        return new MessageResponse(unBlacklistExecutorMessage.getHeader(), MessageDeliveryStatus.ACCEPTED);
    }

    @Override
    public MessageResponse visit(StartLocalServiceInstanceMessage startLocalServiceInstanceMessage) {
        val instanceId = startLocalServiceInstanceMessage.getSpec().getInstanceId();
        if (localServiceInstanceEngine.exists(instanceId)) {
            return new MessageResponse(startLocalServiceInstanceMessage.getHeader(), MessageDeliveryStatus.FAILED);
        }
        if (!ensureRequiredState(ExecutorState.ACTIVE, ExecutorState.UNREADY)) {
            log.error("Cannot start local service instance {} since executor is not in required state", instanceId);
            return new MessageResponse(startLocalServiceInstanceMessage.getHeader(), MessageDeliveryStatus.FAILED);
        }
        try {
            log.info("Starting local service instance {}", instanceId);
            return new MessageResponse(startLocalServiceInstanceMessage.getHeader(),
                                       localServiceInstanceEngine.startInstance(startLocalServiceInstanceMessage.getSpec())
                                       ? MessageDeliveryStatus.ACCEPTED
                                       : MessageDeliveryStatus.FAILED);
        }
        catch (Exception e) {
            log.error("Could not start local service instance: ", e);
            return new MessageResponse(startLocalServiceInstanceMessage.getHeader(), MessageDeliveryStatus.FAILED);
        }
    }

    @Override
    public MessageResponse visit(StopLocalServiceInstanceMessage stopLocalServiceInstanceMessage) {
        val instanceId = stopLocalServiceInstanceMessage.getInstanceId();
        try {
            log.info("Stopping local service instance {}", instanceId);
            return new MessageResponse(stopLocalServiceInstanceMessage.getHeader(),
                                       localServiceInstanceEngine.stopInstance(instanceId)
                                       ? MessageDeliveryStatus.ACCEPTED
                                       : MessageDeliveryStatus.FAILED);
        }
        catch (Exception e) {
            log.error("Could not stop local service instance: ", e);
            return new MessageResponse(stopLocalServiceInstanceMessage.getHeader(), MessageDeliveryStatus.FAILED);
        }
    }

    private boolean ensureRequiredState(final ExecutorState... requiredStates) {
        final var lookup = Set.of(requiredStates);
        final val currentState = executorStateManager.currentState();
        if (!lookup.contains(currentState)) {
            log.warn("Current state is {}, required state is {}", currentState, lookup);
            return false;
        }
        return true;
    }
}
