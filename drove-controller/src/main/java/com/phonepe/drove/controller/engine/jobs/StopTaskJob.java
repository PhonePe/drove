package com.phonepe.drove.controller.engine.jobs;

import com.phonepe.drove.common.CommonUtils;
import com.phonepe.drove.common.model.MessageDeliveryStatus;
import com.phonepe.drove.common.model.MessageHeader;
import com.phonepe.drove.common.model.executor.ExecutorAddress;
import com.phonepe.drove.common.model.executor.StopTaskMessage;
import com.phonepe.drove.controller.engine.ControllerCommunicator;
import com.phonepe.drove.controller.engine.ControllerRetrySpecFactory;
import com.phonepe.drove.controller.resourcemgmt.ClusterResourcesDB;
import com.phonepe.drove.controller.resourcemgmt.ExecutorHostInfo;
import com.phonepe.drove.controller.statedb.TaskDB;
import com.phonepe.drove.jobexecutor.Job;
import com.phonepe.drove.jobexecutor.JobContext;
import com.phonepe.drove.jobexecutor.JobResponseCombiner;
import com.phonepe.drove.models.operation.ClusterOpSpec;
import com.phonepe.drove.models.taskinstance.TaskInfo;
import com.phonepe.drove.models.taskinstance.TaskState;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.TimeoutExceededException;

import static com.phonepe.drove.controller.utils.ControllerUtils.ensureTaskState;

/**
 *
 */
@Slf4j
public class StopTaskJob implements Job<Boolean> {
    private final String sourceAppName;
    private final String taskId;
    private final ClusterOpSpec clusterOpSpec;
    private final TaskDB taskDB;
    private final ClusterResourcesDB clusterResourcesDB;
    private final ControllerCommunicator communicator;
    private final ControllerRetrySpecFactory retrySpecFactory;

    public StopTaskJob(
            String sourceAppName,
            String taskId,
            ClusterOpSpec clusterOpSpec,
            TaskDB taskDB,
            ClusterResourcesDB clusterResourcesDB,
            ControllerCommunicator communicator,
            ControllerRetrySpecFactory retrySpecFactory) {
        this.sourceAppName = sourceAppName;
        this.taskId = taskId;
        this.clusterOpSpec = clusterOpSpec;
        this.taskDB = taskDB;
        this.clusterResourcesDB = clusterResourcesDB;
        this.communicator = communicator;
        this.retrySpecFactory = retrySpecFactory;
    }

    @Override
    public String jobId() {
        return "stop-task-" + sourceAppName + "-" + taskId;
    }

    @Override
    public void cancel() {
        //This job type cannot be cancelled
    }

    @Override
    public Boolean execute(JobContext<Boolean> context, JobResponseCombiner<Boolean> responseCombiner) {
        val retryPolicy = CommonUtils.<Boolean>policy(retrySpecFactory.jobRetrySpec(), r -> !r);
        val task = taskDB.task(sourceAppName, taskId).orElse(null);
        if(null == task) {
            log.warn("No task found for {}/{}", sourceAppName, taskId);
        }
        try {
            return Failsafe.with(retryPolicy)
                    .onFailure(event -> {
                        val failure = event.getFailure();
                        if (null != failure) {
                            log.error("Error stopping instance for " + sourceAppName, failure);
                        }
                        else {
                            log.error("Error stopping instance for {}. Event: {}", sourceAppName, event);
                        }
                    })
                    .get(() -> stopTask(task, clusterOpSpec));
        }
        catch (TimeoutExceededException e) {
            log.error("Could not stop an instance for {} after retires.", sourceAppName);
        }
        catch (Exception e) {
            log.error("Could not stop an instance for " + sourceAppName + " after retires.", e);
        }
        return false;
    }

    private boolean stopTask(final TaskInfo task, final ClusterOpSpec clusterOpSpec) {
        val executorId = task.getExecutorId();
        val node = clusterResourcesDB.currentSnapshot(executorId)
                .map(ExecutorHostInfo::getNodeData)
                .orElse(null);
        if (null == node) {
            log.warn("No node found in the cluster with ID {}.", executorId);
            return false;
        }
        val stopMessage = new StopTaskMessage(MessageHeader.controllerRequest(),
                                              new ExecutorAddress(executorId,
                                                                      task.getHostname(),
                                                                      node.getPort(),
                                                                      node.getTransportType()),
                                              task.getInstanceId());
        val response = communicator.send(stopMessage);
        log.trace("Sent message to stop task: {}/{}. Message: {}", sourceAppName, taskId, stopMessage);
        if(!response.getStatus().equals(MessageDeliveryStatus.ACCEPTED)) {
            log.warn("Task {} could not be stopped. Sending message failed: {}", task.getExecutorId(), executorId);
            return false;
        }
        return ensureTaskState(taskDB, clusterOpSpec, sourceAppName, taskId, TaskState.STOPPED, retrySpecFactory);
    }
}
