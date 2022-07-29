package com.phonepe.drove.common.discovery.leadership;

import com.phonepe.drove.common.CommonTestUtils;
import com.phonepe.drove.common.discovery.NodeDataStore;
import com.phonepe.drove.models.info.nodedata.ControllerNodeData;
import com.phonepe.drove.models.info.nodedata.NodeData;
import com.phonepe.drove.models.info.nodedata.NodeTransportType;
import com.phonepe.drove.models.info.nodedata.NodeType;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 *
 */
class LeadershipObserverTest {

    @Test
    void test() {
        val dataStore = mock(NodeDataStore.class);
        val changeLeader = new AtomicBoolean();
        when(dataStore.nodes(NodeType.CONTROLLER))
                .thenAnswer(
                        (Answer<List<NodeData>>) invocationOnMock -> {
                            if(changeLeader.get()) {
                                return List.of(
                                        new ControllerNodeData("host1", 8080, NodeTransportType.HTTP, new Date(), false),
                                        new ControllerNodeData("host2", 8080, NodeTransportType.HTTP, new Date(), true));
                            }
                            return List.of(
                                    new ControllerNodeData("host1", 8080, NodeTransportType.HTTP, new Date(), true),
                                    new ControllerNodeData("host2", 8080, NodeTransportType.HTTP, new Date(), false));
                        });

        val l = new LeadershipObserver(dataStore);
        l.start();
        CommonTestUtils.waitUntil(() -> l.leader().isPresent());
        assertEquals("host1", l.leader().map(NodeData::getHostname).orElse(""));
        changeLeader.set(true);
        CommonTestUtils.waitUntil(() -> l.leader().map(NodeData::getHostname).orElse("").equals("host2"));
        assertEquals("host2", l.leader().map(NodeData::getHostname).orElse(""));
        l.stop();
    }

}