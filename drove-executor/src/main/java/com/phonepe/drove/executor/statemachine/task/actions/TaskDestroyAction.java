package com.phonepe.drove.executor.statemachine.task.actions;

import com.github.dockerjava.api.exception.NotFoundException;
import com.phonepe.drove.common.model.TaskInstanceSpec;
import com.phonepe.drove.executor.model.ExecutorTaskInfo;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.executor.statemachine.task.TaskAction;
import com.phonepe.drove.models.taskinstance.TaskState;
import com.phonepe.drove.statemachine.StateData;
import io.dropwizard.util.Strings;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

/**
 *
 */
@Slf4j
public class TaskDestroyAction extends TaskAction {
    @Override
    protected StateData<TaskState, ExecutorTaskInfo> executeImpl(
            InstanceActionContext<TaskInstanceSpec> context,
            StateData<TaskState, ExecutorTaskInfo> currentState) {
        val containerId = context.getDockerInstanceId();
        if(!Strings.isNullOrEmpty(containerId)) {
            try (val cmd = context.getClient().removeContainerCmd(containerId).withForce(true)) {
                cmd.exec();
                log.info("Container {} removed", containerId);
            }
            catch (NotFoundException e) {
                log.info("Container already stopped");
            }
            catch (Exception e) {
                log.error("Error stopping container " + containerId, e);
                return StateData.errorFrom(currentState, TaskState.DEPROVISIONING,
                                           "Error removing instance: " + e.getMessage());
            }
        }
        return StateData.from(currentState, TaskState.DEPROVISIONING);
    }

    @Override
    protected boolean isStopAllowed() {
        return false;
    }

    @Override
    protected TaskState defaultErrorState() {
        return TaskState.DEPROVISIONING;
    }

    @Override
    public void stop() {
        //Nothing to do here. This job is not stoppable
    }


}
