package com.phonepe.drove.executor.managed;

import com.codahale.metrics.SharedMetricRegistries;
import com.google.inject.*;
import com.phonepe.drove.common.model.MessageDeliveryStatus;
import com.phonepe.drove.common.model.MessageResponse;
import com.phonepe.drove.executor.AbstractExecutorTestBase;
import com.phonepe.drove.executor.InjectingInstanceActionFactory;
import com.phonepe.drove.executor.engine.ExecutorCommunicator;
import com.phonepe.drove.executor.engine.InstanceEngine;
import com.phonepe.drove.executor.resourcemgmt.ResourceDB;
import com.phonepe.drove.executor.statemachine.BlacklistingManager;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.instance.InstanceState;
import io.dropwizard.lifecycle.setup.LifecycleEnvironment;
import io.dropwizard.setup.Environment;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 *
 */
class ExecutorInstanceStateChangeNotifierTest extends AbstractExecutorTestBase {
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
    void testStateChange() {
        val iin = new InstanceInfo("TEST_APP-1",
                                   "TEST_APP",
                                   "INS1",
                                   "E1",
                                   null,
                                   null,
                                   InstanceState.HEALTHY,
                                   null,
                                   null,
                                   null);
        val ctr = new AtomicInteger();
        val scn = new ExecutorInstanceStateChangeNotifier(
                resourceDB,
                new ExecutorCommunicator(engine,
                                         message -> {
                                             ctr.incrementAndGet();
                                             if (ctr.get() > 1) {
                                                 return new MessageResponse(message.getHeader(),
                                                                            MessageDeliveryStatus.REJECTED);
                                             }
                                             return new MessageResponse(message.getHeader(),
                                                                        MessageDeliveryStatus.ACCEPTED);
                                         }), engine);
        scn.start();
        engine.onStateChange().dispatch(iin);
        assertEquals(1, ctr.get());
        engine.onStateChange().dispatch(iin);
        assertEquals(2, ctr.get());
        scn.stop();
        engine.onStateChange().dispatch(iin);
        engine.onStateChange().dispatch(iin);
        assertEquals(2, ctr.get());
    }


}