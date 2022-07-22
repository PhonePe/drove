package com.phonepe.drove.executor.statemachine.task.actions;

import com.phonepe.drove.common.model.TaskInstanceSpec;
import com.phonepe.drove.executor.model.ExecutorTaskInstanceInfo;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.executor.statemachine.task.TaskInstanceAction;
import com.phonepe.drove.models.taskinstance.TaskInstanceState;
import com.phonepe.drove.statemachine.StateData;
import io.dropwizard.util.Strings;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

/**
 *
 */
@Slf4j
public class TaskInstanceDestroyAction extends TaskInstanceAction {
    @Override
    protected StateData<TaskInstanceState, ExecutorTaskInstanceInfo> executeImpl(
            InstanceActionContext<TaskInstanceSpec> context,
            StateData<TaskInstanceState, ExecutorTaskInstanceInfo> currentState) {
        val containerId = context.getDockerInstanceId();
        if(!Strings.isNullOrEmpty(containerId)) {
            try (val cmd = context.getClient().removeContainerCmd(containerId).withForce(true)) {
                cmd.exec();
                log.info("Container {} removed", containerId);
            }
            catch (Exception e) {
                log.error("Error stopping container " + containerId, e);
                return StateData.errorFrom(currentState, TaskInstanceState.DEPROVISIONING,
                                           "Error removing instance: " + e.getMessage());
            }
        }
        return StateData.from(currentState, TaskInstanceState.DEPROVISIONING);
    }

    @Override
    protected boolean isStopAllowed() {
        return false;
    }

    @Override
    protected TaskInstanceState defaultErrorState() {
        return TaskInstanceState.DEPROVISIONING;
    }

    @Override
    public void stop() {
        //Nothing to do here. This job is not stoppable
    }


}
