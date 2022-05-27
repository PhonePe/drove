package com.phonepe.drove.controller.statedb;

import com.phonepe.drove.controller.managed.LeadershipEnsurer;
import com.phonepe.drove.controller.testsupport.InMemoryInstanceInfoDB;
import com.phonepe.drove.models.instance.InstanceState;
import io.appform.signals.signals.ConsumingSyncSignal;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 *
 */
class CachingProxyInstanceInfoDBTest {

    @Test
    void testCaching() {
        val leadershipSignal = new ConsumingSyncSignal<Boolean>();
        val leadershipEnsurer = mock(LeadershipEnsurer.class);
        when(leadershipEnsurer.onLeadershipStateChanged()).thenReturn(leadershipSignal);

        val root = new InMemoryInstanceInfoDB();
        val db = new CachingProxyInstanceInfoDB(root, leadershipEnsurer);
        assertTrue(db.instances("ABC", EnumSet.allOf(InstanceState.class), 0, Integer.MAX_VALUE).isEmpty());
    }
}