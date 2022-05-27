package com.phonepe.drove.executor.statemachine.actions;

import com.github.dockerjava.api.exception.ConflictException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.google.common.base.Strings;
import com.phonepe.drove.executor.ExecutorOptions;
import com.phonepe.drove.executor.model.ExecutorInstanceInfo;
import com.phonepe.drove.executor.statemachine.InstanceAction;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.models.instance.InstanceState;
import io.appform.functionmetrics.MonitoredFunction;
import io.appform.simplefsm.StateData;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.inject.Inject;

/**
 *
 */
@Slf4j
public class ExecutableCleanupAction extends InstanceAction {

    private final ExecutorOptions options;

    @Inject
    public ExecutableCleanupAction(ExecutorOptions options) {
        this.options = options;
    }

    @Override
    @MonitoredFunction(method = "execute")
    protected StateData<InstanceState, ExecutorInstanceInfo> executeImpl(
            InstanceActionContext context, StateData<InstanceState, ExecutorInstanceInfo> currentState) {
        val dockerImageId = context.getDockerImageId();
        if (Strings.isNullOrEmpty(dockerImageId)) {
            log.warn("No docker image id found. Nothing to be cleaned up.");
        }
        else {
            if(options.isCacheImages()) {
                log.info("Skipped image delete for {} as image caching is enabled.", dockerImageId);
            }
            else {
                val dockerClient = context.getClient();
                try {
                    dockerClient.removeImageCmd(dockerImageId)
                            .withForce(true)
                            .exec();
                    log.info("Removed image: {}", dockerImageId);
                }
                catch (NotFoundException e) {
                    log.info("Looks like image {} has already been deleted", dockerImageId);
                }
                catch (ConflictException e) {
                    log.info("Skipping image delete as other containers running with same image {}", dockerImageId);
                }
                catch (Exception e) {
                    log.error("Error trying to cleanup image {}: {}", dockerImageId, e.getMessage());
                    return StateData.create(InstanceState.STOPPED, currentState.getData(), e.getMessage());
                }
            }
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
