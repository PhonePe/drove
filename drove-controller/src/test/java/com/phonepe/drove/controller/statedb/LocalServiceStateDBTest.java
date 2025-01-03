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

import com.phonepe.drove.common.zookeeper.ZookeeperTestExtension;
import com.phonepe.drove.controller.ControllerTestBase;
import com.phonepe.drove.controller.ControllerTestUtils;
import com.phonepe.drove.controller.managed.LeadershipEnsurer;
import com.phonepe.drove.controller.utils.ControllerUtils;
import com.phonepe.drove.models.instance.LocalServiceInstanceState;
import com.phonepe.drove.models.localservice.ActivationState;
import com.phonepe.drove.models.localservice.LocalServiceInfo;
import com.phonepe.drove.models.localservice.LocalServiceInstanceInfo;
import com.phonepe.drove.models.localservice.LocalServiceSpec;
import io.appform.signals.signals.ConsumingSyncSignal;
import lombok.SneakyThrows;
import lombok.val;
import org.apache.curator.framework.CuratorFramework;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.EnumSet;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests {@link ZKLocalServiceStateDB}
 */
@ExtendWith(ZookeeperTestExtension.class)
class LocalServiceStateDBTest extends ControllerTestBase {
    @Test
    @SneakyThrows
    void testServiceManagement(CuratorFramework curator) {
        val le = mock(LeadershipEnsurer.class);
        when(le.isLeader()).thenReturn(true);
        val s = new ConsumingSyncSignal<Boolean>();
        when(le.onLeadershipStateChanged()).thenReturn(s);
        val db = new CachingProxyLocalServiceStateDB(new ZKLocalServiceStateDB(curator, MAPPER), le);
        le.onLeadershipStateChanged().dispatch(true);

        {
            val spec = ControllerTestUtils.localServiceSpec();
            val serviceId = ControllerUtils.deployableObjectId(spec);
            val info = createInfo(serviceId, spec);

            assertTrue(db.updateService(serviceId, info));
            val created = db.service(serviceId).orElse(null);
            assertNotNull(created);
            assertEquals(ActivationState.INACTIVE, created.getActivationState());

            assertTrue(db.updateService(serviceId, created.withActivationState(ActivationState.ACTIVE)));
            val updated = db.service(serviceId).orElse(null);
            assertNotNull(updated);
            assertEquals(ActivationState.ACTIVE, updated.getActivationState());

            assertTrue(db.removeService(serviceId));
            assertTrue(db.service(serviceId).isEmpty());
        }
        {
            IntStream.range(0, 10)
                    .forEach(i -> {
                        val spec = ControllerTestUtils.localServiceSpec("LS-%d".formatted(i), 1);
                        val id = ControllerUtils.deployableObjectId(spec);
                        assertTrue(db.updateService(id, createInfo(id, spec)));
                    });
            assertEquals(10, db.services(0, Integer.MAX_VALUE).size());
        }
    }

    @Test
    void testInstanceManagement(CuratorFramework curator) {
        val le = mock(LeadershipEnsurer.class);
        when(le.isLeader()).thenReturn(true);
        val s = new ConsumingSyncSignal<Boolean>();
        when(le.onLeadershipStateChanged()).thenReturn(s);
        val db = new CachingProxyLocalServiceStateDB(new ZKLocalServiceStateDB(curator, MAPPER), le);
        le.onLeadershipStateChanged().dispatch(true);

        val spec = ControllerTestUtils.localServiceSpec();
        val serviceId = ControllerUtils.deployableObjectId(spec);

        IntStream.range(0, 10)
                .forEach(i -> {
                    final var instanceId = "SI-%d".formatted(i);
                    db.updateInstanceState(serviceId,
                                           instanceId,
                                           new LocalServiceInstanceInfo(serviceId,
                                                                        spec.getName(),
                                                                        instanceId,
                                                                        "E001",
                                                                        null,
                                                                        null,
                                                                        LocalServiceInstanceState.HEALTHY,
                                                                        Map.of(),
                                                                        null,
                                                                        Date.from(Instant.now()),
                                                                        Date.from(Instant.now())));
                });
        assertEquals(10, db.instances(serviceId, EnumSet.of(LocalServiceInstanceState.HEALTHY), false).size());
        assertTrue(db.instances(serviceId, EnumSet.of(LocalServiceInstanceState.UNHEALTHY), false).isEmpty());
        assertFalse(db.instance(serviceId, "SI-1").isEmpty());
        assertTrue(db.deleteInstanceState(serviceId, "SI-1"));
        assertTrue(db.instance(serviceId, "SI-1").isEmpty());
        assertEquals(9, db.instances(serviceId, EnumSet.of(LocalServiceInstanceState.HEALTHY), false).size());
        db.deleteAllInstancesForService(serviceId);
        assertTrue(db.instances(serviceId, EnumSet.of(LocalServiceInstanceState.HEALTHY), false).isEmpty());

        db.updateInstanceState(serviceId, "SI-STALE", new LocalServiceInstanceInfo(serviceId,
                                                                             spec.getName(),
                                                                             "SI-STALE",
                                                                             "E001",
                                                                             null,
                                                                             null,
                                                                             LocalServiceInstanceState.HEALTHY,
                                                                             Map.of(),
                                                                             null,
                                                                             Date.from(Instant.now()),
                                                                             Date.from(Instant.now().minus(1, ChronoUnit.DAYS))));
        assertTrue(db.instances(serviceId, EnumSet.of(LocalServiceInstanceState.HEALTHY), false).isEmpty());
        assertFalse(db.instances(serviceId, EnumSet.of(LocalServiceInstanceState.HEALTHY), true).isEmpty());


    }

    private static LocalServiceInfo createInfo(String serviceId, LocalServiceSpec spec) {
        return new LocalServiceInfo(serviceId,
                                    spec,
                                    1,
                                    ActivationState.INACTIVE,
                                    Date.from(Instant.now()),
                                    Date.from(Instant.now()));
    }
}