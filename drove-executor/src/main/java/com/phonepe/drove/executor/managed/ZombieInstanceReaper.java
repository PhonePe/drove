package com.phonepe.drove.executor.managed;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.exception.ConflictException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Container;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;
import com.phonepe.drove.common.coverageutils.IgnoreInJacocoGeneratedReport;
import com.phonepe.drove.common.model.DeploymentUnitSpec;
import com.phonepe.drove.executor.discovery.ClusterClient;
import com.phonepe.drove.executor.engine.ApplicationInstanceEngine;
import com.phonepe.drove.executor.engine.DockerLabels;
import com.phonepe.drove.executor.engine.InstanceEngine;
import com.phonepe.drove.executor.engine.TaskInstanceEngine;
import com.phonepe.drove.executor.model.DeployedExecutionObjectInfo;
import com.phonepe.drove.executor.utils.DockerUtils;
import com.phonepe.drove.models.application.JobType;
import com.phonepe.drove.models.instance.InstanceState;
import com.phonepe.drove.models.interfaces.DeployedInstanceInfo;
import com.phonepe.drove.models.taskinstance.TaskState;
import io.appform.signals.signals.ScheduledSignal;
import io.dropwizard.lifecycle.Managed;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import ru.vyarus.dropwizard.guice.module.installer.order.Order;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.phonepe.drove.models.instance.InstanceState.*;

/**
 *
 */
@Singleton
@Slf4j
@Order(80)
public class ZombieInstanceReaper implements Managed {

    private static final Set<InstanceState> EXPECTED_APP_DOCKER_RUNNING_STATES = Set.of(PROVISIONING,
                                                                                        STARTING,
                                                                                        UNREADY,
                                                                                        READY,
                                                                                        HEALTHY,
                                                                                        UNHEALTHY,
                                                                                        STOPPING);
    private static final Set<TaskState> EXPECTED_TASK_DOCKER_RUNNING_STATES = Set.of(TaskState.PROVISIONING,
                                                                                     TaskState.STARTING,
                                                                                     TaskState.RUNNING,
                                                                                     TaskState.RUN_COMPLETED,
                                                                                     TaskState.DEPROVISIONING);

    private final ScheduledSignal zombieCheckSignal;
    private final DockerClient client;
    private final ApplicationInstanceEngine applicationInstanceEngine;
    private final TaskInstanceEngine taskInstanceEngine;

    private final ClusterClient clusterClient;

    @Inject
    @IgnoreInJacocoGeneratedReport
    public ZombieInstanceReaper(
            DockerClient client,
            ApplicationInstanceEngine applicationInstanceEngine,
            TaskInstanceEngine taskInstanceEngine, ClusterClient clusterClient) {
        this(client, applicationInstanceEngine, taskInstanceEngine, Duration.ofMinutes(1), clusterClient);
    }

    @VisibleForTesting
    ZombieInstanceReaper(
            DockerClient client,
            ApplicationInstanceEngine applicationInstanceEngine,
            TaskInstanceEngine taskInstanceEngine,
            Duration checkDuration, ClusterClient clusterClient) {
        this.client = client;
        this.applicationInstanceEngine = applicationInstanceEngine;
        this.taskInstanceEngine = taskInstanceEngine;
        this.zombieCheckSignal = new ScheduledSignal(checkDuration);
        this.clusterClient = clusterClient;
    }

    @Override
    public void start() throws Exception {
        zombieCheckSignal.connect("ZOMBIE_CHECK", this::reconcileInstances);
        log.info("Zombie instance reaper started");
    }

    @Override
    public void stop() throws Exception {
        zombieCheckSignal.disconnect("ZOMBIE_CHECK");
        zombieCheckSignal.close();
        log.info("Zombie instance reaper stopped");
    }

    void reconcileInstances(final Date checkTime) {
        val containers = client.listContainersCmd()
                .withShowAll(true)
                .withLabelFilter(List.of(DockerLabels.DROVE_INSTANCE_ID_LABEL,
                                         DockerLabels.DROVE_INSTANCE_SPEC_LABEL,
                                         DockerLabels.DROVE_INSTANCE_DATA_LABEL))
                .exec();
        val knownInstances = clusterClient.currentKnownInstances();
        val appInstances = applicationInstanceEngine.instanceIds(EXPECTED_APP_DOCKER_RUNNING_STATES);
        val taskInstanceIds = taskInstanceEngine.instanceIds(EXPECTED_TASK_DOCKER_RUNNING_STATES);

        // Kill all app instances that do not have a parent app
        pruneHeadlessInstances(applicationInstanceEngine,
                               JobType.SERVICE,
                               knownInstances.getStaleAppInstanceIds(),
                               appInstances);

        //Remove all instance ids that are marked as stale by containers
        pruneInstanceIds(containers,
                         applicationInstanceEngine,
                         appInstances,
                         JobType.SERVICE);
        pruneInstanceIds(containers,
                         taskInstanceEngine,
                         taskInstanceIds,
                         JobType.COMPUTATION);
        pruneZombieContainers(containers,
                              applicationInstanceEngine,
                              EXPECTED_APP_DOCKER_RUNNING_STATES,
                              JobType.SERVICE);
        pruneZombieContainers(containers,
                              taskInstanceEngine,
                              EXPECTED_TASK_DOCKER_RUNNING_STATES,
                              JobType.COMPUTATION);
    }

    private <E extends DeployedExecutionObjectInfo, S extends Enum<S>,
            T extends DeploymentUnitSpec, I extends DeployedInstanceInfo> void pruneInstanceIds(
            List<Container> containers, InstanceEngine<E, S, T, I> engine,
            Set<String> instanceIds,
            JobType type) {
        //Only states where we expect this to be running
        val foundContainers = containers.stream()
                .filter(container -> type.equals(JobType.valueOf(
                        container.getLabels().get(DockerLabels.DROVE_JOB_TYPE_LABEL))))
                .map(container -> container.getLabels().get(DockerLabels.DROVE_INSTANCE_ID_LABEL))
                .collect(Collectors.toUnmodifiableSet());
        val notFound = Set.copyOf(Sets.difference(instanceIds, foundContainers));
        if (!notFound.isEmpty()) {
            log.warn("Did not find running {} containers for instances: {}", instanceIds, type);
            notFound.forEach(engine::stopInstance);
            log.info("Stale {} instance data cleaned up", type);
        }
        else {
            log.debug("No stale {} instance ids found", type);
        }

    }

    private static <E extends DeployedExecutionObjectInfo, S extends Enum<S>,
            T extends DeploymentUnitSpec,
            I extends DeployedInstanceInfo> void pruneHeadlessInstances(
            InstanceEngine<E, S, T, I> engine,
            JobType type,
            Set<String> staleInstances,
            Set<String> instanceIds) {
        val toBeKilledState = Set.copyOf(Sets.intersection(staleInstances, instanceIds));
        if (!toBeKilledState.isEmpty()) {
            log.warn("Instances {} are marked as stale on controller. Will be killed", toBeKilledState);
            toBeKilledState.forEach(engine::stopInstance);
            log.info("{} controller stale {} containers have been killed", toBeKilledState.size(), type);
        }
        else {
            log.debug("No instances of type {} have been marked as stale by controller", type);
        }
    }

    private <E extends DeployedExecutionObjectInfo, S extends Enum<S>,
            T extends DeploymentUnitSpec, I extends DeployedInstanceInfo>
    void pruneZombieContainers(
            List<Container> containers, InstanceEngine<E, S, T, I> engine, Set<S> states, JobType type) {
        val instanceIds = engine.instanceIds(states);
        val foundContainers = containers.stream()
                .filter(container -> type.equals(JobType.valueOf(
                        container.getLabels().get(DockerLabels.DROVE_JOB_TYPE_LABEL))))
                .map(container -> container.getLabels().get(DockerLabels.DROVE_INSTANCE_ID_LABEL))
                .collect(Collectors.toUnmodifiableSet());
        val onlyInDocker = Sets.difference(foundContainers, instanceIds);
        if (!onlyInDocker.isEmpty()) {
            log.info("Containers of type {} not supposed to be on drove but still running: {}", type, onlyInDocker);
            containers.stream()
                    .filter(container -> onlyInDocker.contains(container.getLabels()
                                                                       .get(DockerLabels.DROVE_INSTANCE_ID_LABEL)))
                    .forEach(container -> {
                        val droveInstanceId = container.getLabels().get(DockerLabels.DROVE_INSTANCE_ID_LABEL);
                        val containerId = container.getId();
                        if (DockerUtils.isRunning(client, container)) {
                            log.info("Killing zombie container of type {}: {} {}", type, droveInstanceId, containerId);
                            try (val cmd = client.killContainerCmd(containerId)) {
                                cmd.exec();
                            }
                            catch (Exception e) {
                                log.error("Error killing container " + droveInstanceId + " (" + containerId + ") : "
                                                  + e.getMessage(),
                                          e);
                            }
                            log.info("Killed zombie container: {} {}", droveInstanceId, containerId);
                        }
                        try {
                            DockerUtils.cleanupContainer(client, containerId);
                        }
                        catch (NotFoundException | ConflictException e) {
                            log.error("Looks like container {} ({}) has already been deleted. No cleanup needed for " +
                                            "this zombie",
                                    droveInstanceId,
                                    containerId);
                        }
                        catch (Exception e) {
                            log.error("Error cleaning up container " + droveInstanceId + " (" + containerId + ") : "
                                              + e.getMessage(),
                                      e);
                        }
                    });
            log.info("All zombie containers of type {} killed", type);
        }
        else {
            log.debug("No zombie containers of type {} found", type);
        }
    }

}
