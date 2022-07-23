package com.phonepe.drove.controller.engine;

import com.phonepe.drove.controller.engine.jobs.BooleanResponseCombiner;
import com.phonepe.drove.controller.engine.jobs.StartTaskJob;
import com.phonepe.drove.controller.engine.jobs.StopTaskJob;
import com.phonepe.drove.controller.resourcemgmt.ClusterResourcesDB;
import com.phonepe.drove.controller.resourcemgmt.InstanceScheduler;
import com.phonepe.drove.controller.statedb.TaskDB;
import com.phonepe.drove.jobexecutor.JobExecutionResult;
import com.phonepe.drove.jobexecutor.JobExecutor;
import com.phonepe.drove.jobexecutor.JobTopology;
import com.phonepe.drove.models.operation.taskops.TaskCreateOperation;
import com.phonepe.drove.models.operation.taskops.TaskKillOperation;
import com.phonepe.drove.models.taskinstance.TaskInstanceInfo;
import com.phonepe.drove.models.taskinstance.TaskInstanceState;
import io.appform.signals.signals.ConsumingFireForgetSignal;
import io.appform.signals.signals.ScheduledSignal;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.inject.Inject;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static java.lang.Boolean.TRUE;

/**
 *
 */
@Slf4j
public class TaskRunner implements Runnable {
    @Getter
    private final String sourceAppName;
    @Getter
    private final String taskId;
    private final JobExecutor<Boolean> jobExecutor;

    private final TaskDB taskDB;
    private final ClusterResourcesDB clusterResourcesDB;

    private final InstanceScheduler scheduler;
    private final ControllerCommunicator communicator;
    private final ControllerRetrySpecFactory retrySpecFactory;
    private final InstanceIdGenerator instanceIdGenerator;
    private final ThreadFactory threadFactory;

    private final ConsumingFireForgetSignal<TaskRunner> completed;

    @Getter
    @Setter
    private Future<?> taskFuture;

    private final AtomicReference<TaskInstanceState> state = new AtomicReference<>();
    private final Lock checkLock = new ReentrantLock();
    private final Condition checkCondition = checkLock.newCondition();
    private final ScheduledSignal checkSignal = new ScheduledSignal(Duration.ofSeconds(5));

    @Inject
    public TaskRunner(
            String sourceAppName, String taskId, JobExecutor<Boolean> jobExecutor, TaskDB taskDB,
            ClusterResourcesDB clusterResourcesDB, InstanceScheduler scheduler,
            ControllerCommunicator communicator,
            ControllerRetrySpecFactory retrySpecFactory,
            InstanceIdGenerator instanceIdGenerator,
            ThreadFactory threadFactory,
            ConsumingFireForgetSignal<TaskRunner> completed) {
        this.sourceAppName = sourceAppName;
        this.taskId = taskId;
        this.jobExecutor = jobExecutor;
        this.taskDB = taskDB;
        this.clusterResourcesDB = clusterResourcesDB;
        this.scheduler = scheduler;
        this.communicator = communicator;
        this.retrySpecFactory = retrySpecFactory;
        this.instanceIdGenerator = instanceIdGenerator;
        this.threadFactory = threadFactory;
        this.completed = completed;
    }

    @Override
    public void run() {
        checkSignal.connect("CHECKER", date -> updateCurrentState());
        monitorTask();
    }

    public String startTask(final TaskCreateOperation taskCreateOperation) {
        val schedulingSessionId = UUID.randomUUID().toString();
        val topology = JobTopology.<Boolean>builder()
                .withThreadFactory(threadFactory)
                .addJob(new StartTaskJob(taskCreateOperation.getSpec(),
                                         taskCreateOperation.getOpSpec(),
                                         scheduler,
                                         taskDB,
                                         communicator,
                                         schedulingSessionId,
                                         retrySpecFactory,
                                         instanceIdGenerator))

                .build();
        return jobExecutor.schedule(topology,
                                         new BooleanResponseCombiner(),
                                         this::handleJobCompletionResult);
    }

    public String stopTask(final TaskKillOperation taskKillOperation) {
        val topology = JobTopology.<Boolean>builder()
                .withThreadFactory(threadFactory)
                .addJob(new StopTaskJob(taskKillOperation.getSourceAppName(),
                                        taskKillOperation.getTaskId(),
                                        taskKillOperation.getOpSpec(),
                                        taskDB,
                                        clusterResourcesDB,
                                        communicator,
                                        retrySpecFactory))
                .build();
        return jobExecutor.schedule(topology,
                                         new BooleanResponseCombiner(),
                                         this::handleJobCompletionResult);
    }

    public void stop() {
        checkSignal.disconnect("CHECKER");
        checkSignal.close();
    }

    private void handleJobCompletionResult(final JobExecutionResult<Boolean> executionResult) {
        if (TRUE.equals(executionResult.getResult())) {
            updateCurrentState();
        }
        else {
            log.error("Unable to start job for {}/{}", sourceAppName, taskId);
            updateCurrentState(TaskInstanceState.LOST);
        }
    }

    private void updateCurrentState() {
        val currState = taskDB.task(sourceAppName, taskId).map(TaskInstanceInfo::getState).orElse(null);
        val existingState = state.get();
        if (existingState != currState) {
            log.info("State for {}/{} changed to {}", sourceAppName, taskId, currState);
            updateCurrentState(currState);
        }
    }

    private void updateCurrentState(TaskInstanceState currState) {
        checkLock.lock();
        try {
            state.set(currState);
            checkCondition.signalAll();
        }
        finally {
            checkLock.unlock();
        }
    }

    private void monitorTask() {
        checkLock.lock();
        try {
            while (state.get() == null || !state.get().isTerminal()) {
                checkCondition.await();
            }
        }
        catch (InterruptedException e) {
            log.info("Monitoring interrupted for {}/{}", sourceAppName, taskId);
            Thread.currentThread().interrupt();
        }
        finally {
            checkLock.unlock();
        }
        completed.dispatch(this);
    }
}
