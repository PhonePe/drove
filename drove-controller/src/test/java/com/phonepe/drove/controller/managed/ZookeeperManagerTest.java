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