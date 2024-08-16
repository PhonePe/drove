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

import com.phonepe.drove.common.CommonUtils;
import com.phonepe.drove.common.zookeeper.ZkConfig;
import lombok.SneakyThrows;
import lombok.val;
import org.apache.curator.framework.imps.CuratorFrameworkState;
import org.apache.curator.test.TestingCluster;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 *
 */
class ZookeeperManagerTest {

    @Test
    @SneakyThrows
    void testZkManager() {
        try(val tc = new TestingCluster(1)) {
            tc.start();
            val zkConfig = new ZkConfig()
                    .setConnectionString(tc.getConnectString())
                    .setNameSpace("drove");
            val curator = CommonUtils.buildCurator(zkConfig);
            val xkm = new ZookeeperManager(curator);
            xkm.start();
            assertEquals(CuratorFrameworkState.STARTED, curator.getState());
            xkm.stop();
            assertEquals(CuratorFrameworkState.STOPPED, curator.getState());
        }
    }

}