/*
 *  Copyright (c) 2024 Original Author(s), PhonePe India Pvt. Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.phonepe.drove.executor.discovery;

import com.google.inject.Guice;
import com.phonepe.drove.common.AbstractTestBase;
import com.phonepe.drove.common.CommonUtils;
import com.phonepe.drove.executor.*;
import com.phonepe.drove.executor.engine.ApplicationInstanceEngine;
import com.phonepe.drove.executor.engine.LocalServiceInstanceEngine;
import com.phonepe.drove.executor.engine.TaskInstanceEngine;
import com.phonepe.drove.executor.managed.ExecutorIdManager;
import com.phonepe.drove.executor.resourcemgmt.ResourceConfig;
import com.phonepe.drove.executor.resourcemgmt.ResourceManager;
import com.phonepe.drove.executor.statemachine.ExecutorStateManager;
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
        rdb.populateResources(Map.of(0, ResourceManager.NodeInfo.from(IntStream.rangeClosed(0, 20)
                                                                             .boxed()
                                                                             .collect(Collectors.toUnmodifiableSet()),
                                                                     512_000_000)));
        val blm = new ExecutorStateManager();
        final var injector = Guice.createInjector();
        val ie = new ApplicationInstanceEngine(eim,
                                               Executors.newSingleThreadExecutor(),
                                               new InjectingApplicationInstanceActionFactory(injector),
                                               rdb,
                                               ExecutorTestingUtils.DOCKER_CLIENT);
        val te = new TaskInstanceEngine(eim,
                                        Executors.newSingleThreadExecutor(),
                                        new InjectingTaskActionFactory(injector),
                                        rdb,
                                        ExecutorTestingUtils.DOCKER_CLIENT);
        val lse = new LocalServiceInstanceEngine(eim,
                                                 Executors.newSingleThreadExecutor(),
                                                 new InjectingLocalServiceInstanceActionFactory(injector),
                                                 rdb,
                                                 ExecutorTestingUtils.DOCKER_CLIENT);
        val rCfg = new ResourceConfig();
        val ndu = new NodeDataUpdater(eim, nds, rdb, ie, te, lse, rCfg, blm);
        ndu.start();
        assertTrue(nds.nodes(NodeType.EXECUTOR).isEmpty());
        ndu.hostInfoAvailable(new ExecutorIdManager.ExecutorHostInfo(8080,
                                                                     "test-host",
                                                                     NodeTransportType.HTTP,
                                                                     CommonUtils.executorId(8080, "test-host")));
        validateSteadyState(updateCounter, nds, 1);
        assertTrue(rdb.lockResources(new ResourceManager.ResourceUsage("test", ResourceManager.ResourceLockType.HARD,
                                                                       Map.of(0,
                                                                              ResourceManager.NodeInfo.from(IntStream.rangeClosed(
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
                    assertNotEquals(ExecutorState.BLACKLISTED, executorData.getExecutorState());
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
                    assertEquals(ExecutorState.BLACKLISTED, executorData.getExecutorState());
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
                assertNotEquals(ExecutorState.BLACKLISTED, executorData.getExecutorState());
                assertTrue(executorData.getState().getCpus().getUsedCores().isEmpty());
                assertTrue(executorData.getState().getMemory().getUsedMemory().isEmpty());
                return null;
            }
        });
    }

}