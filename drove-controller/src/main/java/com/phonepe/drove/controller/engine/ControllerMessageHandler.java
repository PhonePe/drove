package com.phonepe.drove.controller.engine;

import com.phonepe.drove.common.model.MessageDeliveryStatus;
import com.phonepe.drove.common.model.MessageResponse;
import com.phonepe.drove.common.model.controller.ControllerMessageVisitor;
import com.phonepe.drove.common.model.controller.ExecutorSnapshotMessage;
import com.phonepe.drove.common.model.controller.ExecutorStateReportMessage;
import com.phonepe.drove.common.model.controller.InstanceStateReportMessage;
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

    public ControllerMessageHandler(
            ExecutorStateDB executorStateDB,
            StateUpdater stateUpdater) {
        this.executorStateDB = executorStateDB;
        this.stateUpdater = stateUpdater;
    }

    @Override
    public MessageResponse visit(InstanceStateReportMessage instanceStateReport) {
        log.info("Received instance update from executor: {}",
                 instanceStateReport.getResourceSnapshot().getExecutorId());
        val status = stateUpdater.updateSingle(instanceStateReport.getResourceSnapshot(),
                                               instanceStateReport.getInstanceInfo());
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
