package com.phonepe.drove.controller.engine;

import com.google.common.base.Strings;
import com.phonepe.drove.common.model.MessageDeliveryStatus;
import com.phonepe.drove.common.model.MessageResponse;
import com.phonepe.drove.common.model.controller.ControllerMessageVisitor;
import com.phonepe.drove.common.model.controller.ExecutorSnapshotMessage;
import com.phonepe.drove.common.model.controller.InstanceStateReportMessage;
import com.phonepe.drove.common.model.controller.TaskStateReportMessage;
import com.phonepe.drove.controller.event.DroveEventBus;
import com.phonepe.drove.controller.event.events.DroveInstanceFailedEvent;
import com.phonepe.drove.controller.event.events.DroveTaskFailedEvent;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.util.List;

/**
 * Handles received remote messages from executor and updates meta etc as necessary based on message type
 */
@Slf4j
public class ControllerMessageHandler implements ControllerMessageVisitor<MessageResponse> {
    private final StateUpdater stateUpdater;
    private final DroveEventBus droveEventBus;

    public ControllerMessageHandler(
            StateUpdater stateUpdater, DroveEventBus droveEventBus) {
        this.stateUpdater = stateUpdater;
        this.droveEventBus = droveEventBus;
    }

    @Override
    public MessageResponse visit(InstanceStateReportMessage instanceStateReport) {
        val instanceInfo = instanceStateReport.getInstanceInfo();
        log.info("Received instance update from executor: {}", instanceInfo);
        val status = stateUpdater.updateSingle(instanceStateReport.getResourceSnapshot(), instanceInfo);
        if (!Strings.isNullOrEmpty(instanceInfo.getErrorMessage())) {
            droveEventBus.publish(new DroveInstanceFailedEvent(instanceInfo.getAppId(),
                                                               instanceInfo.getInstanceId(),
                                                               instanceInfo.getState(),
                                                               instanceInfo.getErrorMessage()));
        }
        return new MessageResponse(instanceStateReport.getHeader(),
                                   status
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
        val instanceInfo = taskStateReportMessage.getInstanceInfo();
        log.info("Received task update from executor: {}", instanceInfo);
        val status = stateUpdater.updateSingle(taskStateReportMessage.getResourceSnapshot(), instanceInfo);
        if (!Strings.isNullOrEmpty(instanceInfo.getErrorMessage())) {
            droveEventBus.publish(new DroveTaskFailedEvent(instanceInfo.getSourceAppName(),
                                                           instanceInfo.getTaskId(),
                                                           instanceInfo.getState(),
                                                           instanceInfo.getErrorMessage()));
        }
        return new MessageResponse(taskStateReportMessage.getHeader(),
                                   status
                                   ? MessageDeliveryStatus.ACCEPTED
                                   : MessageDeliveryStatus.FAILED);    }
}
