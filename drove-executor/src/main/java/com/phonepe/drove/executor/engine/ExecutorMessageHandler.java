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
    public MessageResponse visit(StartTaskInstanceMessage startTaskInstanceMessage) {
        val instanceId = CommonUtils.instanceId(startTaskInstanceMessage.getSpec());
        if (engine.exists(instanceId)) {
            return new MessageResponse(startTaskInstanceMessage.getHeader(), MessageDeliveryStatus.FAILED);
        }
        try {
            log.info("Starting task instance {}", instanceId);
            return new MessageResponse(startTaskInstanceMessage.getHeader(),
                                       taskInstanceEngine.startInstance(startTaskInstanceMessage.getSpec())
                                       ? MessageDeliveryStatus.ACCEPTED
                                       : MessageDeliveryStatus.FAILED);
        }
        catch (Exception e) {
            log.error("Could not start task: ", e);
            return new MessageResponse(startTaskInstanceMessage.getHeader(), MessageDeliveryStatus.FAILED);
        }
    }

    @Override
    public MessageResponse visit(StopTaskInstanceMessage stopTaskInstanceMessage) {
        val instanceId = stopTaskInstanceMessage.getInstanceId();
        try {
            log.info("Stopping task instance {}", instanceId);
            return new MessageResponse(stopTaskInstanceMessage.getHeader(),
                                       taskInstanceEngine.stopInstance(instanceId)
                                       ? MessageDeliveryStatus.ACCEPTED
                                       : MessageDeliveryStatus.FAILED);
        }
        catch (Exception e) {
            log.error("Could not stop task: ", e);
            return new MessageResponse(stopTaskInstanceMessage.getHeader(), MessageDeliveryStatus.FAILED);
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
