package com.phonepe.drove.controller.engine;

import com.google.common.base.Strings;
import com.phonepe.drove.common.model.MessageDeliveryStatus;
import com.phonepe.drove.common.model.MessageResponse;
import com.phonepe.drove.common.model.controller.ControllerMessageVisitor;
import com.phonepe.drove.common.model.controller.ExecutorSnapshotMessage;
import com.phonepe.drove.common.model.controller.ExecutorStateReportMessage;
import com.phonepe.drove.common.model.controller.InstanceStateReportMessage;
import com.phonepe.drove.controller.event.DroveEventBus;
import com.phonepe.drove.controller.event.DroveInstanceFailedEvent;
import com.phonepe.drove.controller.statedb.ExecutorStateDB;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.util.Collections;

/**
 *
 */
@Slf4j
public class ControllerMessageHandler implements ControllerMessageVisitor<MessageResponse> {
    private final ExecutorStateDB executorStateDB;
    private final StateUpdater stateUpdater;
    private final DroveEventBus droveEventBus;

    public ControllerMessageHandler(
            ExecutorStateDB executorStateDB,
            StateUpdater stateUpdater, DroveEventBus droveEventBus) {
        this.executorStateDB = executorStateDB;
        this.stateUpdater = stateUpdater;
        this.droveEventBus = droveEventBus;
    }

    @Override
    public MessageResponse visit(InstanceStateReportMessage instanceStateReport) {
        val instanceInfo = instanceStateReport.getInstanceInfo();
        log.info("Received instance update from executor: {}",
                 instanceInfo);
        val status = stateUpdater.updateSingle(instanceStateReport.getResourceSnapshot(),
                                               instanceInfo);
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
    public MessageResponse visit(ExecutorStateReportMessage executorStateReport) {
        val executorState = executorStateReport.getExecutorState();
        val status = executorStateDB.updateExecutorState(executorState.getExecutorId(), executorState);
        return new MessageResponse(executorStateReport.getHeader(),
                                   status
                                   ? MessageDeliveryStatus.ACCEPTED
                                   : MessageDeliveryStatus.FAILED);
    }

    @Override
    public MessageResponse visit(ExecutorSnapshotMessage executorSnapshot) {
        stateUpdater.updateClusterResources(Collections.singletonList(executorSnapshot.getNodeData()));
        return new MessageResponse(executorSnapshot.getHeader(), MessageDeliveryStatus.ACCEPTED);
    }
}
