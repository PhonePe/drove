package com.phonepe.drove.executor.statemachine.common.actions;

import com.github.dockerjava.api.exception.ConflictException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.google.common.base.Strings;
import com.phonepe.drove.common.model.DeploymentUnitSpec;
import com.phonepe.drove.executor.model.DeployedExecutionObjectInfo;
import com.phonepe.drove.executor.statemachine.ExecutorActionBase;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.executor.utils.DockerUtils;
import com.phonepe.drove.statemachine.StateData;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.util.concurrent.atomic.AtomicReference;

/**
 * This will clean up running container.
 * Conflict and image caching is handled.
 */
@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class CommonContainerCleanupAction<E extends DeployedExecutionObjectInfo, S extends Enum<S>,
        T extends DeploymentUnitSpec> extends ExecutorActionBase<E, S, T> {

    @Override
    protected final StateData<S, E> executeImpl(
            InstanceActionContext<T> context,
            StateData<S, E> currentState) {
        val containerId = context.getDockerInstanceId();
        val stoppedState = stoppedState();
        val stateData = new AtomicReference<StateData<S, E>>();
        try {
            stateData.set(preRemoveAction(context, currentState));
        }
        catch (Exception e) {
            log.info("Failure in pre-remove step.", e);
            stateData.set(StateData.from(currentState, stoppedState));
        }
        if (Strings.isNullOrEmpty(containerId)) {
            log.warn("No container id found. Nothing to be cleaned up.");
        }
        else {
            val dockerClient = context.getClient();
            try {
                DockerUtils.cleanupContainer(dockerClient, containerId);
            }
            catch (NotFoundException | ConflictException e) {
                log.info("Looks like container {} has already been deleted", containerId);
            }
            catch (Exception e) {
                log.error("Error trying to cleanup container {}: {}", containerId, e.getMessage());
                return StateData.errorFrom(stateData.get(), stateData.get().getState(), e.getMessage());
            }
        }
        return stateData.get();
    }

    protected abstract StateData<S, E> preRemoveAction(InstanceActionContext<T> context, StateData<S, E> currentState);

    @Override
    protected boolean isStopAllowed() {
        return false;
    }

    @Override
    public void stop() {
        //Nothing to do here
    }
}
