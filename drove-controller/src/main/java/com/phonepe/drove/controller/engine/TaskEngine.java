package com.phonepe.drove.controller.engine;

import com.phonepe.drove.common.CommonUtils;
import com.phonepe.drove.controller.resourcemgmt.ClusterResourcesDB;
import com.phonepe.drove.controller.resourcemgmt.InstanceScheduler;
import com.phonepe.drove.controller.statedb.ClusterStateDB;
import com.phonepe.drove.controller.statedb.TaskDB;
import com.phonepe.drove.controller.utils.ControllerUtils;
import com.phonepe.drove.jobexecutor.JobExecutor;
import com.phonepe.drove.models.application.requirements.CPURequirement;
import com.phonepe.drove.models.application.requirements.MemoryRequirement;
import com.phonepe.drove.models.application.requirements.ResourceRequirementVisitor;
import com.phonepe.drove.models.operation.ClusterOpSpec;
import com.phonepe.drove.models.operation.TaskOperation;
import com.phonepe.drove.models.operation.TaskOperationVisitor;
import com.phonepe.drove.models.operation.taskops.TaskCreateOperation;
import com.phonepe.drove.models.operation.taskops.TaskKillOperation;
import com.phonepe.drove.models.task.TaskSpec;
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
import java.util.*;
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
                log.info("Task {}/{} completed", taskRunner.getSourceAppName(), taskRunner.getTaskId());
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

    public ValidationResult handleTaskOp(final TaskOperation operation) {
        return operation.accept(new TaskOperationVisitor<>() {
            @Override
            public ValidationResult visit(TaskCreateOperation create) {

                val taskSpec = create.getSpec();
                val preCheckResult = resourceCheck(taskSpec);
                if (preCheckResult.getStatus().equals(ValidationStatus.FAILURE)) {
                    return preCheckResult;
                }
                val runTaskId = genRunTaskId(taskSpec.getSourceAppName(), taskSpec.getTaskId());
                if (runners.containsKey(runTaskId)
                        || taskDB.task(taskSpec.getSourceAppName(), taskSpec.getTaskId()).isPresent()) {
                    return ValidationResult.failure("Task already exists for "
                                                            + taskSpec.getSourceAppName() + "/" + taskSpec.getTaskId());
                }
                val jobId = runners.computeIfAbsent(runTaskId,
                                                    id -> createRunner(taskSpec.getSourceAppName(),
                                                                       taskSpec.getTaskId()))
                        .startTask(create);
                return !Strings.isNullOrEmpty(jobId)
                       ? ValidationResult.success()
                       : ValidationResult.failure("Could not schedule job to start the task.");
            }

            @Override
            public ValidationResult visit(TaskKillOperation kill) {
                final var sourceAppName = kill.getSourceAppName();
                final var taskId = kill.getTaskId();
                val runner = runners.get(genRunTaskId(sourceAppName, taskId));
                if (null == runner) {
                    return ValidationResult.failure("Either task does not exist or has already finished for "
                                                            + sourceAppName + "/" + taskId);
                }
                return !Strings.isNullOrEmpty(runner.stopTask(kill))
                       ? ValidationResult.success()
                       : ValidationResult.failure("Could not schedule job to stop the task.");
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
            log.debug("Task exists as expected for {}/{}", sourceAppName, taskId);
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

    private ValidationResult resourceCheck(final TaskSpec taskSpec) {
        val executors = clusterResourcesDB.currentSnapshot();
        var freeCores = 0;
        var freeMemory = 0L;
        for (val exec : executors) {
            freeCores += ControllerUtils.freeCores(exec);
            freeMemory += ControllerUtils.freeMemory(exec);
        }
        val requiredCores = taskSpec.getResources()
                .stream()
                .mapToInt(r -> r.accept(new ResourceRequirementVisitor<Integer>() {
                    @Override
                    public Integer visit(CPURequirement cpuRequirement) {
                        return (int) cpuRequirement.getCount();
                    }

                    @Override
                    public Integer visit(MemoryRequirement memoryRequirement) {
                        return 0;
                    }
                }))
                .sum();
        val requiredMem = taskSpec.getResources()
                .stream()
                .mapToLong(r -> r.accept(new ResourceRequirementVisitor<Long>() {
                    @Override
                    public Long visit(CPURequirement cpuRequirement) {
                        return 0L;
                    }

                    @Override
                    public Long visit(MemoryRequirement memoryRequirement) {
                        return memoryRequirement.getSizeInMB();
                    }
                }))
                .sum();
        val errors = new ArrayList<String>();
        if (requiredCores > freeCores) {
            errors.add("Cluster does not have enough CPU. Required: " + requiredCores + " Available: " + freeCores);
        }
        if (requiredMem > freeMemory) {
            errors.add("Cluster does not have enough Memory. Required: " + requiredMem + " Available: " + freeMemory);
        }
        return errors.isEmpty()
               ? ValidationResult.success()
               : ValidationResult.failure(errors);
    }
}