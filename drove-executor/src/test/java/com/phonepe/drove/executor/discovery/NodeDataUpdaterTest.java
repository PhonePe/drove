package com.phonepe.drove.executor.discovery;

import com.codahale.metrics.SharedMetricRegistries;
import com.google.inject.Guice;
import com.phonepe.drove.common.AbstractTestBase;
import com.phonepe.drove.executor.ExecutorTestingUtils;
import com.phonepe.drove.executor.InjectingApplicationInstanceActionFactory;
import com.phonepe.drove.executor.engine.ApplicationInstanceEngine;
import com.phonepe.drove.executor.managed.ExecutorIdManager;
import com.phonepe.drove.executor.resourcemgmt.ResourceConfig;
import com.phonepe.drove.executor.resourcemgmt.ResourceManager;
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

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.phonepe.drove.common.CommonTestUtils.waitUntil;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 *
 */
class NodeDataUpdaterTest extends AbstractTestBase {

    @Test
    @SneakyThrows
    void testNodeData() {
        val eim = new ExecutorIdManager(23);
        val updateCounter = new AtomicInteger();
        val nds = new TestNodeDataStore();
        nds.onNodeDataUpdate().connect(nodeData -> updateCounter.incrementAndGet());
        //when(nds.updateNodeData(any(NodeData.class));
        val rdb = new ResourceManager();
        rdb.populateResources(Map.of(0, new ResourceManager.NodeInfo(IntStream.rangeClosed(0, 20)
                                                                        .boxed()
                                                                        .collect(Collectors.toUnmodifiableSet()),
                                                                     512_000_000)));
        val env = mock(Environment.class);
        when(env.lifecycle()).thenReturn(new LifecycleEnvironment(SharedMetricRegistries.getOrCreate("test")));
        val blm = new BlacklistingManager();
        val ie = new ApplicationInstanceEngine(eim,
                                               Executors.newSingleThreadExecutor(),
                                               new InjectingApplicationInstanceActionFactory(Guice.createInjector()),
                                               rdb,
                                               ExecutorTestingUtils.DOCKER_CLIENT);
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
        assertTrue(rdb.lockResources(new ResourceManager.ResourceUsage("test", ResourceManager.ResourceLockType.HARD,
                                                                       Map.of(0, new ResourceManager.NodeInfo(IntStream.rangeClosed(0, 10)
                                                                                                 .boxed()
                                                                                                 .collect(Collectors.toUnmodifiableSet()),
                                                                                                              128_000_000)))));
        {
            waitUntil(() -> updateCounter.get() == 2);
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
            waitUntil(() -> updateCounter.get() == 4);
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
        waitUntil(() -> updateCounter.get() == updateCount);
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