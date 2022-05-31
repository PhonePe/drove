package com.phonepe.drove.controller.statedb;

import com.phonepe.drove.controller.ControllerTestBase;
import com.phonepe.drove.controller.managed.LeadershipEnsurer;
import com.phonepe.drove.controller.testsupport.InMemoryInstanceInfoDB;
import com.phonepe.drove.models.instance.InstanceState;
import io.appform.signals.signals.ConsumingSyncSignal;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.stream.IntStream;

import static com.phonepe.drove.common.CommonUtils.sublist;
import static com.phonepe.drove.controller.ControllerTestUtils.appSpec;
import static com.phonepe.drove.controller.ControllerTestUtils.generateInstanceInfo;
import static com.phonepe.drove.controller.utils.ControllerUtils.appId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 *
 */
class CachingProxyInstanceInfoDBTest extends ControllerTestBase {

    @Test
    void testCaching() {
        val leadershipSignal = new ConsumingSyncSignal<Boolean>();
        val leadershipEnsurer = mock(LeadershipEnsurer.class);
        when(leadershipEnsurer.onLeadershipStateChanged()).thenReturn(leadershipSignal);

        val root = new InMemoryInstanceInfoDB();
        val db = new CachingProxyInstanceInfoDB(root, leadershipEnsurer);
        assertTrue(db.instances("ABC", EnumSet.allOf(InstanceState.class), 0, Integer.MAX_VALUE).isEmpty());

        val spec = appSpec();
        val appId = appId(spec);

        {
            val generatedInstanceIds = new ArrayList<String>();
            IntStream.rangeClosed(1, 100)
                    .mapToObj(i -> generateInstanceInfo(appId, spec, i))
                    .peek(ii -> generatedInstanceIds.add(ii.getInstanceId()))
                    .forEach(ii -> db.updateInstanceState(appId, ii.getInstanceId(), ii));
            generatedInstanceIds.forEach(iId -> assertTrue(db.instance(appId, iId).isPresent()));
            val instances = db.instances(appId, EnumSet.allOf(InstanceState.class), 0, Integer.MAX_VALUE);
            assertEquals(100, instances.size());
            sublist(instances, 0, 50)
                    .forEach(ii -> db.deleteInstanceState(appId, ii.getInstanceId()));
            assertEquals(50, db.instances(appId, EnumSet.allOf(InstanceState.class), 0, Integer.MAX_VALUE).size());
            db.deleteAllInstancesForApp(appId);
            assertTrue(db.instances(appId, EnumSet.allOf(InstanceState.class), 0, Integer.MAX_VALUE).isEmpty());
        }

        {
            IntStream.rangeClosed(1, 100)
                    .mapToObj(i -> generateInstanceInfo(appId, spec, i))
                    .forEach(ii -> db.updateInstanceState(appId, ii.getInstanceId(), ii));
            assertEquals(100, db.instances(appId, EnumSet.allOf(InstanceState.class), 0, Integer.MAX_VALUE).size());
            leadershipSignal.dispatch(true);
            root.deleteAllInstancesForApp(appId);
            assertTrue(db.instances(appId, EnumSet.allOf(InstanceState.class), 0, Integer.MAX_VALUE).isEmpty());
        }

        {
            val oldDate = new Date(new Date().getTime() - 36_00_000);
            val generatedInstanceIds = new ArrayList<String>();
            IntStream.rangeClosed(1, 100)
                    .mapToObj(i -> generateInstanceInfo(appId, spec, i, InstanceState.HEALTHY, oldDate, null))
                    .peek(i -> generatedInstanceIds.add(i.getInstanceId()))
                    .forEach(ii -> db.updateInstanceState(appId, ii.getInstanceId(), ii));
            assertTrue(db.instances(appId, EnumSet.allOf(InstanceState.class), 0, Integer.MAX_VALUE).isEmpty());
            assertEquals(100,
                         db.instances(appId, EnumSet.of(InstanceState.HEALTHY), 0, Integer.MAX_VALUE, true).size());
            generatedInstanceIds.forEach(iId -> assertTrue(db.instance(appId, iId).isPresent()));
            db.markStaleInstances(appId);
            assertEquals(100, db.instances(appId, EnumSet.of(InstanceState.LOST), 0, Integer.MAX_VALUE).size());

        }

    }
}