package com.phonepe.drove.executor;

import com.codahale.metrics.SharedMetricRegistries;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.phonepe.drove.common.AbstractTestBase;
import com.phonepe.drove.executor.engine.ApplicationInstanceEngine;
import com.phonepe.drove.executor.managed.ExecutorIdManager;
import com.phonepe.drove.executor.resourcemgmt.ResourceManager;
import com.phonepe.drove.executor.statemachine.BlacklistingManager;
import io.dropwizard.lifecycle.setup.LifecycleEnvironment;
import io.dropwizard.setup.Environment;
import lombok.val;
import org.junit.jupiter.api.BeforeEach;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 *
 */
public class AbstractExecutorEngineEnabledTestBase extends AbstractTestBase {
    @Inject
    protected ResourceManager resourceDB;

    @Inject
    protected BlacklistingManager blacklistingManager;

    @Inject
    protected ApplicationInstanceEngine engine;


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
            public ExecutorOptions executorOptions() {
                return new ExecutorOptions()
                        .setCacheImages(true);
            }

            @Provides
            @Singleton
            public ApplicationInstanceEngine engine(
                    final Injector injector,
                    final ResourceManager resourceDB,
                    final ExecutorIdManager executorIdManager) {
                val executorService = Executors.newSingleThreadExecutor();
                return new ApplicationInstanceEngine(
                        executorIdManager,
                        executorService,
                        new InjectingInstanceActionFactory(injector),
                        resourceDB,
                        ExecutorTestingUtils.DOCKER_CLIENT);
            }
        });
        injector.injectMembers(this);
        resourceDB.populateResources(Map.of(0, new ResourceManager.NodeInfo(IntStream.rangeClosed(0, 7)
                                                                               .boxed()
                                                                               .collect(Collectors.toUnmodifiableSet()),
                                                                            16_000_000)));
    }
}
