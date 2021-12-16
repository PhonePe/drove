package com.phonepe.drove.executor.managed;

import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.SharedMetricRegistries;
import com.google.inject.*;
import com.phonepe.drove.common.model.MessageDeliveryStatus;
import com.phonepe.drove.common.model.MessageHeader;
import com.phonepe.drove.common.model.executor.ExecutorAddress;
import com.phonepe.drove.common.model.executor.StartInstanceMessage;
import com.phonepe.drove.common.model.executor.StopInstanceMessage;
import com.phonepe.drove.executor.AbstractExecutorTestBase;
import com.phonepe.drove.executor.InjectingInstanceActionFactory;
import com.phonepe.drove.executor.TestingUtils;
import com.phonepe.drove.executor.engine.InstanceEngine;
import com.phonepe.drove.executor.resourcemgmt.ResourceDB;
import com.phonepe.drove.executor.statemachine.BlacklistingManager;
import com.phonepe.drove.models.instance.InstanceInfo;
import io.dropwizard.lifecycle.setup.LifecycleEnvironment;
import io.dropwizard.setup.Environment;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.phonepe.drove.common.CommonTestUtils.waitUntil;
import static com.phonepe.drove.models.instance.InstanceState.HEALTHY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 *
 */
@Slf4j
class ContainerStatsObserverTest extends AbstractExecutorTestBase {

    @Inject
    private ResourceDB resourceDB;

    @Inject
    private BlacklistingManager blacklistingManager;

    @Inject
    private InstanceEngine engine;


    @BeforeEach
    void setup() {
        val injector = Guice.createInjector(new AbstractModule() {

            @Override
            protected void configure() {
                super.configure();
            }

            @Provides
            @Singleton
            public Environment environment() {
                val env = mock(Environment.class);
                val lsm = new LifecycleEnvironment(SharedMetricRegistries.getOrCreate("test"));
                when(env.lifecycle()).thenReturn(lsm);
                return env;
            }

            @Provides
            @Singleton
            public InstanceEngine engine(
                    final Injector injector,
                    final ResourceDB resourceDB,
                    final ExecutorIdManager executorIdManager,
                    final BlacklistingManager blacklistManager) {
                val executorService = Executors.newSingleThreadExecutor();
                return new InstanceEngine(
                        executorIdManager,
                        executorService,
                        new InjectingInstanceActionFactory(injector),
                        resourceDB,
                        blacklistManager,
                        DOCKER_CLIENT);
            }
        });
        injector.injectMembers(this);
        resourceDB.populateResources(Map.of(0, new ResourceDB.NodeInfo(IntStream.rangeClosed(0, 7)
                                                                               .boxed()
                                                                               .collect(Collectors.toUnmodifiableSet()),
                                                                       16_000_000)));
    }

    @Test
    @SneakyThrows
    void testStats() {
        val statsObserver = new ContainerStatsObserver(METRIC_REGISTRY, engine, DOCKER_CLIENT, Duration.ofSeconds(1));
        statsObserver.start();
        val spec = TestingUtils.testSpec();
        val instanceId = spec.getInstanceId();
        val executorAddress = new ExecutorAddress("eid", "localhost", 3000);
        val startInstanceMessage = new StartInstanceMessage(MessageHeader.controllerRequest(),
                                                            executorAddress,
                                                            spec);
        val startResponse = engine.handleMessage(startInstanceMessage);
        try {
            assertEquals(MessageDeliveryStatus.ACCEPTED, startResponse.getStatus());
            assertEquals(MessageDeliveryStatus.FAILED, engine.handleMessage(startInstanceMessage).getStatus());
            waitUntil(() -> engine.currentState(instanceId)
                            .map(InstanceInfo::getState)
                            .map(instanceState -> instanceState.equals(HEALTHY))
                            .orElse(false));
            log.info("Container is healthy, will look for gauge to be published");
            waitUntil(() -> gaugePresent(instanceId));
            assertTrue(gaugePresent(instanceId));
        }
        finally {
            val stopInstanceMessage = new StopInstanceMessage(MessageHeader.controllerRequest(),
                                                              executorAddress,
                                                              instanceId);
            assertEquals(MessageDeliveryStatus.ACCEPTED, engine.handleMessage(stopInstanceMessage).getStatus());
            waitUntil(() -> engine.currentState(instanceId).isEmpty());
            statsObserver.stop();
        }
    }

    private boolean gaugePresent(String instanceId) {
        return METRIC_REGISTRY.getGauges(MetricFilter.endsWith("nr_throttled"))
                .keySet()
                .stream()
                .anyMatch(name -> name.contains(instanceId));
    }
}