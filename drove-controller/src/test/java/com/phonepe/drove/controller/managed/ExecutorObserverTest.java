/*
 *  Copyright (c) 2022 Original Author(s), PhonePe India Pvt. Ltd.
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

import com.phonepe.drove.common.zookeeper.ZkConfig;
import com.phonepe.drove.common.zookeeper.ZkUtils;
import com.phonepe.drove.controller.ControllerTestBase;
import com.phonepe.drove.controller.ControllerTestUtils;
import com.phonepe.drove.controller.engine.StateUpdater;
import com.phonepe.drove.controller.event.DroveEventBus;
import com.phonepe.drove.models.info.nodedata.ExecutorNodeData;
import lombok.SneakyThrows;
import lombok.val;
import org.apache.curator.test.TestingCluster;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static com.phonepe.drove.common.CommonUtils.buildCurator;
import static org.awaitility.Awaitility.await;
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
                IntStream.rangeClosed(1, 100)
                        .forEach(i -> {
                            val node = ControllerTestUtils.generateExecutorNode(i);
                            assertTrue(ZkUtils.setNodeData(curator,
                                                           "/executor/" + node.getState().getExecutorId(),
                                                           MAPPER,
                                                           node));
                        });
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

                val obs = new ExecutorObserver(curator, MAPPER, updater, eventBus);
                obs.start();
                waitForTest(testExecuted, 1);
                assertFalse(ids.isEmpty());
                assertEquals(100, ids.size());
                IntStream.rangeClosed(1, 50)
                        .forEach(i -> ZkUtils.deleteNode(curator, "/executor/" + ControllerTestUtils.executorId(i)));
                testExecuted.set(-1);
                waitForTest(testExecuted, 2);
                assertFalse(removedIds.isEmpty());
                assertEquals(50, removedIds.size());
                obs.stop();
            }
        }
    }

    private void waitForTest(AtomicInteger testExecuted, int required) {
        await()
                .atMost(Duration.ofMinutes(1))
                .until(() -> testExecuted.get() == required);
    }
}