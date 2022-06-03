package com.phonepe.drove.controller.resources;

import com.phonepe.drove.controller.engine.ApplicationEngine;
import com.phonepe.drove.controller.engine.ControllerCommunicator;
import com.phonepe.drove.controller.resourcemgmt.ClusterResourcesDB;
import com.phonepe.drove.controller.statedb.ApplicationStateDB;
import com.phonepe.drove.controller.statedb.InstanceInfoDB;
import com.phonepe.drove.models.api.ApiErrorCode;
import com.phonepe.drove.models.application.ApplicationInfo;
import com.phonepe.drove.models.instance.InstanceState;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Date;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import static com.phonepe.drove.controller.ControllerTestUtils.appSpec;
import static com.phonepe.drove.controller.ControllerTestUtils.generateInstanceInfo;
import static com.phonepe.drove.controller.utils.ControllerUtils.appId;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 *
 */
class ResponseEngineTest {

    @Test
    void testApplications() {
        val engine = mock(ApplicationEngine.class);
        val applicationStateDB = mock(ApplicationStateDB.class);
        val instanceInfoDB = mock(InstanceInfoDB.class);
        val clusterResourcesDB = mock(ClusterResourcesDB.class);
        val communicator = mock(ControllerCommunicator.class);

        val re = new ResponseEngine(engine, applicationStateDB, instanceInfoDB, clusterResourcesDB, communicator);
        val rng = new SecureRandom();
        when(applicationStateDB.applications(0, Integer.MAX_VALUE))
                .thenReturn(IntStream.range(0, 100)
                                    .mapToObj(i -> {
                                        val spec = appSpec("TEST_APP_" + i, 1);
                                        val appId = appId(spec);
                                        return new ApplicationInfo(appId, spec, rng.nextInt(100), new Date(), new Date());
                                    })
                                    .toList());

        val res = re.applications(0, Integer.MAX_VALUE);
        assertEquals(ApiErrorCode.SUCCESS, res.getStatus());
        assertEquals("success", res.getMessage());
        assertEquals(100, res.getData().size());
    }

    @Test
    void testApplication() {
        val engine = mock(ApplicationEngine.class);
        val applicationStateDB = mock(ApplicationStateDB.class);
        val instanceInfoDB = mock(InstanceInfoDB.class);
        val clusterResourcesDB = mock(ClusterResourcesDB.class);
        val communicator = mock(ControllerCommunicator.class);

        val re = new ResponseEngine(engine, applicationStateDB, instanceInfoDB, clusterResourcesDB, communicator);

        val spec = appSpec();
        val appId = appId(spec);

        val applicationInfo = new ApplicationInfo(appId, spec, 5, new Date(), new Date());
        when(applicationStateDB.application(appId))
                .thenReturn(Optional.of(applicationInfo));

        val res = re.application(appId);
        assertEquals(ApiErrorCode.SUCCESS, res.getStatus());
        assertEquals("success", res.getMessage());
        assertNotNull(res.getData());
    }


    @Test
    void testApplicationInstances() {
        val engine = mock(ApplicationEngine.class);
        val applicationStateDB = mock(ApplicationStateDB.class);
        val instanceInfoDB = mock(InstanceInfoDB.class);
        val clusterResourcesDB = mock(ClusterResourcesDB.class);
        val communicator = mock(ControllerCommunicator.class);

        val re = new ResponseEngine(engine, applicationStateDB, instanceInfoDB, clusterResourcesDB, communicator);
        val spec = appSpec();
        val appId = appId(spec);
        val instances = IntStream.rangeClosed(1, 100)
                        .mapToObj(i -> generateInstanceInfo(appId, spec, i, InstanceState.STARTING))
                                .toList();

        when(instanceInfoDB.activeInstances(appId, 0, Integer.MAX_VALUE)).thenReturn(instances);

        val res = re.applicationInstances(appId, Set.of());
        assertEquals(ApiErrorCode.SUCCESS, res.getStatus());
        assertEquals("success", res.getMessage());
        assertEquals(100, res.getData().size());
        assertEquals(instances, res.getData());
        assertTrue(re.applicationInstances(appId, Set.of(InstanceState.HEALTHY)).getData().isEmpty());
        assertEquals(instances, re.applicationInstances(appId, Set.of(InstanceState.STARTING)).getData());
    }

    @Test
    void testInstanceDetails() {
        val engine = mock(ApplicationEngine.class);
        val applicationStateDB = mock(ApplicationStateDB.class);
        val instanceInfoDB = mock(InstanceInfoDB.class);
        val clusterResourcesDB = mock(ClusterResourcesDB.class);
        val communicator = mock(ControllerCommunicator.class);

        val re = new ResponseEngine(engine, applicationStateDB, instanceInfoDB, clusterResourcesDB, communicator);
        val spec = appSpec();
        val appId = appId(spec);
        val instance = generateInstanceInfo(appId, spec, 1, InstanceState.STARTING);

        final var instanceId = instance.getInstanceId();
        when(instanceInfoDB.instance(appId, instanceId)).thenReturn(Optional.of(instance));

        {
            val res = re.instanceDetails(appId, instanceId);
            assertEquals(ApiErrorCode.SUCCESS, res.getStatus());
            assertEquals("success", res.getMessage());
            assertEquals(instance, res.getData());
        }
        {
            val res = re.instanceDetails(appId, "blah");
            assertEquals(ApiErrorCode.FAILED, res.getStatus());
            assertEquals("No such instance", res.getMessage());
            assertNull(res.getData());
        }
    }
}