package com.phonepe.drove.executor.statemachine.actions;

import com.phonepe.drove.common.StateData;
import com.phonepe.drove.executor.engine.DockerLabels;
import com.phonepe.drove.executor.engine.InstanceLogHandler;
import com.phonepe.drove.executor.logging.LogBus;
import com.phonepe.drove.executor.model.ExecutorInstanceInfo;
import com.phonepe.drove.executor.statemachine.InstanceAction;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.models.instance.InstanceState;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.slf4j.MDC;

import javax.inject.Inject;
import java.util.Collections;

/**
 *
 */
@Slf4j
public class InstanceRecoveryAction extends InstanceAction {
    private final LogBus logBus;

    @Inject
    public InstanceRecoveryAction(LogBus logBus) {
        this.logBus = logBus;
    }

    @Override
    public void stop() {
        //This is not stoppable
    }

    @Override
    protected StateData<InstanceState, ExecutorInstanceInfo> executeImpl(
            InstanceActionContext context, StateData<InstanceState, ExecutorInstanceInfo> currentState) {
        val client = context.getClient();
        val instanceId = currentState.getData().getInstanceId();
        val container = client.listContainersCmd()
                .withLabelFilter(Collections.singletonMap(DockerLabels.DROVE_INSTANCE_ID_LABEL, instanceId))
                .exec()
                .stream()
                .findAny()
                .orElse(null);
        if (null == container) {
            return StateData.errorFrom(currentState,
                                       InstanceState.STOPPED,
                                       "No container found with drove id: " + instanceId);
        }
        val containerId = container.getId();
        context.setDockerInstanceId(containerId);
        context.setDockerImageId(container.getImageId());
        client.logContainerCmd(containerId)
                .withTail(0)
                .withFollowStream(true)
                .withStdOut(true)
                .withStdErr(true)
                .exec(new InstanceLogHandler(MDC.getCopyOfContextMap(),
                                             currentState.getData().getAppId(),
                                             instanceId,
                                             logBus));

        return StateData.from(currentState, InstanceState.UNREADY);
    }

    @Override
    protected boolean isStopAllowed() {
        return false;
    }
}
