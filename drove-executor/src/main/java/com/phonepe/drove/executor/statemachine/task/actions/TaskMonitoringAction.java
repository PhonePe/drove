package com.phonepe.drove.executor.statemachine.task.actions;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.exception.NotFoundException;
import com.phonepe.drove.common.model.TaskInstanceSpec;
import com.phonepe.drove.executor.model.ExecutorTaskInfo;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.executor.statemachine.task.TaskAction;
import com.phonepe.drove.models.taskinstance.TaskState;
import com.phonepe.drove.statemachine.StateData;
import lombok.Value;
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
            monitor();
            if (stopped.get()) {
                dockerClient.killContainerCmd(containerId).exec();
                return StateData.from(currentState, TaskState.RUN_CANCELLED);
            }
            val runResult = Objects.requireNonNullElse(result.get(), new TaskResult(-1, false));
            if (runResult.isContainerLost()) {
                return StateData.errorFrom(currentState,
                                           TaskState.RUN_FAILED,
                                           "Task instance lost for container: " + containerId);
            }
            val exitCode = runResult.getStatus();
            if (exitCode == 0) {
                return StateData.from(currentState, TaskState.RUN_COMPLETED);
            }
            return StateData.errorFrom(currentState,
                                       TaskState.RUN_FAILED,
                                       "Task instance exited with status: " + exitCode);

        }
        catch (Exception e) {
            return StateData.errorFrom(currentState, TaskState.RUN_FAILED, e.getMessage());
        }
        finally {
            stopJob();
            checkLock.unlock();
        }
    }

    @Override
    protected TaskState defaultErrorState() {
        return TaskState.RUN_FAILED;
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

    private void monitor() {
        while (!stopped.get() && result.get() == null) {
            try {
                stateChanged.await();
            }
            catch (InterruptedException e) {
                log.info("Task status check monitor thread interrupted.");
                Thread.currentThread().interrupt();
                return;
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
                val setStatus = result.compareAndSet(null,
                                                     new TaskResult(Objects.requireNonNullElse(currState.getExitCodeLong(),
                                                                                               -1L), false));
                log.debug("Result set status: {}", setStatus);
                stateChanged.signalAll();
            }
        }
        catch (NotFoundException e) {
            log.error("Container {} has gone away", containerId);
            val setStatus = result.compareAndSet(null, new TaskResult(-1, true));
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

    @Value
    private static class TaskResult {
        long status;
        boolean containerLost;
    }

}
