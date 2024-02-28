package com.phonepe.drove.controller.resources;

import com.phonepe.drove.auth.model.*;
import com.phonepe.drove.controller.statedb.ApplicationInstanceInfoDB;
import com.phonepe.drove.controller.statedb.ApplicationStateDB;
import com.phonepe.drove.controller.utils.ControllerUtils;
import com.phonepe.drove.models.api.ApiErrorCode;
import com.phonepe.drove.models.application.ApplicationInfo;
import com.phonepe.drove.models.info.nodedata.NodeType;
import com.phonepe.drove.models.instance.InstanceInfo;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.phonepe.drove.controller.ControllerTestUtils.appSpec;
import static com.phonepe.drove.controller.ControllerTestUtils.generateInstanceInfo;
import static com.phonepe.drove.models.instance.InstanceState.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
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
        val appId = ControllerUtils.deployableObjectId(spec);
        val instanceInfoDB = mock(ApplicationInstanceInfoDB.class);
        val callingInstance = generateInstanceInfo(appId, spec, 1);
        when(instanceInfoDB.instance(appId, callingInstance.getInstanceId()))
                .thenReturn(Optional.of(callingInstance));
        val instances = IntStream.rangeClosed(1, 5)
                .mapToObj(i -> generateInstanceInfo(appId, spec, i))
                .toList();
        when(instanceInfoDB.instances(Set.of(appId), RUNNING_STATES))
                .thenReturn(Map.of(appId, instances));
        val appDB = mock(ApplicationStateDB.class);
        val aInfo = new ApplicationInfo(appId, spec, 5, new Date(), new Date());
        when(appDB.application(appId)).thenReturn(Optional.of(aInfo));
        when(appDB.applications(0, Integer.MAX_VALUE)).thenReturn(List.of(aInfo));
        val as = new AppSupport(appDB, instanceInfoDB);
        {
            val r = as.siblingInstances(
                    new DroveApplicationInstance("test",
                                                 new DroveApplicationInstanceInfo(appId,
                                                                                  callingInstance.getInstanceId(),
                                                                                  callingInstance.getExecutorId())),
                    Set.of(),
                    false);
            assertEquals(ApiErrorCode.SUCCESS, r.getStatus());
            assertEquals(instances, r.getData());
        }
        {
            val r = as.siblingInstances(
                    new DroveApplicationInstance("test",
                                                 new DroveApplicationInstanceInfo(appId,
                                                                                  callingInstance.getInstanceId(),
                                                                                  callingInstance.getExecutorId())),
                    null,
                    false);
            assertEquals(ApiErrorCode.SUCCESS, r.getStatus());
            assertEquals(instances, r.getData());
        }
    }

    @Test
    void testSuccessSpecificState() {
        val spec = appSpec();
        val appId = ControllerUtils.deployableObjectId(spec);
        val instanceInfoDB = mock(ApplicationInstanceInfoDB.class);
        val callingInstance = generateInstanceInfo(appId, spec, 1);
        when(instanceInfoDB.instance(appId, callingInstance.getInstanceId()))
                .thenReturn(Optional.of(callingInstance));
        val instances = IntStream.rangeClosed(1, 5)
                .mapToObj(i -> generateInstanceInfo(appId, spec, i, HEALTHY))
                .toList();
        when(instanceInfoDB.instances(Set.of(appId), Set.of(HEALTHY)))
                .thenReturn(Map.of(appId, instances));
        val appDB = mock(ApplicationStateDB.class);
        val aInfo = new ApplicationInfo(appId, spec, 5, new Date(), new Date());
        when(appDB.application(appId)).thenReturn(Optional.of(aInfo));
        when(appDB.applications(0, Integer.MAX_VALUE)).thenReturn(List.of(aInfo));
        val as = new AppSupport(appDB, instanceInfoDB);
        val r = as.siblingInstances(
                new DroveApplicationInstance("test",
                                             new DroveApplicationInstanceInfo(appId,
                                                                              callingInstance.getInstanceId(),
                                                                              callingInstance.getExecutorId())),
                Set.of(HEALTHY),
                false);
        assertEquals(ApiErrorCode.SUCCESS, r.getStatus());
        assertEquals(instances, r.getData());
    }

    @Test
    void testFailNoInfo() {
        val spec = appSpec();
        val appId = ControllerUtils.deployableObjectId(spec);
        val instanceInfoDB = mock(ApplicationInstanceInfoDB.class);
        val callingInstance = generateInstanceInfo(appId, spec, 1);
        when(instanceInfoDB.instance(appId, callingInstance.getInstanceId()))
                .thenReturn(Optional.of(callingInstance));
        val instances = IntStream.rangeClosed(1, 5)
                .mapToObj(i -> generateInstanceInfo(appId, spec, i))
                .toList();
        when(instanceInfoDB.instances(Set.of(appId), ACTIVE_STATES))
                .thenReturn(Map.of(appId, instances));
        val appDB = mock(ApplicationStateDB.class);
        val aInfo = new ApplicationInfo(appId, spec, 5, new Date(), new Date());
        when(appDB.application(appId)).thenReturn(Optional.of(aInfo));
        when(appDB.applications(0, Integer.MAX_VALUE)).thenReturn(List.of(aInfo));
        val as = new AppSupport(appDB, instanceInfoDB);
        {
            val r = as.siblingInstances(new DroveClusterNode("test-node", NodeType.EXECUTOR),
                                        Set.of(),
                                        false);
            assertEquals(ApiErrorCode.FAILED, r.getStatus());
            assertEquals("This api is applicable for calls by app instances from inside the cluster only",
                         r.getMessage());
        }
        {
            val r = as.siblingInstances(
                    new DroveExternalUser("test-user", DroveUserRole.EXTERNAL_READ_ONLY, null),
                    Set.of(),
                    false);
            assertEquals(ApiErrorCode.FAILED, r.getStatus());
            assertEquals("This api is applicable for calls by app instances from inside the cluster only",
                         r.getMessage());
        }
    }

    @Test
    void testNoAccess() {
        val spec = appSpec();
        val appId = ControllerUtils.deployableObjectId(spec);
        val instanceInfoDB = mock(ApplicationInstanceInfoDB.class);
        val callingInstance = generateInstanceInfo(appId, spec, 1);
        when(instanceInfoDB.instance(appId, callingInstance.getInstanceId()))
                .thenReturn(Optional.of(callingInstance));
        val instances = IntStream.rangeClosed(1, 5)
                .mapToObj(i -> generateInstanceInfo(appId, spec, i))
                .toList();
        when(instanceInfoDB.instances(Set.of(appId), ACTIVE_STATES))
                .thenReturn(Map.of(appId, instances));
        val appDB = mock(ApplicationStateDB.class);
        val aInfo = new ApplicationInfo(appId, spec, 5, new Date(), new Date());
        when(appDB.application(appId)).thenReturn(Optional.of(aInfo));
        when(appDB.applications(0, Integer.MAX_VALUE)).thenReturn(List.of(aInfo));
        val as = new AppSupport(appDB, instanceInfoDB);
        {
            val r = as.siblingInstances(
                    new DroveApplicationInstance("test",
                                                 new DroveApplicationInstanceInfo("wrong-app",
                                                                                  callingInstance.getInstanceId(),
                                                                                  callingInstance.getExecutorId())),
                    Set.of(),
                    false);
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
                    Set.of(),
                    false);
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
                    Set.of(),
                    false);
            assertEquals(ApiErrorCode.FAILED, r.getStatus());
            assertEquals("Please send valid token for you app instance. " +
                                 "The token value is available in the DROVE_APP_INSTANCE_AUTH_TOKEN environment " +
                                 "variable",
                         r.getMessage());
        }

    }

    @Test
    @SuppressWarnings("unchecked")
    void testSuccessMulti() {
        val appIds = new ArrayList<String>();
        val specs = new ArrayList<ApplicationInfo>();
        val infos = new HashMap<String, List<InstanceInfo>>();
        IntStream.rangeClosed(1, 10)
                .forEach(appIdx -> {
                    val spec = appSpec(appIdx);
                    val appId = ControllerUtils.deployableObjectId(spec);
                    val instances = IntStream.rangeClosed(1, 5)
                            .mapToObj(i -> generateInstanceInfo(appId, spec, i))
                            .toList();
                    appIds.add(appId);
                    specs.add(new ApplicationInfo(appId, spec, 5, new Date(), new Date()));
                    infos.put(appId, instances);
                });

        IntStream.rangeClosed(1, 10)
                .forEach(appIdx -> {
                    val spec = appSpec("OTHER_APP", appIdx);
                    val appId = ControllerUtils.deployableObjectId(spec);
                    val instances = IntStream.rangeClosed(1, 5)
                            .mapToObj(i -> generateInstanceInfo(appId, spec, i))
                            .toList();
                    appIds.add(appId);
                    specs.add(new ApplicationInfo(appId, spec, 5, new Date(), new Date()));
                    infos.put(appId, instances);
                });

        val instanceInfoDB = mock(ApplicationInstanceInfoDB.class);
        val callingInstance = generateInstanceInfo(appIds.get(0), specs.get(0).getSpec(), 1);
        when(instanceInfoDB.instance(callingInstance.getAppId(), callingInstance.getInstanceId()))
                .thenReturn(Optional.of(callingInstance));

        when(instanceInfoDB.instances(ArgumentMatchers.<Set<String>>any(), eq(RUNNING_STATES)))
                .thenAnswer(invocationOnMock -> {
                    val appIdSet = (Set<String>)invocationOnMock.getArgument(0);
                    return appIdSet.stream()
                            .flatMap(appId -> infos.getOrDefault(appId, List.of()).stream())
                            .collect(Collectors.groupingBy(InstanceInfo::getAppId));
                });
        val appDB = mock(ApplicationStateDB.class);
        when(appDB.application(anyString())).thenAnswer(invocationOnMock -> {
            val appId = invocationOnMock.getArgument(0, String.class);
            return specs.stream().filter(appInfo -> appInfo.getAppId().equals(appId)).findFirst();
        });
        when(appDB.applications(0, Integer.MAX_VALUE)).thenReturn(specs);
        val as = new AppSupport(appDB, instanceInfoDB);
        {
            val r = as.siblingInstances(
                    new DroveApplicationInstance("test",
                                                 new DroveApplicationInstanceInfo(callingInstance.getAppId(),
                                                                                  callingInstance.getInstanceId(),
                                                                                  callingInstance.getExecutorId())),
                    Set.of(),
                    true);
            assertEquals(ApiErrorCode.SUCCESS, r.getStatus());
            assertEquals(50, r.getData().size());
        }
        {
            val r = as.siblingInstances(
                    new DroveApplicationInstance("test",
                                                 new DroveApplicationInstanceInfo(callingInstance.getAppId(),
                                                                                  callingInstance.getInstanceId(),
                                                                                  callingInstance.getExecutorId())),
                    Set.of(),
                    false);
            assertEquals(ApiErrorCode.SUCCESS, r.getStatus());
            assertEquals(5, r.getData().size());
        }
    }
}