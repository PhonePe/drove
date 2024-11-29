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
import com.phonepe.drove.controller.utils.ControllerUtils;
import com.phonepe.drove.models.application.ApplicationInfo;
import lombok.SneakyThrows;
import lombok.val;
import org.apache.curator.framework.CuratorFramework;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Date;
import java.util.stream.IntStream;

import static com.phonepe.drove.controller.ControllerTestUtils.appSpec;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 *
 */
@ExtendWith(ZookeeperTestExtension.class)
class ZkApplicationStateDBTest extends ControllerTestBase {

    @Test
    @SneakyThrows
    void testSingle(final CuratorFramework curator) {
        val db = new ZkApplicationStateDB(curator, MAPPER);
        assertTrue(db.applications(0, Integer.MAX_VALUE).isEmpty());
        val spec = appSpec();
        val appId = ControllerUtils.deployableObjectId(spec);
        val appInfo = new ApplicationInfo(appId, spec, 1, new Date(), new Date());
        assertTrue(db.updateApplicationState(appId, appInfo));
        {
            val apps = db.applications(0, Integer.MAX_VALUE);
            assertFalse(apps.isEmpty());
            assertEquals(1, apps.size());
            assertEquals(appInfo, apps.get(0));
        }
        { //Ensure saved app is present
            val app = db.application(appId);
            assertTrue(app.isPresent());
            assertEquals(appInfo, app.get());
        }
        {
            assertNull(db.application("INVALID").orElse(null));
        }
        {
            assertTrue(db.deleteApplicationState(appId));
            assertNull(db.application(appId).orElse(null));
        }
    }

    @Test
    @SneakyThrows
    void testException() {
        val curatorFramework = mock(CuratorFramework.class);
        when(curatorFramework.getChildren()).thenThrow(new IllegalStateException("Test exception"));
        val db = new ZkApplicationStateDB(curatorFramework, MAPPER);
        assertTrue(db.applications(0, Integer.MAX_VALUE).isEmpty());
    }

    @Test
    @SneakyThrows
    void testMulti(final CuratorFramework curator) {
        val db = new ZkApplicationStateDB(curator, MAPPER);
        IntStream.rangeClosed(1, 100)
                .forEach(i -> {
                    val spec = appSpec(i);
                    val appId = ControllerUtils.deployableObjectId(spec);
                    val appInfo = new ApplicationInfo(appId, spec, 1, new Date(), new Date());
                    db.updateApplicationState(appId, appInfo);
                });
        assertEquals(100, db.applications(0, Integer.MAX_VALUE).size());
    }

    @Test
    @SneakyThrows
    void testUpdate(final CuratorFramework curator) {
        val db = new ZkApplicationStateDB(curator, MAPPER);
        assertTrue(db.applications(0, Integer.MAX_VALUE).isEmpty());
        val spec = appSpec();
        val appId = ControllerUtils.deployableObjectId(spec);
        val appInfo = new ApplicationInfo(appId, spec, 1, new Date(), new Date());
        assertTrue(db.updateApplicationState(appId, appInfo));
        {
            val apps = db.applications(0, Integer.MAX_VALUE);
            assertFalse(apps.isEmpty());
            assertEquals(1, apps.size());
            assertEquals(appInfo, apps.get(0));
        }
        {
            assertEquals(1, db.application(appId).map(ApplicationInfo::getInstances).orElse(0L));
            assertTrue(db.updateInstanceCount(appId, 5));
            assertEquals(5, db.application(appId).map(ApplicationInfo::getInstances).orElse(0L));
        }
    }
}