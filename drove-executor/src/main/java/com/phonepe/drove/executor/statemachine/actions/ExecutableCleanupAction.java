package com.phonepe.drove.executor.statemachine.actions;

import com.google.common.base.Strings;
import com.phonepe.drove.common.StateData;
import com.phonepe.drove.executor.model.ExecutorInstanceInfo;
import com.phonepe.drove.executor.statemachine.InstanceAction;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.models.instance.InstanceState;
import io.appform.functionmetrics.MonitoredFunction;
import lombok.extern.slf4j.Slf4j;

/**
 *
 */
@Slf4j
public class ExecutableCleanupAction extends InstanceAction {

    @Override
    @MonitoredFunction(method = "execute")
    protected StateData<InstanceState, ExecutorInstanceInfo> executeImpl(
            InstanceActionContext context, StateData<InstanceState, ExecutorInstanceInfo> currentState) {
        if (Strings.isNullOrEmpty(context.getDockerImageId())) {
            log.warn("No docker image id found. Nothing to be cleaned up.");
        }
        else {
/*            val dockerClient = context.getClient();
            try {
                dockerClient.removeImageCmd(context.getDockerImageId())
                        .withForce(true)
                        .exec();
            } catch (Exception e) {
                log.error("Error trying to cleanup image: " + context.getDockerImageId(), e);
                return StateData.create(InstanceState.STOPPED, currentState.getData(), e.getMessage());
            }*/
        }
        return StateData.from(currentState, InstanceState.STOPPED);
    }

    @Override
    protected boolean isStopAllowed() {
        return false;
    }

    @Override
    protected InstanceState defaultErrorState() {
        return InstanceState.STOPPED;
    }

    @Override
    public void stop() {
        //Nothing to do here
    }
}
