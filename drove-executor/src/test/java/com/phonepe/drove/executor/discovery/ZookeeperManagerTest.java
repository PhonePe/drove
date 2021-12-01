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
            val zkConfig = new ZkConfig();
            zkConfig.setConnectionString(tc.getConnectString());
            zkConfig.setNameSpace("drove");
            val curator = CommonUtils.buildCurator(zkConfig);
            val xkm = new ZookeeperManager(curator);
            xkm.start();
            assertEquals(CuratorFrameworkState.STARTED, curator.getState());
            xkm.stop();
            assertEquals(CuratorFrameworkState.STOPPED, curator.getState());
        }
    }

}