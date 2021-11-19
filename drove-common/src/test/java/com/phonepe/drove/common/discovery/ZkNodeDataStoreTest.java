package com.phonepe.drove.common.discovery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.drove.common.CommonUtils;
import com.phonepe.drove.common.zookeeper.ZkConfig;
import com.phonepe.drove.models.info.ExecutorResourceSnapshot;
import com.phonepe.drove.models.info.nodedata.ExecutorNodeData;
import com.phonepe.drove.models.info.nodedata.NodeType;
import com.phonepe.drove.models.info.resources.available.AvailableCPU;
import com.phonepe.drove.models.info.resources.available.AvailableMemory;
import lombok.SneakyThrows;
import lombok.val;
import org.apache.curator.test.TestingCluster;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 *
 */
class ZkNodeDataStoreTest {

    @Test
    @SneakyThrows
    void testDS() {
        val cluster = new TestingCluster(1);
        cluster.start();
        val config = new ZkConfig();
        config.setConnectionString(cluster.getConnectString());
        val mapper = new ObjectMapper();
        CommonUtils.configureMapper(mapper);

        val curator = CommonUtils.buildCurator(config);
        curator.start();
        curator.blockUntilConnected();

        val store = new ZkNodeDataStore(curator, mapper);

        val state = new ExecutorResourceSnapshot(
                "abc",
                new AvailableCPU(Collections.singletonMap(0, Collections.singleton(1)),
                                 Collections.singletonMap(0, Collections.singleton(0))),
                new AvailableMemory(Collections.singletonMap(0, 1024L),
                                    Collections.singletonMap(0, 1024L)));
        val nodeData = new ExecutorNodeData("localhost",
                                            8080,
                                            new Date(),
                                            state, Collections.emptyList());
        store.updateNodeData(nodeData);
        var executors = store.nodes(NodeType.EXECUTOR);
        assertFalse(executors.isEmpty());
        assertEquals(nodeData, executors.get(0));
        store.removeNodeData(nodeData);
        executors = store.nodes(NodeType.EXECUTOR);
        assertTrue(executors.isEmpty());
    }
}