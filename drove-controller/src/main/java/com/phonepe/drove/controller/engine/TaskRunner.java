package com.phonepe.drove.controller.engine;

import com.phonepe.drove.common.net.HttpCaller;
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
import com.phonepe.drove.models.taskinstance.TaskInfo;
import com.phonepe.drove.models.taskinstance.TaskResult;
import com.phonepe.drove.models.taskinstance.TaskState;
import io.appform.signals.signals.ConsumingFireForgetSignal;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.inject.Inject;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static com.phonepe.drove.models.taskinstance.TaskState.ACTIVE_STATES;
import static com.phonepe.drove.models.taskinstance.TaskState.LOST;
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
    private final HttpCaller httpCaller;

    @Getter
    @Setter
    private Future<?> taskFuture;

    private final AtomicReference<TaskState> state = new AtomicReference<>();
    private final Lock checkLock = new ReentrantLock();
    private final Condition checkCondition = checkLock.newCondition();

    @Inject
    public TaskRunner(
            String sourceAppName,
            String taskId,
            JobExecutor<Boolean> jobExecutor,
            TaskDB taskDB,
            ClusterResourcesDB clusterResourcesDB,
            InstanceScheduler scheduler,
            ControllerCommunicator communicator,
            ControllerRetrySpecFactory retrySpecFactory,
            InstanceIdGenerator instanceIdGenerator,
            ThreadFactory threadFactory,
            ConsumingFireForgetSignal<TaskRunner> completed, HttpCaller httpCaller) {
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
        this.httpCaller = httpCaller;
    }

    @Override
    public void run() {
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
                                         instanceIdGenerator,
                                         httpCaller))

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
        //Nothing to do here
    }

    public Optional<TaskState> updateCurrentState() {
        val validUpdateDate = new Date(new Date().getTime() - TaskDB.MAX_ACCEPTABLE_UPDATE_INTERVAL.toMillis());
        val instance = taskDB.task(sourceAppName, taskId).orElse(null);
        if(null == instance) {
            return Optional.empty();
        }
        if(ACTIVE_STATES.contains(instance.getState())
                && instance.getUpdated().before(validUpdateDate)) {
            log.info("Stale instance detected: {}/{}", sourceAppName, taskId);
            val updateStatus = taskDB.updateTask(sourceAppName, taskId, convertToLost(instance));
            log.info("Stale mark status for task {}/{} is {}", sourceAppName, taskId, updateStatus);
            if(updateStatus) {
                updateCurrentState(LOST);
            }
            return Optional.of(LOST);
        }
        val currState = instance.getState();
        val existingState = state.get();
        if (existingState != currState) {
            log.info("State for {}/{} changed to {}", sourceAppName, taskId, currState);
            updateCurrentState(currState);
        }
        return Optional.ofNullable(currState);
    }

    private void handleJobCompletionResult(final JobExecutionResult<Boolean> executionResult) {
        if (TRUE.equals(executionResult.getResult())) {
            updateCurrentState();
        }
        else {
            log.error("Unable to start job for {}/{}", sourceAppName, taskId);
            updateCurrentState(TaskState.LOST);
        }
    }

    private void updateCurrentState(TaskState currState) {
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

    private TaskInfo convertToLost(TaskInfo instance) {
        return new TaskInfo(instance.getSourceAppName(),
                            instance.getTaskId(),
                            instance.getInstanceId(),
                            instance.getExecutorId(),
                            instance.getHostname(),
                            instance.getExecutable(),
                            instance.getResources(),
                            instance.getVolumes(),
                            instance.getLoggingSpec(),
                            instance.getEnv(),
                            TaskState.LOST,
                            instance.getMetadata(),
                            new TaskResult(TaskResult.Status.LOST, -1),
                            "Instance lost",
                            instance.getCreated(),
                            new Date());
    }
}
