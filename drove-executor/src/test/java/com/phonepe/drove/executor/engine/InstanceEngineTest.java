package com.phonepe.drove.executor.engine;

import com.codahale.metrics.SharedMetricRegistries;
import com.google.inject.*;
import com.phonepe.drove.executor.InjectingInstanceActionFactory;
import com.phonepe.drove.executor.TestingUtils;
import com.phonepe.drove.executor.managed.ExecutorIdManager;
import com.phonepe.drove.executor.resourcemgmt.ResourceDB;
import com.phonepe.drove.executor.statemachine.BlacklistingManager;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.instance.InstanceState;
import io.dropwizard.lifecycle.setup.LifecycleEnvironment;
import io.dropwizard.setup.Environment;
import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 *
 */
class InstanceEngineTest {

    @Inject
    private ResourceDB resourceDB;

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
                        blacklistManager);
            }
        });
        injector.injectMembers(this);
        resourceDB.populateResources(Map.of(0, new ResourceDB.NodeInfo(IntStream.rangeClosed(0, 7)
                                                                               .boxed()
                                                                               .collect(Collectors.toUnmodifiableSet()),
                                                                       16_000_000)));
    }

    @Test
    void testBasicRun() {
        val spec = TestingUtils.testSpec();
        assertTrue(engine.startInstance(spec));
        val instanceId = spec.getInstanceId();
        await().forever()
                .pollInterval(Duration.ofSeconds(1))
                .until(() -> engine.currentState(instanceId)
                        .map(InstanceInfo::getState)
                        .map(instanceState -> instanceState.equals(InstanceState.HEALTHY))
                        .orElse(false));
        assertTrue(engine.stopInstance(instanceId));
        await().forever()
                .pollInterval(Duration.ofSeconds(1))
                .until(() -> engine.currentState(instanceId).isEmpty());
    }
}