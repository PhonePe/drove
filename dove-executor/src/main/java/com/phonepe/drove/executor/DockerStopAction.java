package com.phonepe.drove.executor;

import com.google.common.base.Strings;
import com.phonepe.drove.common.StateData;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.instance.InstanceState;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

/**
 *
 */
@Slf4j
public class DockerStopAction extends InstanceAction {
    @Override
    public StateData<InstanceState, InstanceInfo> execute(
            InstanceActionContext context, StateData<InstanceState, InstanceInfo> currentState) {
        if (Strings.isNullOrEmpty(context.getDockerInstanceId())) {
            log.warn("No docker id found for instance {}. Nothing to be done for stop.",
                     currentState.getData().getInstanceId());
        }
        else {
//            context.getLoggerFuture().cancel(true);
            val dockerClient = context.getClient();
            try {
                dockerClient.stopContainerCmd(context.getDockerInstanceId()).exec();
            }
            catch (Exception e) {
                log.error("Error stopping instance: " + context.getDockerInstanceId(), e);
                return StateData.errorFrom(currentState, InstanceState.DEPROVISIONING, e.getMessage());
            }
        }
        return StateData.create(InstanceState.DEPROVISIONING, currentState.getData());
    }

    @Override
    public void stop() {
        //Ignored
    }
}
