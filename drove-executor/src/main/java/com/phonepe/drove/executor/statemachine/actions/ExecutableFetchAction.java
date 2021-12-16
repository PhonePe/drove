package com.phonepe.drove.executor.statemachine.actions;

import com.phonepe.drove.common.StateData;
import com.phonepe.drove.executor.model.ExecutorInstanceInfo;
import com.phonepe.drove.executor.statemachine.InstanceAction;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.models.application.executable.DockerCoordinates;
import com.phonepe.drove.models.instance.InstanceState;
import io.appform.functionmetrics.MonitoredFunction;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

/**
 *
 */
@Slf4j
public class ExecutableFetchAction extends InstanceAction {
    @Override
    @MonitoredFunction(method = "execute")
    protected StateData<InstanceState, ExecutorInstanceInfo> executeImpl(
            InstanceActionContext context, StateData<InstanceState, ExecutorInstanceInfo> currentState) {
        val instanceSpec = context.getInstanceSpec();
        val client = context.getClient();
        val image = instanceSpec.getExecutable().accept(DockerCoordinates::getUrl);
        log.info("Pulling docker image: {}", image);
        try {
            client.pullImageCmd(image).exec(new ImagePullProgressHandler(image)).awaitCompletion();
            val imageId = client.inspectImageCmd(image).exec().getId();
            log.info("Pulled image {} with image ID: {}", image, imageId);
            context.setDockerImageId(image);
            return StateData.create(InstanceState.STARTING, currentState.getData());
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return StateData.errorFrom(currentState,
                                       InstanceState.PROVISIONING_FAILED,
                                       "Pull operation interrupted");
        }
        catch (Exception e) {
            return StateData.errorFrom(currentState,
                                       InstanceState.PROVISIONING_FAILED,
                                       "Error while pulling image " + image + ": " + e.getMessage());
        }
    }

    @Override
    public void stop() {
        //Nothing to do here
    }

}
