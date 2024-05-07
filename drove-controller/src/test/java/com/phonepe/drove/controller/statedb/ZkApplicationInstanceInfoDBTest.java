package com.phonepe.drove.controller.statedb;

import com.phonepe.drove.common.zookeeper.ZkConfig;
import com.phonepe.drove.controller.ControllerTestBase;
import com.phonepe.drove.controller.ControllerTestUtils;
import com.phonepe.drove.controller.utils.ControllerUtils;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.instance.InstanceState;
import lombok.SneakyThrows;
import lombok.val;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.test.TestingCluster;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.phonepe.drove.common.CommonUtils.buildCurator;
import static com.phonepe.drove.models.instance.InstanceState.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 *
 */
class ZkApplicationInstanceInfoDBTest extends ControllerTestBase {

    @Test
    @SneakyThrows
    void testSaveRetrieve() {
        try (val cluster = new TestingCluster(1)) {
            cluster.start();
            try (val curator = buildCurator(new ZkConfig().setConnectionString(cluster.getConnectString())
                                                    .setNameSpace("DTEST"))) {
                curator.start();
                val db = new ZkApplicationInstanceInfoDB(curator, MAPPER);
                val spec = ControllerTestUtils.appSpec();
                val appId = ControllerUtils.deployableObjectId(spec);
                val instances = IntStream.rangeClosed(1, 100)
                        .mapToObj(i -> {
                            val ii = ControllerTestUtils.generateInstanceInfo(appId, spec, i);
                            assertTrue(db.updateInstanceState(appId, ii.getInstanceId(), ii));
                            return ii.getInstanceId();
                        })
                        .collect(Collectors.toSet());
                val retrieved = db.healthyInstances(appId)
                        .stream()
                        .map(InstanceInfo::getInstanceId)
                        .collect(Collectors.toSet());
                assertEquals(instances.size(), retrieved.size());
                assertTrue(instances.containsAll(retrieved));
                assertNotNull(db.instance(appId, "AI-00016").orElse(null));
                assertTrue(instances.stream()
                        .allMatch(iid -> db.deleteInstanceState(appId, iid)));
                assertTrue(db.healthyInstances(appId).isEmpty());
            }
        }
    }

    @Test
    @SneakyThrows
    void testException() {
        val curator = mock(CuratorFramework.class);
        when(curator.getChildren()).thenThrow(new IllegalStateException("Test error"));
        val db = new ZkApplicationInstanceInfoDB(curator, MAPPER);
        assertThrows(IllegalStateException.class,
                     () -> db.activeInstances("ERR", 0, 1));
        assertThrows(IllegalStateException.class,
                     () -> db.oldInstances("ERR", 0, 1));
        assertThrows(IllegalStateException.class,
                     () -> db.markStaleInstances("ERR"));

    }

    @Test
    @SneakyThrows
    void testCountActive() {
        try (val cluster = new TestingCluster(1)) {
            cluster.start();
            try (val curator = buildCurator(new ZkConfig().setConnectionString(cluster.getConnectString())
                                                    .setNameSpace("DTEST"))) {
                curator.start();
                val db = new ZkApplicationInstanceInfoDB(curator, MAPPER);
                val spec = ControllerTestUtils.appSpec();
                val appId = ControllerUtils.deployableObjectId(spec);
                val instances = IntStream.rangeClosed(1, 100)
                        .mapToObj(i -> {
                            val ii = ControllerTestUtils.generateInstanceInfo(appId, spec, i, PROVISIONING);
                            assertTrue(db.updateInstanceState(appId, ii.getInstanceId(), ii));
                            return ii.getInstanceId();
                        })
                        .collect(Collectors.toSet());
                {
                    val retrieved = db.instances(appId, Set.of(PROVISIONING), 0, Integer.MAX_VALUE)
                            .stream()
                            .map(InstanceInfo::getInstanceId)
                            .collect(Collectors.toSet());
                    assertEquals(instances.size(), retrieved.size());
                    assertTrue(instances.containsAll(retrieved));
                    assertEquals(instances.size(), db.instanceCount(appId, PROVISIONING));
                }
                {
                    val retrieved = db.activeInstances(appId, 0, Integer.MAX_VALUE)
                            .stream()
                            .filter(ii -> ii.getState().equals(PROVISIONING))
                            .map(InstanceInfo::getInstanceId)
                            .collect(Collectors.toSet());
                    assertEquals(instances.size(), retrieved.size());
                    assertTrue(instances.containsAll(retrieved));

                }
                assertTrue(instances.stream()
                        .allMatch(iid -> db.deleteInstanceState(appId, iid)));
                assertTrue(db.healthyInstances(appId).isEmpty());
            }
        }
    }

    @Test
    @SneakyThrows
    void testDeleteAll() {
        try (val cluster = new TestingCluster(1)) {
            cluster.start();
            try (val curator = buildCurator(new ZkConfig().setConnectionString(cluster.getConnectString())
                                                    .setNameSpace("DTEST"))) {
                curator.start();
                val db = new ZkApplicationInstanceInfoDB(curator, MAPPER);
                val spec = ControllerTestUtils.appSpec();
                val appId = ControllerUtils.deployableObjectId(spec);
                val instances = IntStream.rangeClosed(1, 100)
                        .mapToObj(i -> {
                            val ii = ControllerTestUtils.generateInstanceInfo(appId, spec, i);
                            assertTrue(db.updateInstanceState(appId, ii.getInstanceId(), ii));
                            return ii.getInstanceId();
                        })
                        .collect(Collectors.toSet());
                val retrieved = db.healthyInstances(appId)
                        .stream()
                        .map(InstanceInfo::getInstanceId)
                        .collect(Collectors.toSet());
                assertEquals(instances.size(), retrieved.size());
                assertTrue(instances.containsAll(retrieved));
                assertNotNull(db.instance(appId, "AI-00016").orElse(null));
                assertTrue(db.deleteAllInstancesForApp(appId));
                assertTrue(db.healthyInstances(appId).isEmpty());
            }
        }
    }

    @Test
    @SneakyThrows
    void testStaleUpdate() {
        try (val cluster = new TestingCluster(1)) {
            cluster.start();
            try (val curator = buildCurator(new ZkConfig().setConnectionString(cluster.getConnectString())
                                                    .setNameSpace("DTEST"))) {
                curator.start();
                val db = new ZkApplicationInstanceInfoDB(curator, MAPPER);
                val spec = ControllerTestUtils.appSpec();
                val appId = ControllerUtils.deployableObjectId(spec);
                val instances = IntStream.rangeClosed(1, 100)
                        .mapToObj(i -> {
                            val ii = ControllerTestUtils.generateInstanceInfo(appId, spec, i, HEALTHY);
                            assertTrue(db.updateInstanceState(appId, ii.getInstanceId(), ii));
                            return ii.getInstanceId();
                        })
                        .collect(Collectors.toSet());
                val retrieved = db.healthyInstances(appId)
                        .stream()
                        .map(InstanceInfo::getInstanceId)
                        .collect(Collectors.toSet());
                assertEquals(100, retrieved.size());
                val oldDate = new Date(new Date().getTime() - 120_000L);
                IntStream.rangeClosed(1, 100)
                        .forEach(i -> {
                            val ii = ControllerTestUtils.generateInstanceInfo(appId, spec, i, HEALTHY, oldDate, null);
                            assertTrue(db.updateInstanceState(appId, ii.getInstanceId(), ii));
                        });
                assertEquals(0, db.healthyInstances(appId).size());
                assertEquals(0, db.oldInstances(appId, 0, Integer.MAX_VALUE).size());
                assertEquals(100, db.markStaleInstances(appId));
                assertTrue(db.healthyInstances(appId).isEmpty());
                assertTrue(instances.containsAll(db.oldInstances(appId, 0, Integer.MAX_VALUE)
                                                         .stream()
                                                         .filter(ii -> ii.getState().equals(LOST))
                                                         .map(InstanceInfo::getInstanceId)
                                                         .collect(Collectors.toSet())));
                assertTrue(db.deleteAllInstancesForApp(appId));
                assertTrue(db.healthyInstances(appId).isEmpty());
            }
        }
    }

    @Test
    @SneakyThrows
    void testOldInstances() {
        try (val cluster = new TestingCluster(1)) {
            cluster.start();
            try (val curator = buildCurator(new ZkConfig().setConnectionString(cluster.getConnectString())
                                                    .setNameSpace("DTEST"))) {
                curator.start();
                val db = new ZkApplicationInstanceInfoDB(curator, MAPPER);
                val spec = ControllerTestUtils.appSpec();
                val appId = ControllerUtils.deployableObjectId(spec);
                val instances = IntStream.rangeClosed(1, 100)
                        .mapToObj(i -> {
                            val ii = ControllerTestUtils.generateInstanceInfo(appId, spec, i, InstanceState.STOPPED);
                            assertTrue(db.updateInstanceState(appId, ii.getInstanceId(), ii));
                            return ii.getInstanceId();
                        })
                        .collect(Collectors.toSet());
                val retrieved = db.oldInstances(appId, 0, Integer.MAX_VALUE)
                        .stream()
                        .map(InstanceInfo::getInstanceId)
                        .collect(Collectors.toSet());
                assertEquals(instances.size(), retrieved.size());
                assertTrue(instances.containsAll(retrieved));
            }
        }
    }

}