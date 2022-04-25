package com.phonepe.drove.controller.statedb;

import com.phonepe.drove.common.zookeeper.ZkConfig;
import com.phonepe.drove.controller.ControllerTestBase;
import com.phonepe.drove.models.application.ApplicationInfo;
import lombok.SneakyThrows;
import lombok.val;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.test.TestingCluster;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.stream.IntStream;

import static com.phonepe.drove.common.CommonUtils.buildCurator;
import static com.phonepe.drove.controller.ControllerTestUtils.appSpec;
import static com.phonepe.drove.controller.utils.ControllerUtils.appId;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 *
 */
class ZkApplicationStateDBTest extends ControllerTestBase {

    @Test
    @SneakyThrows
    void testSingle() {
        try(val cluster = new TestingCluster(1)) {
            cluster.start();
            try (val curatror = buildCurator(new ZkConfig().setConnectionString(cluster.getConnectString()).setNameSpace("DTEST"))) {
                curatror.start();
                val db = new ZkApplicationStateDB(curatror, MAPPER);
                assertTrue(db.applications(0, Integer.MAX_VALUE).isEmpty());
                val spec = appSpec();
                val appId = appId(spec);
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
    void testMulti() {
        try(val cluster = new TestingCluster(1)) {
            cluster.start();
            try (val curatror = buildCurator(new ZkConfig().setConnectionString(cluster.getConnectString())
                                                     .setNameSpace("DTEST"))) {
                curatror.start();
                val db = new ZkApplicationStateDB(curatror, MAPPER);
                IntStream.rangeClosed(1, 100)
                        .forEach(i -> {
                            val spec = appSpec(i);
                            val appId = appId(spec);
                            val appInfo = new ApplicationInfo(appId, spec, 1, new Date(), new Date());
                            db.updateApplicationState(appId, appInfo);
                        });
                assertEquals(100, db.applications(0, Integer.MAX_VALUE).size());
            }
        }
    }

    @Test
    @SneakyThrows
    void testUpdate() {
        try(val cluster = new TestingCluster(1)) {
            cluster.start();
            try (val curatror = buildCurator(new ZkConfig().setConnectionString(cluster.getConnectString()).setNameSpace("DTEST"))) {
                curatror.start();
                val db = new ZkApplicationStateDB(curatror, MAPPER);
                assertTrue(db.applications(0, Integer.MAX_VALUE).isEmpty());
                val spec = appSpec();
                val appId = appId(spec);
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
    }
}