package com.phonepe.drove.controller.statedb;

import com.phonepe.drove.controller.ControllerTestBase;
import com.phonepe.drove.controller.testsupport.InMemoryClusterStateDB;
import com.phonepe.drove.models.common.ClusterState;
import com.phonepe.drove.models.common.ClusterStateData;
import lombok.val;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 *
 */
class CachingProxyClusterStateDBTest extends ControllerTestBase {

    @Test
    void test() {
        val db = new CachingProxyClusterStateDB(new InMemoryClusterStateDB());
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