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

package com.phonepe.drove.controller.statedb;

import com.phonepe.drove.common.zookeeper.ZkConfig;
import com.phonepe.drove.controller.ControllerTestBase;
import com.phonepe.drove.models.common.ClusterState;
import com.phonepe.drove.models.common.ClusterStateData;
import lombok.SneakyThrows;
import lombok.val;
import org.apache.curator.test.TestingCluster;
import org.junit.jupiter.api.Test;

import static com.phonepe.drove.common.CommonUtils.buildCurator;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 */
class ZkClusterStateDBTest extends ControllerTestBase {
    @Test
    @SneakyThrows
    void test() {
        try(val cluster = new TestingCluster(1)) {
            cluster.start();
            try (val curator = buildCurator(new ZkConfig().setConnectionString(cluster.getConnectString())
                                                     .setNameSpace("DTEST"))) {
                curator.start();
                val db = new ZkClusterStateDB(curator, MAPPER);
                assertFalse(db.currentState().isPresent());
                assertTrue(db.setClusterState(ClusterState.NORMAL).isPresent());
                assertEquals(ClusterState.NORMAL,
                             db.currentState().map(ClusterStateData::getState).orElse(ClusterState.MAINTENANCE));
                assertTrue(db.setClusterState(ClusterState.MAINTENANCE).isPresent());
                assertEquals(ClusterState.MAINTENANCE,
                             db.currentState().map(ClusterStateData::getState).orElse(ClusterState.NORMAL));
                assertTrue(db.setClusterState(ClusterState.NORMAL).isPresent());
                assertEquals(ClusterState.NORMAL,
                             db.currentState().map(ClusterStateData::getState).orElse(ClusterState.MAINTENANCE));
                assertTrue(db.setClusterState(ClusterState.MAINTENANCE).isPresent());
            }
        }
    }
}