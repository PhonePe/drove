package com.phonepe.drove.controller.engine;

import com.phonepe.drove.common.model.MessageDeliveryStatus;
import com.phonepe.drove.common.model.MessageResponse;
import com.phonepe.drove.common.model.controller.ControllerMessageVisitor;
import com.phonepe.drove.common.model.controller.ExecutorSnapshotMessage;
import com.phonepe.drove.common.model.controller.InstanceStateReportMessage;
import com.phonepe.drove.common.model.controller.TaskStateReportMessage;
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

}
