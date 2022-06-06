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
            try (val curatror = buildCurator(new ZkConfig().setConnectionString(cluster.getConnectString())
                                                     .setNameSpace("DTEST"))) {
                curatror.start();
                val db = new ZkClusterStateDB(curatror, MAPPER);
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