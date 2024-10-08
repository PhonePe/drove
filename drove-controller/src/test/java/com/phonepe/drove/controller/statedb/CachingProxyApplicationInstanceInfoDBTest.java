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

import com.phonepe.drove.controller.ControllerTestBase;
import com.phonepe.drove.controller.managed.LeadershipEnsurer;
import com.phonepe.drove.controller.testsupport.InMemoryApplicationInstanceInfoDB;
import com.phonepe.drove.controller.utils.ControllerUtils;
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
import static com.phonepe.drove.controller.utils.ControllerUtils.deployableObjectId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 *
 */
class CachingProxyApplicationInstanceInfoDBTest extends ControllerTestBase {

    @Test
    void testCaching() {
        val leadershipSignal = new ConsumingSyncSignal<Boolean>();
        val leadershipEnsurer = mock(LeadershipEnsurer.class);
        when(leadershipEnsurer.onLeadershipStateChanged()).thenReturn(leadershipSignal);

        val root = new InMemoryApplicationInstanceInfoDB();
        val db = new CachingProxyApplicationInstanceInfoDB(root, leadershipEnsurer);
        assertTrue(db.instances("ABC", EnumSet.allOf(InstanceState.class), 0, Integer.MAX_VALUE).isEmpty());

        val spec = appSpec();
        val appId = ControllerUtils.deployableObjectId(spec);

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