/*
 *  Copyright (c) 2022 Original Author(s), PhonePe India Pvt. Ltd.
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

package com.phonepe.drove.controller.engine;

import com.google.common.base.Strings;
import com.phonepe.drove.common.CommonUtils;
import com.phonepe.drove.common.net.HttpCaller;
import com.phonepe.drove.controller.config.ControllerOptions;
import com.phonepe.drove.controller.managed.LeadershipEnsurer;
import com.phonepe.drove.controller.resourcemgmt.ClusterResourcesDB;
import com.phonepe.drove.controller.resourcemgmt.InstanceScheduler;
import com.phonepe.drove.controller.statedb.ClusterStateDB;
import com.phonepe.drove.controller.statedb.TaskDB;
import com.phonepe.drove.controller.utils.ControllerUtils;
import com.phonepe.drove.jobexecutor.JobExecutor;
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
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.stream.Collectors;

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
    private final LeadershipEnsurer leadershipEnsurer;
    private final ClusterOpSpec defaultClusterOpSpec;
    private final ControllerOptions controllerOptions;
    private final HttpCaller httpCaller;

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
            ClusterStateDB clusterStateDB,
            LeadershipEnsurer leadershipEnsurer,
            ClusterOpSpec defaultClusterOpSpec,
            ControllerOptions controllerOptions,
            HttpCaller httpCaller) {
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
        this.leadershipEnsurer = leadershipEnsurer;
        this.defaultClusterOpSpec = defaultClusterOpSpec;
        this.controllerOptions = controllerOptions;
        this.httpCaller = httpCaller;
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
            if (!leadershipEnsurer.isLeader()) {
                log.debug("Zombie task check skipped as node is not leader");
                return;
            }
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
                val errors = new ArrayList<String>();
                errors.addAll(ControllerUtils.ensureWhitelistedVolumes(taskSpec.getVolumes(), controllerOptions));
                errors.addAll(ControllerUtils.ensureCmdlArgs(taskSpec.getArgs(), controllerOptions));
                errors.addAll(resourceCheck(taskSpec));
                if (!errors.isEmpty()) {
                    return ValidationResult.failure(errors);
                }
                val runTaskId = genRunTaskId(taskSpec.getSourceAppName(), taskSpec.getTaskId());
                if (runners.containsKey(runTaskId)
                        || taskDB.task(taskSpec.getSourceAppName(), taskSpec.getTaskId()).isPresent()) {
                    return ValidationResult.failure(
                            String.format("Task already exists for %s/%s with taskID: %s",
                                          taskSpec.getSourceAppName(), taskSpec.getTaskId(), runTaskId));
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
        runner.stopTask(new TaskKillOperation(sourceAppName, taskId, defaultClusterOpSpec));
    }

    public List<TaskInfo> activeTasks() {
        return taskDB.tasks(
                runners.values()
                        .stream()
                        .collect(Collectors.groupingBy(
                                TaskRunner::getSourceAppName,
                                Collectors.mapping(TaskRunner::getTaskId, Collectors.toUnmodifiableSet()))),
                TaskState.ACTIVE_STATES);
    }

    private void monitorRunners(Date triggerDate) {
        if (!leadershipEnsurer.isLeader()) {
            log.info("Task check skipped as I'm not the leader");
            return;
        }
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
                                    completed,
                                    httpCaller);
        val f = executorService.submit(runner);
        runner.setTaskFuture(f);
        return runner;
    }

    private String genRunTaskId(String sourceAppName, String taskId) {
        return sourceAppName + "-" + taskId;
    }

    private List<String> resourceCheck(final TaskSpec taskSpec) {
        val errors = new ArrayList<String>();
        ControllerUtils.checkResources(clusterResourcesDB, taskSpec, 1, errors);
        return errors;
    }
}
