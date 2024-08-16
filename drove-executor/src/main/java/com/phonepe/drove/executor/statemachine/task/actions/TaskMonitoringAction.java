/*
 *  Copyright (c) 2024 Original Author(s), PhonePe India Pvt. Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.phonepe.drove.executor.statemachine.task.actions;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.exception.NotFoundException;
import com.phonepe.drove.common.model.TaskInstanceSpec;
import com.phonepe.drove.executor.model.ExecutorTaskInfo;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.executor.statemachine.task.TaskAction;
import com.phonepe.drove.executor.utils.ExecutorUtils;
import com.phonepe.drove.models.taskinstance.TaskResult;
import com.phonepe.drove.models.taskinstance.TaskState;
import com.phonepe.drove.statemachine.StateData;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.slf4j.MDC;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 *
 */
@Slf4j
public class TaskMonitoringAction extends TaskAction {
    private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    private final Lock checkLock = new ReentrantLock();
    private final Condition stateChanged = checkLock.newCondition();
    private final AtomicReference<TaskResult> result = new AtomicReference<>();
    private final AtomicBoolean stopped = new AtomicBoolean();
    private ScheduledFuture<?> checkerJob;

    @Override
    protected StateData<TaskState, ExecutorTaskInfo> executeImpl(
            InstanceActionContext<TaskInstanceSpec> context,
            StateData<TaskState, ExecutorTaskInfo> currentState) {
        try {
            val currentContext = MDC.getCopyOfContextMap();
            val mdc = null != currentContext
                      ? currentContext
                      : Collections.<String, String>emptyMap();
            val dockerClient = context.getClient();
            val containerId = context.getDockerInstanceId();
            checkerJob = executorService.scheduleWithFixedDelay(() -> checkContainerStatus(dockerClient,
                                                                                           containerId,
                                                                                           mdc),
                                                                0, 1, TimeUnit.SECONDS);
            checkLock.lock();
            monitor(currentState);
            if (stopped.get()) {
                dockerClient.killContainerCmd(containerId).exec();
                return StateData.from(ExecutorUtils.injectResult(currentState,
                                                                 new TaskResult(TaskResult.Status.CANCELLED, -1)),
                                      TaskState.RUN_COMPLETED);
            }
            val runResult = Objects.requireNonNullElse(result.get(),
                                                       new TaskResult(TaskResult.Status.FAILED, -1));
            val exitCode = runResult.getExitCode();
            if (exitCode == 0) {
                return StateData.from(ExecutorUtils.injectResult(currentState, runResult), TaskState.RUN_COMPLETED);
            }
            if (runResult.getStatus().equals(TaskResult.Status.LOST)) {
                return StateData.errorFrom(ExecutorUtils.injectResult(currentState, runResult),
                                           TaskState.RUN_COMPLETED,
                                           "Task instance lost for container: " + containerId);
            }
            return StateData.errorFrom(ExecutorUtils.injectResult(currentState, runResult),
                                       TaskState.RUN_COMPLETED,
                                       "Task instance exited with status: " + exitCode);

        }
        catch (Exception e) {
            return StateData.errorFrom(ExecutorUtils.injectResult(currentState, new TaskResult(TaskResult.Status.FAILED, -1)),
                                       TaskState.RUN_COMPLETED, e.getMessage());
        }
        finally {
            stopJob();
            checkLock.unlock();
        }
    }

    @Override
    protected TaskState defaultErrorState() {
        return TaskState.RUN_COMPLETED;
    }

    @Override
    public void stop() {
        checkLock.lock();
        try {
            stopped.set(true);
            stateChanged.signalAll();
        }
        finally {
            checkLock.unlock();
        }
    }

    private void monitor(StateData<TaskState, ExecutorTaskInfo> currentState) {
        val sourceAppName = currentState.getData().getSourceAppName();
        val taskId = currentState.getData().getTaskId();
        log.info("Starting to monitor {}/{}", sourceAppName, taskId);
        while (!stopped.get() && result.get() == null) {
            try {
                stateChanged.await();
            }
            catch (InterruptedException e) {
                log.info("Task status check monitor thread interrupted.");
                Thread.currentThread().interrupt();
                return;
            }
            if(stopped.get()) {
                log.warn("Monitor for {}/{} stopped as stop was called",
                         sourceAppName, taskId);
            }
            else {
                log.info("Exiting monitor as task result has been generated for {}/{}", sourceAppName, taskId);
            }
        }
    }

    private void stopJob() {
        checkerJob.cancel(true);
        executorService.shutdownNow();
        try {
            val terminated = executorService.awaitTermination(5, TimeUnit.SECONDS);
            if (!terminated) {
                log.warn("Health check executor has not been shut down properly.");
            }
            executorService.shutdownNow();
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void checkContainerStatus(
            final DockerClient dockerClient,
            final String containerId,
            final Map<String, String> mdc) {
        MDC.setContextMap(mdc);
        checkLock.lock();
        try {
            val currState = dockerClient.inspectContainerCmd(containerId)
                    .exec()
                    .getState();
            if (currState != null && !Objects.requireNonNullElse(currState.getRunning(), true)) {
                log.info("Task instance completed with status: {}", currState);
                val exitCode = Objects.requireNonNullElse(currState.getExitCodeLong(),
                                                             -1L);
                val setStatus = result.compareAndSet(null,
                                                     new TaskResult(exitCode == 0L
                                                                    ? TaskResult.Status.SUCCESSFUL
                                                                    : TaskResult.Status.FAILED,
                                                                    exitCode));
                log.debug("Result set status: {}", setStatus);
                stateChanged.signalAll();
            }
        }
        catch (NotFoundException e) {
            log.error("Container {} has gone away", containerId);
            val setStatus = result.compareAndSet(null, new TaskResult(TaskResult.Status.LOST, -1));
            log.debug("Result set status: {}", setStatus);
            stateChanged.signalAll();
        }
        catch (CancellationException e) {
            log.info("Task execution has been cancelled");

        }
        catch (Exception e) {
            log.error("Error checking task status: ", e);
        }
        finally {
            checkLock.unlock();
        }
    }

}
