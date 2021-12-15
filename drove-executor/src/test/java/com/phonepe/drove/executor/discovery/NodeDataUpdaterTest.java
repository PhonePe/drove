package com.phonepe.drove.executor.discovery;

import com.codahale.metrics.SharedMetricRegistries;
import com.google.inject.Guice;
import com.phonepe.drove.executor.AbstractExecutorBaseTest;
import com.phonepe.drove.executor.InjectingInstanceActionFactory;
import com.phonepe.drove.executor.engine.InstanceEngine;
import com.phonepe.drove.executor.managed.ExecutorIdManager;
import com.phonepe.drove.executor.resourcemgmt.ResourceConfig;
import com.phonepe.drove.executor.resourcemgmt.ResourceDB;
import com.phonepe.drove.executor.statemachine.BlacklistingManager;
import com.phonepe.drove.models.info.nodedata.ControllerNodeData;
import com.phonepe.drove.models.info.nodedata.ExecutorNodeData;
import com.phonepe.drove.models.info.nodedata.NodeDataVisitor;
import com.phonepe.drove.models.info.nodedata.NodeType;
import io.dropwizard.lifecycle.setup.LifecycleEnvironment;
import io.dropwizard.setup.Environment;
import lombok.SneakyThrows;
import lombok.val;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 *
 */
class NodeDataUpdaterTest extends AbstractExecutorBaseTest {

    @Test
    @SneakyThrows
    void testNodeData() {
        val eim = new ExecutorIdManager(23);
        val updateCounter = new AtomicInteger();
        val nds = new TestNodeDataStore();
        nds.onNodeDataUpdate().connect(nodeData -> updateCounter.incrementAndGet());
        //when(nds.updateNodeData(any(NodeData.class));
        val rdb = new ResourceDB();
        rdb.populateResources(Map.of(0, new ResourceDB.NodeInfo(IntStream.rangeClosed(0, 20)
                                                                        .boxed()
                                                                        .collect(Collectors.toUnmodifiableSet()),
                                                                512_000_000)));
        val env = mock(Environment.class);
        when(env.lifecycle()).thenReturn(new LifecycleEnvironment(SharedMetricRegistries.getOrCreate("test")));
        val blm = new BlacklistingManager();
        val ie = new InstanceEngine(eim,
                                    Executors.newSingleThreadExecutor(),
                                    new InjectingInstanceActionFactory(Guice.createInjector()),
                                    rdb,
                                    blm,
                                    DOCKER_CLIENT);
        val rCfg = new ResourceConfig();
        val ndu = new NodeDataUpdater(eim, nds, rdb, env, ie, rCfg, blm);
        ndu.start();
        assertTrue(nds.nodes(NodeType.EXECUTOR).isEmpty());
        val server = mock(Server.class);
        val conn = mock(ServerConnector.class);
        when(conn.getLocalPort()).thenReturn(8080);
        when(server.getConnectors()).thenReturn(new ServerConnector[] { conn });
        ndu.serverStarted(server);
        validateSteadyState(updateCounter, nds, 1);
        assertTrue(rdb.lockResources(new ResourceDB.ResourceUsage("test", ResourceDB.ResourceLockType.HARD,
                                                       Map.of(0, new ResourceDB.NodeInfo(IntStream.rangeClosed(0, 10)
                                                                                                 .boxed()
                                                                                                 .collect(Collectors.toUnmodifiableSet()),
                                                                                         128_000_000)))));
        {
            await()
                    .pollInterval(Duration.ofSeconds(1))
                    .until(() -> updateCounter.get() == 2);
            val nodes = nds.nodes(NodeType.EXECUTOR);
            assertFalse(nodes.isEmpty());
            val node = nodes.get(0);
            assertEquals(8080, node.getPort());
            node.accept(new NodeDataVisitor<Void>() {
                @Override
                public Void visit(ControllerNodeData controllerData) {
                    fail("Should not have received a controller node here");
                    return null;
                }

                @Override
                public Void visit(ExecutorNodeData executorData) {
                    assertFalse(executorData.isBlacklisted());
                    assertFalse(executorData.getState().getCpus().getUsedCores().isEmpty());
                    assertFalse(executorData.getState().getMemory().getUsedMemory().isEmpty());
                    return null;
                }
            });
        }
        rdb.reclaimResources("test");
        validateSteadyState(updateCounter, nds, 3);

        blm.blacklist();
        {
            await()
                    .pollInterval(Duration.ofSeconds(1))
                    .until(() -> updateCounter.get() == 4);
            val nodes = nds.nodes(NodeType.EXECUTOR);
            assertFalse(nodes.isEmpty());
            val node = nodes.get(0);
            assertEquals(8080, node.getPort());
            node.accept(new NodeDataVisitor<Void>() {
                @Override
                public Void visit(ControllerNodeData controllerData) {
                    fail("Should not have received a controller node here");
                    return null;
                }

                @Override
                public Void visit(ExecutorNodeData executorData) {
                    assertTrue(executorData.isBlacklisted());
                    return null;
                }
            });
        }
        blm.unblacklist();
        validateSteadyState(updateCounter, nds, 5);
        ndu.stop();
    }

    private void validateSteadyState(AtomicInteger updateCounter, TestNodeDataStore nds, int updateCount) {
        await()
                .pollInterval(Duration.ofSeconds(1))
                .until(() -> updateCounter.get() == updateCount);
        val nodes = nds.nodes(NodeType.EXECUTOR);
        assertFalse(nodes.isEmpty());
        val node = nodes.get(0);
        assertEquals(8080, node.getPort());
        node.accept(new NodeDataVisitor<Void>() {
            @Override
            public Void visit(ControllerNodeData controllerData) {
                fail("Should not have received a controller node here");
                return null;
            }

            @Override
            public Void visit(ExecutorNodeData executorData) {
                assertFalse(executorData.isBlacklisted());
                assertTrue(executorData.getState().getCpus().getUsedCores().isEmpty());
                assertTrue(executorData.getState().getMemory().getUsedMemory().isEmpty());
                return null;
            }
        });
    }

}