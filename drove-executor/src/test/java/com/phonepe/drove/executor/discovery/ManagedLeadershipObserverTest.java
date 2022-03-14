package com.phonepe.drove.executor.discovery;

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
                val currVal = ctr.incrementAndGet();
                if (currVal == 2) {
                    log.info("Returning leader : h2");
                    return List.of(new ControllerNodeData("h1", 3000, NodeTransportType.HTTP, new Date(), false),
                                   new ControllerNodeData("h2", 3000, NodeTransportType.HTTP, new Date(), true));
                }
                if (currVal == 3) {
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
            waitUntil(() -> ctr.get() == 2);
            val leader = obs.leader().orElse(null);

            assertNotNull(leader);
            assertTrue(leader.isLeader());
            assertEquals("h2", leader.getHostname());
        }
        {
            waitUntil(() -> ctr.get() == 3);
            val leader = obs.leader().orElse(null);

            assertNotNull(leader);
            assertTrue(leader.isLeader());
            assertEquals("h1", leader.getHostname());
        }
        {
            waitUntil(() -> ctr.get() > 3);
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
                                             false)));
        val obs = new ManagedLeadershipObserver(new LeadershipObserver(nds));
        obs.start();
        waitUntil(() -> obs.leader().isEmpty());
        val leader = obs.leader().orElse(null);
        assertNull(leader);
        obs.stop();
    }
}