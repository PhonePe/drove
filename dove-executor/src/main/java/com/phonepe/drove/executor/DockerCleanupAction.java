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
public class DockerCleanupAction extends InstanceAction {

    @Override
    public StateData<InstanceState, InstanceInfo> execute(
            InstanceActionContext context, StateData<InstanceState, InstanceInfo> currentState) {
        if (Strings.isNullOrEmpty(context.getDockerImageId())) {
            log.warn("No docker image id found for {}. Nothing to be cleaned up",
                     currentState.getData().getInstanceId());
        }
        else {
            val dockerClient = context.getClient();
            try {
                dockerClient.removeImageCmd(context.getDockerImageId())
                        .withForce(true)
                        .exec();
            } catch (Exception e) {
                log.error("Error trying to cleanup image: " + context.getDockerImageId(), e);
                return StateData.create(InstanceState.STOPPED, currentState.getData(), e.getMessage());
            }
        }
        return StateData.create(InstanceState.STOPPED, currentState.getData());
    }

    @Override
    public void stop() {

    }
}
