package com.phonepe.drove.controller.resources;

import com.phonepe.drove.auth.model.*;
import com.phonepe.drove.controller.statedb.InstanceInfoDB;
import com.phonepe.drove.models.api.ApiErrorCode;
import com.phonepe.drove.models.info.nodedata.NodeType;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import static com.phonepe.drove.controller.ControllerTestUtils.appSpec;
import static com.phonepe.drove.controller.ControllerTestUtils.generateInstanceInfo;
import static com.phonepe.drove.controller.utils.ControllerUtils.appId;
import static com.phonepe.drove.models.instance.InstanceState.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 *
 */
class AppSupportTest {

    @Test
    void testSuccess() {
        val spec = appSpec();
        val appId = appId(spec);
        val instanceInfoDB = mock(InstanceInfoDB.class);
        val callingInstance = generateInstanceInfo(appId, spec, 1);
        when(instanceInfoDB.instance(eq(appId), eq(callingInstance.getInstanceId())))
                .thenReturn(Optional.of(callingInstance));
        val instances = IntStream.rangeClosed(1, 5)
                .mapToObj(i -> generateInstanceInfo(appId, spec, i))
                .toList();
        when(instanceInfoDB.instances(Set.of(appId), RUNNING_STATES))
                .thenReturn(Map.of(appId, instances));

        val as = new AppSupport(instanceInfoDB);
        {
            val r = as.siblingInstances(
                    new DroveApplicationInstance("test",
                                                 new DroveApplicationInstanceInfo(appId,
                                                                                  callingInstance.getInstanceId(),
                                                                                  callingInstance.getExecutorId())),
                    Set.of());
            assertEquals(ApiErrorCode.SUCCESS, r.getStatus());
            assertEquals(instances, r.getData());
        }
        {
            val r = as.siblingInstances(
                    new DroveApplicationInstance("test",
                                                 new DroveApplicationInstanceInfo(appId,
                                                                                  callingInstance.getInstanceId(),
                                                                                  callingInstance.getExecutorId())),
                    null);
            assertEquals(ApiErrorCode.SUCCESS, r.getStatus());
            assertEquals(instances, r.getData());
        }
    }

    @Test
    void testSuccessSpecificState() {
        val spec = appSpec();
        val appId = appId(spec);
        val instanceInfoDB = mock(InstanceInfoDB.class);
        val callingInstance = generateInstanceInfo(appId, spec, 1);
        when(instanceInfoDB.instance(eq(appId), eq(callingInstance.getInstanceId())))
                .thenReturn(Optional.of(callingInstance));
        val instances = IntStream.rangeClosed(1, 5)
                .mapToObj(i -> generateInstanceInfo(appId, spec, i, HEALTHY))
                .toList();
        when(instanceInfoDB.instances(Set.of(appId), Set.of(HEALTHY)))
                .thenReturn(Map.of(appId, instances));

        val as = new AppSupport(instanceInfoDB);
        val r = as.siblingInstances(
                new DroveApplicationInstance("test",
                                             new DroveApplicationInstanceInfo(appId,
                                                                              callingInstance.getInstanceId(),
                                                                              callingInstance.getExecutorId())),
                Set.of(HEALTHY));
        assertEquals(ApiErrorCode.SUCCESS, r.getStatus());
        assertEquals(instances, r.getData());
    }

    @Test
    void testFailNoInfo() {
        val spec = appSpec();
        val appId = appId(spec);
        val instanceInfoDB = mock(InstanceInfoDB.class);
        val callingInstance = generateInstanceInfo(appId, spec, 1);
        when(instanceInfoDB.instance(eq(appId), eq(callingInstance.getInstanceId())))
                .thenReturn(Optional.of(callingInstance));
        val instances = IntStream.rangeClosed(1, 5)
                .mapToObj(i -> generateInstanceInfo(appId, spec, i))
                .toList();
        when(instanceInfoDB.instances(Set.of(appId), ACTIVE_STATES))
                .thenReturn(Map.of(appId, instances));

        val as = new AppSupport(instanceInfoDB);
        {
            val r = as.siblingInstances(new DroveClusterNode("test-node", NodeType.EXECUTOR),
                                        Set.of());
            assertEquals(ApiErrorCode.FAILED, r.getStatus());
            assertEquals("This api is applicable for calls by app instances from inside the cluster only",
                         r.getMessage());
        }
        {
            val r = as.siblingInstances(
                    new DroveExternalUser("test-user", DroveUserRole.EXTERNAL_READ_ONLY, null),
                    Set.of());
            assertEquals(ApiErrorCode.FAILED, r.getStatus());
            assertEquals("This api is applicable for calls by app instances from inside the cluster only",
                         r.getMessage());
        }
    }

    @Test
    void testNoAccess() {
        val spec = appSpec();
        val appId = appId(spec);
        val instanceInfoDB = mock(InstanceInfoDB.class);
        val callingInstance = generateInstanceInfo(appId, spec, 1);
        when(instanceInfoDB.instance(eq(appId), eq(callingInstance.getInstanceId())))
                .thenReturn(Optional.of(callingInstance));
        val instances = IntStream.rangeClosed(1, 5)
                .mapToObj(i -> generateInstanceInfo(appId, spec, i))
                .toList();
        when(instanceInfoDB.instances(Set.of(appId), ACTIVE_STATES))
                .thenReturn(Map.of(appId, instances));

        val as = new AppSupport(instanceInfoDB);
        {
            val r = as.siblingInstances(
                    new DroveApplicationInstance("test",
                                                 new DroveApplicationInstanceInfo("wrong-app",
                                                                                  callingInstance.getInstanceId(),
                                                                                  callingInstance.getExecutorId())),
                    Set.of());
            assertEquals(ApiErrorCode.FAILED, r.getStatus());
            assertEquals("Please send valid token for you app instance. " +
                                 "The token value is available in the DROVE_APP_INSTANCE_AUTH_TOKEN environment " +
                                 "variable",
                         r.getMessage());
        }
        {
            val r = as.siblingInstances(
                    new DroveApplicationInstance("test",
                                                 new DroveApplicationInstanceInfo(appId,
                                                                                  "wrong-instance",
                                                                                  callingInstance.getExecutorId())),
                    Set.of());
            assertEquals(ApiErrorCode.FAILED, r.getStatus());
            assertEquals("Please send valid token for you app instance. " +
                                 "The token value is available in the DROVE_APP_INSTANCE_AUTH_TOKEN environment " +
                                 "variable",
                         r.getMessage());
        }
        {
            val r = as.siblingInstances(
                    new DroveApplicationInstance("test",
                                                 new DroveApplicationInstanceInfo(appId,
                                                                                  callingInstance.getInstanceId(),
                                                                                  "wrong-executor")),
                    Set.of());
            assertEquals(ApiErrorCode.FAILED, r.getStatus());
            assertEquals("Please send valid token for you app instance. " +
                                 "The token value is available in the DROVE_APP_INSTANCE_AUTH_TOKEN environment " +
                                 "variable",
                         r.getMessage());
        }

    }

}