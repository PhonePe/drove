package com.phonepe.drove.controller.engine;

import com.phonepe.drove.controller.resourcemgmt.ClusterResourcesDB;
import com.phonepe.drove.controller.resourcemgmt.InstanceScheduler;
import com.phonepe.drove.controller.statedb.TaskDB;
import com.phonepe.drove.jobexecutor.JobExecutor;
import com.phonepe.drove.models.operation.TaskOperation;
import com.phonepe.drove.models.operation.TaskOperationVisitor;
import com.phonepe.drove.models.operation.taskops.TaskCreateOperation;
import com.phonepe.drove.models.operation.taskops.TaskKillOperation;
import io.appform.signals.signals.ConsumingFireForgetSignal;
import io.dropwizard.util.Strings;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.Map;
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
    private final ConsumingFireForgetSignal<TaskRunner> completed = new ConsumingFireForgetSignal<TaskRunner>();

    private final Map<String, TaskRunner> runners = new ConcurrentHashMap<>();

    @Inject
    public TaskEngine(
            TaskDB taskDB,
            ClusterResourcesDB clusterResourcesDB,
            InstanceScheduler scheduler,
            ControllerCommunicator communicator,
            ControllerRetrySpecFactory retrySpecFactory,
            InstanceIdGenerator instanceIdGenerator,
            @Named("JobLevelThreadFactory") ThreadFactory threadFactory,
            @Named("TaskThreadPool") ExecutorService executorService, JobExecutor<Boolean> jobExecutor) {
        this.taskDB = taskDB;
        this.clusterResourcesDB = clusterResourcesDB;
        this.scheduler = scheduler;
        this.communicator = communicator;
        this.retrySpecFactory = retrySpecFactory;
        this.instanceIdGenerator = instanceIdGenerator;
        this.threadFactory = threadFactory;
        this.executorService = executorService;
        this.jobExecutor = jobExecutor;
        this.completed.connect(taskRunner -> {
            val runTaskId = taskRunner.getSourceAppName() + "-" + taskRunner.getTaskId();
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
    }

    public boolean handleTaskOp(final TaskOperation operation) {
        return operation.accept(new TaskOperationVisitor<Boolean>() {
            @Override
            public Boolean visit(TaskCreateOperation create) {

                val taskSpec = create.getSpec();
                val runTaskId = taskSpec.getSourceApp() + "-" + taskSpec.getTaskId();
                if(runners.containsKey(runTaskId) || taskDB.task(taskSpec.getSourceApp(), taskSpec.getTaskId()).isPresent()) {
                    return false;
                }
                runners.computeIfAbsent(runTaskId, id -> {
                    val runner = new TaskRunner(taskSpec.getSourceApp(),
                                                taskSpec.getTaskId(),
                                                jobExecutor,
                                                taskDB,
                                                clusterResourcesDB,
                                                scheduler,
                                                communicator,
                                                retrySpecFactory,
                                                instanceIdGenerator,
                                                threadFactory,
                                                completed);
                    runner.startTask(create);
                    val f = executorService.submit(runner);
                    runner.setTaskFuture(f);
                    return runner;
                });
                return true;
            }

            @Override
            public Boolean visit(TaskKillOperation kill) {
                val runner = runners.get(kill.getTaskId());
                if(null == runner) {
                    return false;
                }
                return !Strings.isNullOrEmpty(runner.stopTask(kill));
            }
        });
    }
}
