package com.phonepe.drove.executor.managed;

import com.github.dockerjava.api.DockerClient;
import com.google.common.collect.Sets;
import com.phonepe.drove.executor.engine.DockerLabels;
import com.phonepe.drove.executor.engine.ApplicationInstanceEngine;
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
import java.util.stream.Collectors;

import static com.phonepe.drove.models.instance.InstanceState.RUNNING_STATES;

/**
 *
 */
@Singleton
@Slf4j
@Order(80)
public class ZombieInstanceReaper implements Managed {

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
        val instanceIds = instanceEngine.instanceIds(RUNNING_STATES);
        //Only states where we expect this to be running
        val containers = client.listContainersCmd()
                .withLabelFilter(List.of(DockerLabels.DROVE_INSTANCE_ID_LABEL,
                                         DockerLabels.DROVE_INSTANCE_SPEC_LABEL,
                                         DockerLabels.DROVE_INSTANCE_DATA_LABEL))
                .exec();
        val foundContainers = containers.stream()
                .map(container -> container.getLabels().get(DockerLabels.DROVE_INSTANCE_ID_LABEL))
                .collect(Collectors.toUnmodifiableSet());
        val notFound = Sets.difference(instanceIds, foundContainers);
        if (!notFound.isEmpty()) {
            log.warn("Did not find running containers for instances: {}", instanceIds);
            notFound.forEach(instanceEngine::stopInstance);
        }
        else {
            log.debug("All instances are present as expected. Invocation time: {}", checkTime);
        }
    }
}
