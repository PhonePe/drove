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

import lombok.SneakyThrows;
import lombok.val;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.imps.CuratorFrameworkState;
import org.apache.curator.retry.RetryNTimes;
import org.apache.curator.test.TestingCluster;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 *
 */
class ZookeeperManagerTest {

    @Test
    @SneakyThrows
    void test() {
        try (val cluster = new TestingCluster(1)) {
            cluster.start();
            try (val curator = CuratorFrameworkFactory.builder()
                    .connectString(cluster.getConnectString())
                    .namespace("DTEST")
                    .retryPolicy(new RetryNTimes(0, 10))
                    .sessionTimeoutMs(10)
                    .build()) {
                val zm = new ZookeeperManager(curator);
                zm.start();
                assertEquals(CuratorFrameworkState.STARTED, curator.getState());
                zm.stop();
            }
        }
    }
}