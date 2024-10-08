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

import com.phonepe.drove.common.CommonUtils;
import com.phonepe.drove.common.model.MessageDeliveryStatus;
import com.phonepe.drove.common.model.MessageResponse;
import com.phonepe.drove.common.model.executor.*;
import com.phonepe.drove.executor.statemachine.BlacklistingManager;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 *
 */
@Slf4j
@Singleton
public class ExecutorMessageHandler implements ExecutorMessageVisitor<MessageResponse> {
    private final ApplicationInstanceEngine engine;
    private final TaskInstanceEngine taskInstanceEngine;
    private final BlacklistingManager blacklistingManager;

    @Inject
    public ExecutorMessageHandler(ApplicationInstanceEngine engine,
                                  TaskInstanceEngine taskInstanceEngine,
                                  BlacklistingManager blacklistingManager) {
        this.engine = engine;
        this.taskInstanceEngine = taskInstanceEngine;
        this.blacklistingManager = blacklistingManager;
    }

    @Override
    public MessageResponse visit(StartInstanceMessage startInstanceMessage) {
        val instanceId = startInstanceMessage.getSpec().getInstanceId();
        if (engine.exists(instanceId)) {
            return new MessageResponse(startInstanceMessage.getHeader(), MessageDeliveryStatus.FAILED);
        }
        try {
            log.info("Starting application instance {}", instanceId);
            return new MessageResponse(startInstanceMessage.getHeader(),
                                       engine.startInstance(startInstanceMessage.getSpec())
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
                                       engine.stopInstance(instanceId)
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
        if (engine.exists(instanceId)) {
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
            blacklistingManager.blacklist();
        }
        catch (Exception e) {
            return new MessageResponse(blacklistExecutorMessage.getHeader(), MessageDeliveryStatus.FAILED);
        }
        return new MessageResponse(blacklistExecutorMessage.getHeader(), MessageDeliveryStatus.ACCEPTED);
    }

    @Override
    public MessageResponse visit(UnBlacklistExecutorMessage unBlacklistExecutorMessage) {
        try {
            blacklistingManager.unblacklist();
        }
        catch (Exception e) {
            return new MessageResponse(unBlacklistExecutorMessage.getHeader(), MessageDeliveryStatus.FAILED);
        }
        return new MessageResponse(unBlacklistExecutorMessage.getHeader(), MessageDeliveryStatus.ACCEPTED);
    }
}
