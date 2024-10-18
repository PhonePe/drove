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

import com.phonepe.drove.common.discovery.NodeDataStore;
import com.phonepe.drove.common.discovery.leadership.LeadershipObserver;
import com.phonepe.drove.models.info.nodedata.*;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.phonepe.drove.common.CommonTestUtils.waitUntil;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 *
 */
@Slf4j
class ManagedLeadershipObserverTest {

    @Test
    @SneakyThrows
    void testObserverSuccess() {
        val nds = mock(NodeDataStore.class);
        when(nds.nodes(NodeType.CONTROLLER)).thenReturn(List.of(new ControllerNodeData("h1",
                                                                                       3000,
                                                                                       NodeTransportType.HTTP,
                                                                                       new Date(),
                                                                                       false),
                                                                new ControllerNodeData("h2",
                                                                                       3000,
                                                                                       NodeTransportType.HTTP,
                                                                                       new Date(),
                                                                                       true)));
        val obs = new ManagedLeadershipObserver(new LeadershipObserver(nds));
        obs.start();
        waitUntil(() -> obs.leader().isPresent());
        val leader = obs.leader().orElse(null);
        assertNotNull(leader);
        assertTrue(leader.isLeader());
        assertEquals("h2", leader.getHostname());
        obs.stop();
    }

    @Test
    @SneakyThrows
    void testObserverNoLeader() {
        val nds = mock(NodeDataStore.class);
        when(nds.nodes(NodeType.CONTROLLER)).thenReturn(List.of(new ControllerNodeData("h1",
                                                                                       3000,
                                                                                       NodeTransportType.HTTP,
                                                                                       new Date(),
                                                                                       false),
                                                                new ControllerNodeData("h2",
                                                                                       3000,
                                                                                       NodeTransportType.HTTP,
                                                                                       new Date(),
                                                                                       false)));
        val obs = new ManagedLeadershipObserver(new LeadershipObserver(nds));
        obs.start();
        waitUntil(() -> obs.leader().isEmpty());
        val leader = obs.leader().orElse(null);
        assertNull(leader);
        obs.stop();
    }

    @Test
    @SneakyThrows
    void testObserverTransientState() {
        val nds = mock(NodeDataStore.class);
        val ctr = new AtomicInteger(0);
        when(nds.nodes(NodeType.CONTROLLER)).thenAnswer(new Answer<List<NodeData>>() {
            @Override
            public List<NodeData> answer(InvocationOnMock mock) throws Throwable {
                val currVal = ctr.get();
                if (currVal == 1) {
                    log.info("Returning leader : h2");
                    return List.of(new ControllerNodeData("h1", 3000, NodeTransportType.HTTP, new Date(), false),
                                   new ControllerNodeData("h2", 3000, NodeTransportType.HTTP, new Date(), true));
                }
                if (currVal == 2) {
                    log.info("Returning leader : h1");
                    return List.of(new ControllerNodeData("h1", 3000, NodeTransportType.HTTP, new Date(), true),
                                   new ControllerNodeData("h2", 3000, NodeTransportType.HTTP, new Date(), false));
                }
                log.info("Returning no leader");
                return List.of(new ControllerNodeData("h1", 3000, NodeTransportType.HTTP, new Date(), false),
                               new ControllerNodeData("h2", 3000, NodeTransportType.HTTP, new Date(), false));
            }
        });
        val obs = new ManagedLeadershipObserver(new LeadershipObserver(nds));
        obs.start();
        {
            ctr.set(0);
            waitUntil(() -> obs.leader().isEmpty());
            val leader = obs.leader().orElse(null);
            assertNull(leader);
        }
        {
            ctr.set(1);
            waitUntil(() -> {
                val leader = obs.leader().orElse(null);
                return null != leader && leader.getHostname().equals("h2");
            });
            val leader = obs.leader().orElse(null);

            assertNotNull(leader);
            assertTrue(leader.isLeader());
            assertEquals("h2", leader.getHostname());
        }
        {
            ctr.set(2);
            waitUntil(() -> {
                val leader = obs.leader().orElse(null);
                return null != leader && leader.getHostname().equals("h1");
            });
            val leader = obs.leader().orElse(null);

            assertNotNull(leader);
            assertTrue(leader.isLeader());
            assertEquals("h1", leader.getHostname());
        }
        {
            ctr.set(3);
            waitUntil(() -> obs.leader().isEmpty());
            val leader = obs.leader().orElse(null);
            assertNull(leader);
        }
        obs.stop();
    }

    @Test
    @SneakyThrows
    void testObserverRandomData() {
        val nds = mock(NodeDataStore.class);
        when(nds.nodes(NodeType.CONTROLLER)).thenReturn(
                List.of(new ExecutorNodeData("h",
                                             4000,
                                             NodeTransportType.HTTP,
                                             new Date(),
                                             null,
                                             null,
                                             null,
                                             null,
                                             null,
                                             ExecutorState.ACTIVE)));
        val obs = new ManagedLeadershipObserver(new LeadershipObserver(nds));
        obs.start();
        waitUntil(() -> obs.leader().isEmpty());
        val leader = obs.leader().orElse(null);
        assertNull(leader);
        obs.stop();
    }
}