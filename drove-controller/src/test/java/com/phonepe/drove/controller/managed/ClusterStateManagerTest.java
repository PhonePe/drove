package com.phonepe.drove.controller.managed;

import com.phonepe.drove.controller.statedb.ClusterStateDB;
import com.phonepe.drove.controller.testsupport.InMemoryClusterStateDB;
import com.phonepe.drove.models.common.ClusterState;
import com.phonepe.drove.models.common.ClusterStateData;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests {@link ClusterStateManager}
 */
class ClusterStateManagerTest {
    @Test
    @SneakyThrows
    void testNoExistingState() {
        val cdb = new InMemoryClusterStateDB();
        val cm = new ClusterStateManager(cdb);
        cm.start();
        assertEquals(ClusterState.NORMAL, cdb.currentState().map(ClusterStateData::getState).orElse(null));
        cm.stop();
    }

    @Test
    @SneakyThrows
    void testNoExistingStateUpdateFail() {
        val cdb = mock(ClusterStateDB.class);
        when(cdb.setClusterState(any())).thenReturn(Optional.empty());
        val cm = new ClusterStateManager(cdb);
        cm.start();
        assertTrue(cdb.currentState().isEmpty());
        cm.stop();
    }

    @Test
    @SneakyThrows
    void testHasExistingState() {
        val cdb = new InMemoryClusterStateDB();
        val cm = new ClusterStateManager(cdb);
        cdb.setClusterState(ClusterState.MAINTENANCE);
        cm.start();
        assertEquals(ClusterState.MAINTENANCE, cdb.currentState().map(ClusterStateData::getState).orElse(null));
        cm.stop();
    }
}