package com.phonepe.drove.controller.engine;

import com.phonepe.drove.controller.statedb.StateDB;
import com.phonepe.drove.internalmodels.MessageDeliveryStatus;
import com.phonepe.drove.internalmodels.MessageResponse;
import com.phonepe.drove.internalmodels.controller.ControllerMessageVisitor;
import com.phonepe.drove.internalmodels.controller.ExecutorStateReportMessage;
import com.phonepe.drove.internalmodels.controller.InstanceStateReportMessage;
import lombok.val;

/**
 *
 */
public class ControllerMessageHandler implements ControllerMessageVisitor<MessageResponse> {
    private final StateDB stateDB;

    public ControllerMessageHandler(StateDB stateDB) {
        this.stateDB = stateDB;
    }

    @Override
    public MessageResponse visit(InstanceStateReportMessage instanceStateReport) {
        val instanceInfo = instanceStateReport.getInstanceInfo();
        val status = stateDB.updateInstanceState(
                instanceInfo.getAppId(),
                instanceInfo.getInstanceId(),
                instanceInfo);
        return new MessageResponse(instanceStateReport.getHeader(),
                                   status
                                   ? MessageDeliveryStatus.ACCEPTED
                                   : MessageDeliveryStatus.FAILED);
    }

    @Override
    public MessageResponse visit(ExecutorStateReportMessage executorStateReport) {
        val executorState = executorStateReport.getExecutorState();
        val status = stateDB.updateExecutorState(executorState.getExecutorId(),
                                                 executorState);
        return new MessageResponse(executorStateReport.getHeader(),
                                   status
                                   ? MessageDeliveryStatus.ACCEPTED
                                   : MessageDeliveryStatus.FAILED);    }
}
