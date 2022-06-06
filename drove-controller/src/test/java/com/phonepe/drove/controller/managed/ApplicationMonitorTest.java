package com.phonepe.drove.controller.managed;

import com.phonepe.drove.controller.ControllerTestUtils;
import com.phonepe.drove.controller.engine.ApplicationEngine;
import com.phonepe.drove.controller.engine.CommandValidator;
import com.phonepe.drove.controller.statedb.ApplicationStateDB;
import com.phonepe.drove.controller.statedb.ClusterStateDB;
import com.phonepe.drove.controller.statedb.InstanceInfoDB;
import com.phonepe.drove.models.application.ApplicationInfo;
import com.phonepe.drove.models.common.ClusterState;
import com.phonepe.drove.models.common.ClusterStateData;
import com.phonepe.drove.models.operation.ApplicationOperation;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.phonepe.drove.controller.utils.ControllerUtils.appId;
import static com.phonepe.drove.models.application.ApplicationState.*;
import static com.phonepe.drove.models.instance.InstanceState.HEALTHY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 *
 */
class ApplicationMonitorTest {

    @Test
    @SneakyThrows
    void testAllRunningPass() {
        val stateDB = mock(ApplicationStateDB.class);
        val instanceInfoDB = mock(InstanceInfoDB.class);
        val clusterStateDB = mock(ClusterStateDB.class);
        val appEngine = mock(ApplicationEngine.class);

        val monitor = new ApplicationMonitor(stateDB, instanceInfoDB, clusterStateDB, appEngine);

        val specs = IntStream.rangeClosed(1, 100)
                .mapToObj(ControllerTestUtils::appSpec)
                .map(spec -> new ApplicationInfo(appId(spec), spec, 10, new Date(), new Date()))
                .toList();
        when(stateDB.applications(0, Integer.MAX_VALUE)).thenReturn(specs);
        when(appEngine.applicationState(anyString())).thenReturn(Optional.of(RUNNING));
        when(instanceInfoDB.instanceCount(anyString(), eq(HEALTHY))).thenReturn(10L);
        when(clusterStateDB.currentState()).thenReturn(Optional.of(new ClusterStateData(ClusterState.NORMAL, new Date(0))));
        val ids = new ArrayList<String>();
        when(appEngine.handleOperation(any(ApplicationOperation.class)))
                .thenAnswer((Answer<CommandValidator.ValidationResult>) invocationOnMock -> {
                    ids.add(invocationOnMock.getArgument(0, String.class));
                    return CommandValidator.ValidationResult.success();
                });
        monitor.checkAllApps(new Date());

        assertTrue(ids.isEmpty());
    }

    @Test
    @SneakyThrows
    void testAllMonitoringPass() {
        val stateDB = mock(ApplicationStateDB.class);
        val instanceInfoDB = mock(InstanceInfoDB.class);
        val clusterStateDB = mock(ClusterStateDB.class);
        val appEngine = mock(ApplicationEngine.class);

        val monitor = new ApplicationMonitor(stateDB, instanceInfoDB, clusterStateDB, appEngine);

        val specs = IntStream.rangeClosed(1, 100)
                .mapToObj(ControllerTestUtils::appSpec)
                .map(spec -> new ApplicationInfo(appId(spec), spec, 0, new Date(), new Date()))
                .toList();
        when(stateDB.applications(0, Integer.MAX_VALUE)).thenReturn(specs);
        when(appEngine.applicationState(anyString())).thenReturn(Optional.of(MONITORING));
        when(instanceInfoDB.instanceCount(anyString(), eq(HEALTHY))).thenReturn(0L);
        when(clusterStateDB.currentState()).thenReturn(Optional.of(new ClusterStateData(ClusterState.NORMAL, new Date(0))));
        val ids = new ArrayList<String>();
        when(appEngine.handleOperation(any(ApplicationOperation.class)))
                .thenAnswer((Answer<CommandValidator.ValidationResult>) invocationOnMock -> {
                    ids.add(invocationOnMock.getArgument(0, String.class));
                    return CommandValidator.ValidationResult.success();
                });
        monitor.checkAllApps(new Date());

        assertTrue(ids.isEmpty());
    }

    @Test
    @SneakyThrows
    void testAllScale() {
        val stateDB = mock(ApplicationStateDB.class);
        val instanceInfoDB = mock(InstanceInfoDB.class);
        val clusterStateDB = mock(ClusterStateDB.class);
        val appEngine = mock(ApplicationEngine.class);

        val monitor = new ApplicationMonitor(stateDB, instanceInfoDB, clusterStateDB, appEngine);

        val specs = IntStream.rangeClosed(1, 100)
                .mapToObj(ControllerTestUtils::appSpec)
                .map(spec -> new ApplicationInfo(appId(spec), spec, 10, new Date(), new Date()))
                .toList();
        when(stateDB.applications(0, Integer.MAX_VALUE)).thenReturn(specs);
        when(appEngine.applicationState(anyString())).thenReturn(Optional.of(RUNNING));
        when(instanceInfoDB.instanceCount(anyString(), eq(HEALTHY))).thenReturn(9L);
        when(clusterStateDB.currentState()).thenReturn(Optional.of(new ClusterStateData(ClusterState.NORMAL, new Date(0))));
        val ids = new HashSet<String>();
        when(appEngine.handleOperation(any(ApplicationOperation.class)))
                .thenAnswer((Answer<CommandValidator.ValidationResult>) invocationOnMock -> {
                    ids.add(appId(invocationOnMock.getArgument(0, ApplicationOperation.class)));
                    return CommandValidator.ValidationResult.success();
                });
        monitor.checkAllApps(new Date());

        assertEquals(specs.stream().map(ApplicationInfo::getAppId).collect(Collectors.toSet()), ids);


    }

    @Test
    @SneakyThrows
    void testAllIgnore() {
        val stateDB = mock(ApplicationStateDB.class);
        val instanceInfoDB = mock(InstanceInfoDB.class);
        val clusterStateDB = mock(ClusterStateDB.class);
        val appEngine = mock(ApplicationEngine.class);

        val monitor = new ApplicationMonitor(stateDB, instanceInfoDB, clusterStateDB, appEngine);

        val specs = IntStream.rangeClosed(1, 100)
                .mapToObj(ControllerTestUtils::appSpec)
                .map(spec -> new ApplicationInfo(appId(spec), spec, 10, new Date(), new Date()))
                .toList();
        when(stateDB.applications(0, Integer.MAX_VALUE)).thenReturn(specs);
        when(appEngine.applicationState(anyString())).thenReturn(Optional.of(DESTROY_REQUESTED));
        when(instanceInfoDB.instanceCount(anyString(), eq(HEALTHY))).thenReturn(9L);
        when(clusterStateDB.currentState()).thenReturn(Optional.of(new ClusterStateData(ClusterState.NORMAL, new Date(0))));
        val ids = new HashSet<String>();
        when(appEngine.handleOperation(any(ApplicationOperation.class)))
                .thenAnswer((Answer<CommandValidator.ValidationResult>) invocationOnMock -> {
                    ids.add(appId(invocationOnMock.getArgument(0, ApplicationOperation.class)));
                    return CommandValidator.ValidationResult.success();
                });
        monitor.checkAllApps(new Date());

        assertTrue(ids.isEmpty()); //No scale even if instance count mismatch


    }
}