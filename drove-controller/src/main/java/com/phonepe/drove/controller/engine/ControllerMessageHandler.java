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

package com.phonepe.drove.controller.engine;

import com.phonepe.drove.common.model.MessageDeliveryStatus;
import com.phonepe.drove.common.model.MessageResponse;
import com.phonepe.drove.common.model.controller.*;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Handles received remote messages from executor and updates meta etc as necessary based on message type
 */
@Slf4j
public class ControllerMessageHandler implements ControllerMessageVisitor<MessageResponse> {
    private final StateUpdater stateUpdater;

    public ControllerMessageHandler(
            StateUpdater stateUpdater) {
        this.stateUpdater = stateUpdater;
    }

    @Override
    public MessageResponse visit(InstanceStateReportMessage instanceStateReport) {
        return new MessageResponse(
                instanceStateReport.getHeader(),
                stateUpdater.updateSingle(instanceStateReport.getResourceSnapshot(),
                                          instanceStateReport.getInstanceInfo())
                ? MessageDeliveryStatus.ACCEPTED
                : MessageDeliveryStatus.FAILED);
    }

    @Override
    public MessageResponse visit(ExecutorSnapshotMessage executorSnapshot) {
        stateUpdater.updateClusterResources(List.of(executorSnapshot.getNodeData()));
        return new MessageResponse(executorSnapshot.getHeader(), MessageDeliveryStatus.ACCEPTED);
    }

    @Override
    public MessageResponse visit(TaskStateReportMessage taskStateReportMessage) {
        return new MessageResponse(
                taskStateReportMessage.getHeader(),
                stateUpdater.updateSingle(taskStateReportMessage.getResourceSnapshot(),
                                          taskStateReportMessage.getInstanceInfo())
                ? MessageDeliveryStatus.ACCEPTED
                : MessageDeliveryStatus.FAILED);
    }

    @Override
    public MessageResponse visit(LocalServiceInstanceStateReportMessage localServiceStateReportMessage) {
        return new MessageResponse(
                localServiceStateReportMessage.getHeader(),
                stateUpdater.updateSingle(localServiceStateReportMessage.getResourceSnapshot(),
                                          localServiceStateReportMessage.getInstanceInfo())
                ? MessageDeliveryStatus.ACCEPTED
                : MessageDeliveryStatus.FAILED);
    }

}
