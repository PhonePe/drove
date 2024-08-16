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

package com.phonepe.drove.common.discovery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.drove.common.CommonUtils;
import com.phonepe.drove.common.zookeeper.ZkConfig;
import com.phonepe.drove.models.info.ExecutorResourceSnapshot;
import com.phonepe.drove.models.info.nodedata.ExecutorNodeData;
import com.phonepe.drove.models.info.nodedata.NodeTransportType;
import com.phonepe.drove.models.info.nodedata.NodeType;
import com.phonepe.drove.models.info.resources.PhysicalLayout;
import com.phonepe.drove.models.info.resources.available.AvailableCPU;
import com.phonepe.drove.models.info.resources.available.AvailableMemory;
import lombok.SneakyThrows;
import lombok.val;
import org.apache.curator.test.TestingCluster;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 *
 */
class ZkNodeDataStoreTest {

    @Test
    @SneakyThrows
    void testDS() {
        try (val cluster = new TestingCluster(1)) {
            cluster.start();
            val config = new ZkConfig();
            config.setConnectionString(cluster.getConnectString());
            val mapper = new ObjectMapper();
            CommonUtils.configureMapper(mapper);

            val curator = CommonUtils.buildCurator(config);
            curator.start();
            curator.blockUntilConnected();

            val store = new ZkNodeDataStore(curator, mapper);

            assertTrue(store.nodes(NodeType.EXECUTOR).isEmpty());
            val state = new ExecutorResourceSnapshot(
                    "abc",
                    new AvailableCPU(Collections.singletonMap(0, Collections.singleton(1)),
                                     Collections.singletonMap(0, Collections.singleton(0))),
                    new AvailableMemory(Collections.singletonMap(0, 1024L),
                                        Collections.singletonMap(0, 1024L)),
                    new PhysicalLayout(Collections.singletonMap(0, Collections.singleton(1)),
                                       Collections.singletonMap(0, 1024L)));
            val nodeData = new ExecutorNodeData("localhost",
                                                8080,
                                                NodeTransportType.HTTP,
                                                new Date(),
                                                state,
                                                List.of(),
                                                List.of(),
                                                Set.of(),
                                                false);
            store.updateNodeData(nodeData);
            var executors = store.nodes(NodeType.EXECUTOR);
            assertFalse(executors.isEmpty());
            assertEquals(nodeData, executors.get(0));
            store.removeNodeData(nodeData);
            executors = store.nodes(NodeType.EXECUTOR);
            assertTrue(executors.isEmpty());
        }
    }

    @Test
    @SneakyThrows
    void testNoNode() {
        try (val cluster = new TestingCluster(1)) {
            cluster.start();
            val config = new ZkConfig();
            config.setConnectionString(cluster.getConnectString());
            val mapper = new ObjectMapper();
            CommonUtils.configureMapper(mapper);

            val curator = CommonUtils.buildCurator(config);
            curator.start();
            curator.blockUntilConnected();

            val store = new ZkNodeDataStore(curator, mapper);
            var executors = store.nodes(NodeType.EXECUTOR);
            assertTrue(executors.isEmpty());

//            store.updateNodeData();
        }
    }
}