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

package com.phonepe.drove.controller.managed;

import com.phonepe.drove.common.CommonTestUtils;
import com.phonepe.drove.common.discovery.NodeDataStore;
import com.phonepe.drove.common.discovery.ZkNodeDataStore;
import com.phonepe.drove.common.zookeeper.ZkConfig;
import com.phonepe.drove.controller.ControllerTestBase;
import com.phonepe.drove.controller.ControllerTestUtils;
import com.phonepe.drove.controller.config.ControllerOptions;
import com.phonepe.drove.controller.engine.StateUpdater;
import com.phonepe.drove.controller.event.DroveEventBus;
import com.phonepe.drove.models.info.nodedata.ExecutorNodeData;
import com.phonepe.drove.models.info.nodedata.NodeType;
import io.appform.signals.signals.ConsumingSyncSignal;
import lombok.SneakyThrows;
import lombok.val;
import org.apache.curator.test.TestingCluster;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static com.phonepe.drove.common.CommonUtils.buildCurator;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 *
 */
class ExecutorObserverTest extends ControllerTestBase {

    @Test
    @SneakyThrows
    @SuppressWarnings("unchecked")
    void testObserver() {
        val eventBus = mock(DroveEventBus.class);
        doNothing().when(eventBus).publish(any());
        try (val cluster = new TestingCluster(1)) {
            cluster.start();
            try (val curator = buildCurator(new ZkConfig().setConnectionString(cluster.getConnectString())
                                                    .setNameSpace("DTEST"))) {
                curator.start();
                val nds = new ZkNodeDataStore(curator, MAPPER);
                IntStream.rangeClosed(1, 100)
                        .forEach(i -> nds.updateNodeData(ControllerTestUtils.generateExecutorNode(i)));
                val updater = mock(StateUpdater.class);
                val testExecuted = new AtomicInteger(0);
                val ids = new HashSet<String>();
                val removedIds = new HashSet<String>();
                doAnswer(invocationOnMock -> {
                    testExecuted.compareAndSet(0, 1);
                    invocationOnMock.getArgument(0, List.class)
                            .stream()
                            .map(n -> ((ExecutorNodeData) n).getState().getExecutorId())
                            .forEach(i -> ids.add((String) i));
                    return null;
                }).when(updater).updateClusterResources(anyList());
                doAnswer(invocationOnMock -> {
                    testExecuted.compareAndSet(-1, 2);
                    removedIds.addAll(invocationOnMock.getArgument(0));
                    return null;
                }).when(updater).remove(anyCollection());
                val le = mock(LeadershipEnsurer.class);
                when(le.isLeader()).thenReturn(true);
                val s = new ConsumingSyncSignal<Boolean>();
                when(le.onLeadershipStateChanged()).thenReturn(s);
                val obs = new ExecutorObserver(nds,
                                               updater,
                                               le,
                                               eventBus,
                                               ControllerOptions.DEFAULT,
                                               Duration.ofMillis(500));
                obs.start();
                s.dispatch(true);
                CommonTestUtils.waitUntil(() -> testExecuted.get() ==  1);
                assertFalse(ids.isEmpty());
                assertEquals(100, ids.size());
                IntStream.rangeClosed(1, 50)
                        .forEach(i -> nds.removeNodeData(ControllerTestUtils.generateExecutorNode(i)));
                testExecuted.set(-1);
                CommonTestUtils.waitUntil(() -> testExecuted.get() ==  2);
                assertFalse(removedIds.isEmpty());
                assertEquals(50, removedIds.size());
                obs.stop();
            }
        }
    }

    @Test
    void testStaleDataRemoval() throws Exception {
        val node = ControllerTestUtils.generateExecutorNode(1);
        val nds = mock(NodeDataStore.class);
        when(nds.nodes(NodeType.EXECUTOR)).thenReturn(List.of(node));
        val stateUpdater = mock(StateUpdater.class);
        val removed = new AtomicBoolean(false);
        doAnswer(new Answer() {
            @Override
            @SuppressWarnings("unchecked")
            public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
                val ids = (Set<String>)invocationOnMock.getArgument(0, Set.class);
                removed.set(ids.contains(node.getState().getExecutorId()));
                return null;
            }
        }).when(stateUpdater).remove(anyCollection());
        val le = mock(LeadershipEnsurer.class);
        when(le.isLeader()).thenReturn(true);
        when(le.onLeadershipStateChanged()).thenReturn(new ConsumingSyncSignal<>());
        val eb = new DroveEventBus();
        val eo = new ExecutorObserver(nds,
                                      stateUpdater,
                                      le,
                                      eb,
                                      ControllerOptions.DEFAULT
                                              .withStaleExecutorAge(io.dropwizard.util.Duration.seconds(3)),
                                      Duration.ofMillis(500));
        eo.start();
        CommonTestUtils.waitUntil(removed::get);
        assertTrue(removed.get());
        eo.stop();
    }
}