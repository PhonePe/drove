package com.phonepe.drove.executor.discovery;

import com.google.inject.Guice;
import com.phonepe.drove.common.AbstractTestBase;
import com.phonepe.drove.common.CommonUtils;
import com.phonepe.drove.executor.ExecutorOptions;
import com.phonepe.drove.executor.ExecutorTestingUtils;
import com.phonepe.drove.executor.InjectingApplicationInstanceActionFactory;
import com.phonepe.drove.executor.InjectingTaskActionFactory;
import com.phonepe.drove.executor.engine.ApplicationInstanceEngine;
import com.phonepe.drove.executor.engine.TaskInstanceEngine;
import com.phonepe.drove.executor.managed.ExecutorIdManager;
import com.phonepe.drove.executor.resourcemgmt.ResourceConfig;
import com.phonepe.drove.executor.resourcemgmt.ResourceManager;
import com.phonepe.drove.executor.statemachine.BlacklistingManager;
import com.phonepe.drove.models.info.nodedata.*;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.phonepe.drove.common.CommonTestUtils.waitUntil;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 */
class NodeDataUpdaterTest extends AbstractTestBase {

    @Test
    @SneakyThrows
    void testNodeData() {
        val eim = new ExecutorIdManager(23, ExecutorOptions.DEFAULT);
        val updateCounter = new AtomicInteger();
        val nds = new TestNodeDataStore();
        nds.onNodeDataUpdate().connect(nodeData -> updateCounter.incrementAndGet());
        //when(nds.updateNodeData(any(NodeData.class));
        val rdb = new ResourceManager();
        rdb.populateResources(Map.of(0, new ResourceManager.NodeInfo(IntStream.rangeClosed(0, 20)
                                                                             .boxed()
                                                                             .collect(Collectors.toUnmodifiableSet()),
                                                                     512_000_000)));
        val blm = new BlacklistingManager();
        val ie = new ApplicationInstanceEngine(eim,
                                               Executors.newSingleThreadExecutor(),
                                               new InjectingApplicationInstanceActionFactory(Guice.createInjector()),
                                               rdb,
                                               ExecutorTestingUtils.DOCKER_CLIENT);
        val te = new TaskInstanceEngine(eim,
                                        Executors.newSingleThreadExecutor(),
                                        new InjectingTaskActionFactory(Guice.createInjector()),
                                        rdb,
                                        ExecutorTestingUtils.DOCKER_CLIENT);
        val rCfg = new ResourceConfig();
        val ndu = new NodeDataUpdater(eim, nds, rdb, ie, te, rCfg, blm);
        ndu.start();
        assertTrue(nds.nodes(NodeType.EXECUTOR).isEmpty());
        ndu.hostInfoAvailable(new ExecutorIdManager.ExecutorHostInfo(8080,
                                                                     "test-host",
                                                                     NodeTransportType.HTTP,
                                                                     CommonUtils.executorId(8080, "test-host")));
        validateSteadyState(updateCounter, nds, 1);
        assertTrue(rdb.lockResources(new ResourceManager.ResourceUsage("test", ResourceManager.ResourceLockType.HARD,
                                                                       Map.of(0,
                                                                              new ResourceManager.NodeInfo(IntStream.rangeClosed(
                                                                                              0,
                                                                                              10)
                                                                                                                   .boxed()
                                                                                                                   .collect(
                                                                                                                           Collectors.toUnmodifiableSet()),
                                                                                                           128_000_000)))));
        {
            waitUntil(() -> updateCounter.get() == 2);
            val nodes = nds.nodes(NodeType.EXECUTOR);
            assertFalse(nodes.isEmpty());
            val node = nodes.get(0);
            assertEquals(8080, node.getPort());
            assertEquals("test-host", node.getHostname());
            assertEquals(NodeTransportType.HTTP, node.getTransportType());
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