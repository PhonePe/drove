package com.phonepe.drove.controller.engine;

import com.phonepe.drove.common.CommonUtils;
import com.phonepe.drove.controller.resourcemgmt.ClusterResourcesDB;
import com.phonepe.drove.controller.resourcemgmt.InstanceScheduler;
import com.phonepe.drove.controller.statedb.ClusterStateDB;
import com.phonepe.drove.controller.statedb.TaskDB;
import com.phonepe.drove.jobexecutor.JobExecutor;
import com.phonepe.drove.models.operation.ClusterOpSpec;
import com.phonepe.drove.models.operation.TaskOperation;
import com.phonepe.drove.models.operation.TaskOperationVisitor;
import com.phonepe.drove.models.operation.taskops.TaskCreateOperation;
import com.phonepe.drove.models.operation.taskops.TaskKillOperation;
import com.phonepe.drove.models.taskinstance.TaskInfo;
import com.phonepe.drove.models.taskinstance.TaskState;
import io.appform.signals.signals.ConsumingFireForgetSignal;
import io.appform.signals.signals.ScheduledSignal;
import io.dropwizard.util.Strings;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;

/**
 *
 */
@Singleton
@Slf4j
public class TaskEngine {
    private final TaskDB taskDB;
    private final ClusterResourcesDB clusterResourcesDB;

    private final InstanceScheduler scheduler;
    private final ControllerCommunicator communicator;
    private final ControllerRetrySpecFactory retrySpecFactory;
    private final InstanceIdGenerator instanceIdGenerator;
    private final ThreadFactory threadFactory;
    private final ExecutorService executorService;
    private final JobExecutor<Boolean> jobExecutor;
    private final ClusterStateDB clusterStateDB;

    private final ConsumingFireForgetSignal<TaskRunner> completed = new ConsumingFireForgetSignal<>();

    private final Map<String, TaskRunner> runners = new ConcurrentHashMap<>();

    private final ScheduledSignal checkSignal = new ScheduledSignal(Duration.ofSeconds(5));

    @Inject
    public TaskEngine(
            TaskDB taskDB,
            ClusterResourcesDB clusterResourcesDB,
            InstanceScheduler scheduler,
            ControllerCommunicator communicator,
            ControllerRetrySpecFactory retrySpecFactory,
            InstanceIdGenerator instanceIdGenerator,
            @Named("JobLevelThreadFactory") ThreadFactory threadFactory,
            @Named("TaskThreadPool") ExecutorService executorService,
            JobExecutor<Boolean> jobExecutor,
            ClusterStateDB clusterStateDB) {
        this.taskDB = taskDB;
        this.clusterResourcesDB = clusterResourcesDB;
        this.scheduler = scheduler;
        this.communicator = communicator;
        this.retrySpecFactory = retrySpecFactory;
        this.instanceIdGenerator = instanceIdGenerator;
        this.threadFactory = threadFactory;
        this.executorService = executorService;
        this.jobExecutor = jobExecutor;
        this.clusterStateDB = clusterStateDB;
        this.completed.connect(taskRunner -> {
            val runTaskId = genRunTaskId(taskRunner.getSourceAppName(), taskRunner.getTaskId());
            try {
                taskRunner.stop();
                runners.remove(runTaskId);
                taskRunner.getTaskFuture().get();
                log.info("Task {}/{} completed", taskRunner.getSourceAppName(), taskRunner.getSourceAppName());
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            catch (ExecutionException e) {
                log.error("Error in thread for " + runTaskId, e);
            }
        });
        this.checkSignal.connect(this::monitorRunners);
        this.taskDB.onStateChange().connect(newState -> {
            if (newState.getState().equals(TaskState.RUNNING)) {
                handleZombieTask(newState.getSourceAppName(), newState.getTaskId());
            }
        });
    }

    public boolean handleTaskOp(final TaskOperation operation) {
        return operation.accept(new TaskOperationVisitor<>() {
            @Override
            public Boolean visit(TaskCreateOperation create) {

                val taskSpec = create.getSpec();
                val runTaskId = genRunTaskId(taskSpec.getSourceAppName(), taskSpec.getTaskId());
                if (runners.containsKey(runTaskId) || taskDB.task(taskSpec.getSourceAppName(), taskSpec.getTaskId())
                        .isPresent()) {
                    return false;
                }
                val jobId = runners.computeIfAbsent(runTaskId,
                                                    id -> createRunner(taskSpec.getSourceAppName(),
                                                                       taskSpec.getTaskId()))
                        .startTask(create);
                return !Strings.isNullOrEmpty(jobId);
            }

            @Override
            public Boolean visit(TaskKillOperation kill) {
                val runner = runners.get(genRunTaskId(kill.getSourceAppName(), kill.getTaskId()));
                if (null == runner) {
                    return false;
                }
                return !Strings.isNullOrEmpty(runner.stopTask(kill));
            }
        });
    }

    public TaskRunner registerTaskRunner(String sourceAppName, String taskId) {
        val runTaskId = genRunTaskId(sourceAppName, taskId);
        return runners.computeIfAbsent(runTaskId, id -> createRunner(sourceAppName, taskId));
    }

    public void handleZombieTask(String sourceAppName, String taskId) {
        val runTaskId = genRunTaskId(sourceAppName, taskId);
        if (runners.containsKey(runTaskId)) {
            log.info("Task exists as expected for {}/{}", sourceAppName, taskId);
            return;
        }
        log.info("Task {}/{} is zombie and needs to be killed", sourceAppName, taskId);
        val runner = runners.computeIfAbsent(runTaskId, id -> createRunner(sourceAppName, taskId));
        runner.stopTask(new TaskKillOperation(sourceAppName, taskId, ClusterOpSpec.DEFAULT));
    }

    public List<TaskInfo> activeTasks() {
        return runners.values()
                .stream()
                .map(taskRunner -> taskDB.task(taskRunner.getSourceAppName(), taskRunner.getTaskId()).orElse(null))
                .filter(Objects::nonNull)
                .toList();
    }

    private void monitorRunners(Date triggerDate) {
        if (CommonUtils.isInMaintenanceWindow(clusterStateDB.currentState().orElse(null))) {
            log.warn("Task check skipped as cluster is in maintenance window");
            return;
            //Anything that finishes in this window will continue to show up in "Stopped" state on the ui till it is
            // reaped once cluster is out of the maintenance window
        }
        runners.forEach((runTaskId, runner) -> {
            val currState = runner.updateCurrentState().orElse(null);
            if (null != currState && currState.equals(TaskState.LOST)) {
                val sourceAppName = runner.getSourceAppName();
                val taskId = runner.getTaskId();
                log.info("Task {}/{} is lost. Runner will be stopped.", sourceAppName, taskId);
                runner.stopTask(new TaskKillOperation(sourceAppName, taskId, ClusterOpSpec.DEFAULT));
            }
        });
        log.debug("Task check triggered at {} is completed", triggerDate);
    }

    private TaskRunner createRunner(String sourceAppName, String taskId) {
        val runner = new TaskRunner(sourceAppName,
                                    taskId,
                                    jobExecutor,
                                    taskDB,
                                    clusterResourcesDB,
                                    scheduler,
                                    communicator,
                                    retrySpecFactory,
                                    instanceIdGenerator,
                                    threadFactory,
                                    completed);
        val f = executorService.submit(runner);
        runner.setTaskFuture(f);
        return runner;
    }

    private String genRunTaskId(String sourceAppName, String taskId) {
        return sourceAppName + "-" + taskId;
    }

}
