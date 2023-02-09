package com.phonepe.drove.executor.managed;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Container;
import com.google.common.collect.Sets;
import com.phonepe.drove.executor.engine.ApplicationInstanceEngine;
import com.phonepe.drove.executor.engine.DockerLabels;
import com.phonepe.drove.models.instance.InstanceState;
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

    private static final Set<InstanceState> EXPECTED_DOCKER_RUNNING_STATES = Set.of(PROVISIONING,
                                                                                    STARTING,
                                                                                    UNREADY,
                                                                                    READY,
                                                                                    HEALTHY,
                                                                                    UNHEALTHY,
                                                                                    STOPPING);

    private final ScheduledSignal zombieCheckSignal = new ScheduledSignal(Duration.ofSeconds(30));
    private final DockerClient client;
    private final ApplicationInstanceEngine instanceEngine;

    @Inject
    public ZombieInstanceReaper(
            DockerClient client,
            ApplicationInstanceEngine instanceEngine) {
        this.client = client;
        this.instanceEngine = instanceEngine;
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
                .withLabelFilter(List.of(DockerLabels.DROVE_INSTANCE_ID_LABEL,
                                         DockerLabels.DROVE_INSTANCE_SPEC_LABEL,
                                         DockerLabels.DROVE_INSTANCE_DATA_LABEL))
                .exec();
        cleanupMissingDockers(containers);
        cleanupZombieContainers(containers);
    }

    void cleanupMissingDockers(List<Container> containers) {
        val instanceIds = instanceEngine.instanceIds(RUNNING_STATES);
        //Only states where we expect this to be running
        val foundContainers = containers.stream()
                .map(container -> container.getLabels().get(DockerLabels.DROVE_INSTANCE_ID_LABEL))
                .collect(Collectors.toUnmodifiableSet());
        val notFound = Sets.difference(instanceIds, foundContainers);
        if (!notFound.isEmpty()) {
            log.warn("Did not find running containers for instances: {}", instanceIds);
            notFound.forEach(instanceEngine::stopInstance);
            log.info("Stale instance data cleaned up");
        }
        else {
            log.debug("No stale instance ids found");
        }
    }

    private void cleanupZombieContainers(List<Container> containers) {
        val instanceIds = instanceEngine.instanceIds(EXPECTED_DOCKER_RUNNING_STATES);
        val foundContainers = containers.stream()
                .map(container -> container.getLabels().get(DockerLabels.DROVE_INSTANCE_ID_LABEL))
                .collect(Collectors.toUnmodifiableSet());
        val onlyInDocker = Sets.difference(foundContainers, instanceIds);
        if(!onlyInDocker.isEmpty()) {
            log.info("Containers not supposed to be on drove but still running: {}", onlyInDocker);
            containers.stream()
                    .filter(container -> onlyInDocker.contains(container.getLabels().get(DockerLabels.DROVE_INSTANCE_ID_LABEL)))
                    .forEach(container -> {
                        val droveInstanceId = container.getLabels().get(DockerLabels.DROVE_INSTANCE_ID_LABEL);
                        log.info("Killing zombie container: {} {}", droveInstanceId, container.getId());
                        try {
                            client.killContainerCmd(container.getId()).exec();
                        }
                        catch (Exception e) {
                            log.error("Error killing container " + droveInstanceId + " (" + container.getId() + ") : " + e.getMessage(), e);
                        }
                        log.info("Killed zombie container: {} {}", droveInstanceId, container.getId());
                    });
            log.info("All zombie containers killed");
        }
        else {
            log.debug("No zombie containers found");
        }
    }


}
