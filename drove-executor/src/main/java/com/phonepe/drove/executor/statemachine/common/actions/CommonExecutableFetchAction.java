package com.phonepe.drove.executor.statemachine.common.actions;

import com.phonepe.drove.common.model.DeploymentUnitSpec;
import com.phonepe.drove.executor.dockerauth.DockerAuthConfig;
import com.phonepe.drove.executor.model.DeployedExecutionObjectInfo;
import com.phonepe.drove.executor.statemachine.ExecutorActionBase;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.executor.utils.DockerUtils;
import com.phonepe.drove.models.application.executable.DockerCoordinates;
import com.phonepe.drove.statemachine.StateData;
import io.appform.functionmetrics.MonitoredFunction;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

/**
 *
 */
@Slf4j
public abstract class CommonExecutableFetchAction<E extends DeployedExecutionObjectInfo, S extends Enum<S>, T extends DeploymentUnitSpec> extends ExecutorActionBase<E, S, T> {

        private final DockerAuthConfig dockerAuthConfig;

    protected CommonExecutableFetchAction(DockerAuthConfig dockerAuthConfig) {
        this.dockerAuthConfig = dockerAuthConfig;
    }

    @Override
    @MonitoredFunction(method = "execute")
    protected StateData<S, E> executeImpl(InstanceActionContext<T> context, StateData<S, E> currentState) {
        val instanceSpec = context.getInstanceSpec();
        val client = context.getClient();
        val image = instanceSpec.getExecutable().accept(DockerCoordinates::getUrl);
        log.info("Pulling docker image: {}", image);
        try {
            return DockerUtils.pullImage(client, dockerAuthConfig, instanceSpec, imageId -> {
                context.setDockerImageId(imageId);
                return StateData.from(currentState, startState());
            });
        }
        catch (InterruptedException e) {
            log.info("Action has been interrupted");
            Thread.currentThread().interrupt();
            return StateData.errorFrom(currentState,
                                       defaultErrorState(),
                                       "Pull operation interrupted");
        }
        catch (Exception e) {
            return StateData.errorFrom(currentState,
                                       defaultErrorState(),
                                       "Error while pulling image " + image + ": " + e.getMessage());
        }
    }

    protected abstract S startState();

    @Override
    public void stop() {
        //Nothing to do here
    }
}