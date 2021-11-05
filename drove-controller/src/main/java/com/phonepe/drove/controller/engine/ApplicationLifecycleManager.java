package com.phonepe.drove.controller.engine;

import com.phonepe.drove.controller.engine.jobs.StartSingleInstanceJob;
import com.phonepe.drove.controller.engine.jobs.StopSingleInstanceJob;
import com.phonepe.drove.controller.jobexecutor.JobExecutor;
import com.phonepe.drove.controller.jobexecutor.JobTopology;
import com.phonepe.drove.controller.resources.ClusterResourcesDB;
import com.phonepe.drove.controller.resources.InstanceScheduler;
import com.phonepe.drove.controller.statedb.ApplicationStateDB;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.operation.ops.ApplicationCreateOperation;
import com.phonepe.drove.models.operation.ops.ApplicationSuspendOperation;
import io.appform.signals.signals.ConsumingSyncSignal;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 *
 */
@Singleton
@Slf4j
public class ApplicationLifecycleManager {
    private final InstanceScheduler scheduler;
    private final ApplicationStateDB applicationStateDB;
    private final ClusterResourcesDB clusterResourcesDB;
    private final ControllerCommunicator communicator;
    private final ConsumingSyncSignal<ApplicationStateChangeInfo> stateChanged = new ConsumingSyncSignal<>();
    private final JobExecutor<Boolean> jobExecutor = new JobExecutor<>(Executors.newFixedThreadPool(1024));

    @Inject
    public ApplicationLifecycleManager(
            InstanceScheduler scheduler,
            ApplicationStateDB applicationStateDB,
            ClusterResourcesDB clusterResourcesDB,
            ControllerCommunicator communicator) {
        this.scheduler = scheduler;
        this.applicationStateDB = applicationStateDB;
        this.clusterResourcesDB = clusterResourcesDB;
        this.communicator = communicator;
    }

    public String start(final ApplicationCreateOperation createOperation) {
        val applicationSpec = createOperation.getSpec();
        val clusterOpSpec = createOperation.getOpSpec();
        val parallelism = clusterOpSpec.getParallelism();
        val topology = JobTopology.<Boolean>builder()
                .addParallel(parallelism, IntStream.range(0, applicationSpec.getInstances())
                        .mapToObj(i -> new StartSingleInstanceJob(applicationSpec,
                                                                  clusterOpSpec,
                                                                  scheduler,
                                                                  applicationStateDB,
                                                                  communicator))
                        .collect(Collectors.toUnmodifiableList()))
                .build();
        return jobExecutor.schedule(topology, new BooleanResponseCombiner(), r -> {});
    }

    public String stop(final ApplicationSuspendOperation suspendOperation) {
        val clusterOpSpec = suspendOperation.getOpSpec();
        val existingInstances = applicationStateDB.instances(
                suspendOperation.getAppId(), 0, Integer.MAX_VALUE);
        val topology = JobTopology.<Boolean>builder()
                .addParallel(clusterOpSpec.getParallelism(), existingInstances
                        .stream()
                        .map(InstanceInfo::getInstanceId)
                        .map(iid -> new StopSingleInstanceJob(suspendOperation.getAppId(),
                                                              iid,
                                                              suspendOperation.getOpSpec(),
                                                              applicationStateDB,
                                                              clusterResourcesDB,
                                                              communicator))
                        .collect(Collectors.toUnmodifiableList()))
                .build();
        return jobExecutor.schedule(topology, new BooleanResponseCombiner(), r -> {});
    }

}
