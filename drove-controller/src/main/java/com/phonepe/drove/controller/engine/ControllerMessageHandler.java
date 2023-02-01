package com.phonepe.drove.controller.engine;

import com.phonepe.drove.common.model.MessageDeliveryStatus;
import com.phonepe.drove.common.model.MessageResponse;
import com.phonepe.drove.common.model.controller.ControllerMessageVisitor;
import com.phonepe.drove.common.model.controller.ExecutorSnapshotMessage;
import com.phonepe.drove.common.model.controller.InstanceStateReportMessage;
import com.phonepe.drove.common.model.controller.TaskStateReportMessage;
import com.phonepe.drove.controller.event.DroveEventBus;
import com.phonepe.drove.controller.event.events.DroveInstanceStateChangeEvent;
import com.phonepe.drove.controller.event.events.DroveStateChangeEvent;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.util.List;

import static com.phonepe.drove.controller.utils.EventUtils.instanceMetadata;

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
        if(status) {
            droveEventBus.publish(new DroveInstanceStateChangeEvent(instanceMetadata(instanceInfo)));
        }
        else {
            log.info("Application instance state report message from {} for {} has been ignored",
                      instanceStateReport.getResourceSnapshot().getExecutorId(),
                     instanceInfo.getInstanceId());
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
        if(status) {
            droveEventBus.publish(new DroveStateChangeEvent(instanceMetadata(instanceInfo)));
        }
        else {
            log.info("Task state update from {} for task {} has been ignored",
                     taskStateReportMessage.getResourceSnapshot().getExecutorId(),
                     taskStateReportMessage.getInstanceInfo().getTaskId());
        }
        return new MessageResponse(taskStateReportMessage.getHeader(),
                                   status
                                   ? MessageDeliveryStatus.ACCEPTED
                                   : MessageDeliveryStatus.FAILED);
    }

}
