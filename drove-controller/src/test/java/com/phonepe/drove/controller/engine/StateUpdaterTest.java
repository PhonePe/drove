package com.phonepe.drove.controller.engine;

import com.phonepe.drove.controller.ControllerTestUtils;
import com.phonepe.drove.controller.resourcemgmt.ClusterResourcesDB;
import com.phonepe.drove.controller.statedb.ApplicationInstanceInfoDB;
import com.phonepe.drove.controller.utils.ControllerUtils;
import com.phonepe.drove.models.info.ExecutorResourceSnapshot;
import com.phonepe.drove.models.info.nodedata.ExecutorNodeData;
import com.phonepe.drove.models.info.resources.available.AvailableCPU;
import com.phonepe.drove.models.info.resources.available.AvailableMemory;
import com.phonepe.drove.models.instance.InstanceInfo;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static com.phonepe.drove.controller.ControllerTestUtils.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 *
 */
class StateUpdaterTest {

    @Test
    @SuppressWarnings("unchecked")
    void testUpdater() {
        val cDB = mock(ClusterResourcesDB.class);
        val iiDB = mock(ApplicationInstanceInfoDB.class);

        val spec = appSpec(1);
        val taskSpec = taskSpec(1);
        val instance = generateInstanceInfo(ControllerUtils.deployableObjectId(spec), spec, 0);
        val taskInstance = generateTaskInstanceInfo(taskSpec, 0);
        val executor = ControllerTestUtils.executorHost(8080, List.of(instance), List.of(taskInstance));
        val nodes = List.of(executor.getNodeData());
        val counter = new AtomicInteger();
        doAnswer(invocationOnMock -> {
            val arg = (List<ExecutorNodeData>) invocationOnMock.getArgument(0, List.class);
            assertEquals(arg, nodes);
            counter.addAndGet(arg.size());
            return null;
        }).when(cDB).update(anyList());

        doAnswer(invocationOnMock -> {
            counter.incrementAndGet();
            return null;
        }).when(iiDB).updateInstanceState(anyString(), anyString(), any(InstanceInfo.class));

        val su = new StateUpdater(cDB, iiDB);
        su.updateClusterResources(nodes);
        su.updateClusterResources(List.of());
        assertEquals(2, counter.get());
    }

    @Test
    void testRemove() {
        val cDB = mock(ClusterResourcesDB.class);
        val iiDB = mock(ApplicationInstanceInfoDB.class);

        val spec = appSpec(1);
        val taskSpec = taskSpec(1);
        val instance = generateInstanceInfo(ControllerUtils.deployableObjectId(spec), spec, 0);
        val taskInstance = generateTaskInstanceInfo(taskSpec, 0);
        val executor = ControllerTestUtils.executorHost(8080, List.of(instance), List.of(taskInstance));

        doReturn(Optional.of(executor))
                .when(cDB).currentSnapshot(executor.getExecutorId());
        val count = new AtomicInteger();
        doAnswer(invocationOnMock -> {
            val argument = invocationOnMock.getArgument(0, List.class);
            assertEquals(List.of(executor.getExecutorId()), argument);
            count.addAndGet(argument.size());
            return null;
        }).when(cDB).remove(anyList());
        doAnswer(invocationOnMock -> {
            count.incrementAndGet();
            return null;
        }).when(iiDB).deleteInstanceState(anyString(), anyString());

        val su = new StateUpdater(cDB, iiDB);
        su.remove(List.of(executor.getExecutorId()));
        assertEquals(2, count.get());
    }

    @Test
    void testUpdateSingle() {
        val cDB = mock(ClusterResourcesDB.class);
        val iiDB = mock(ApplicationInstanceInfoDB.class);

        val spec = appSpec(1);
        val appId = ControllerUtils.deployableObjectId(spec);
        val instance = generateInstanceInfo(appId, spec, 0);

        val counter = new AtomicInteger();
        doAnswer(invocationOnMock -> {
            counter.incrementAndGet();
            return null;
        }).when(cDB).update(any(ExecutorResourceSnapshot.class));

        doAnswer(invocationOnMock -> {
            counter.incrementAndGet();
            assertEquals(instance.getInstanceId(), invocationOnMock.getArgument(1, String.class));
            return null;
        }).when(iiDB).updateInstanceState(anyString(), anyString(), any(InstanceInfo.class));

        val su = new StateUpdater(cDB, iiDB);
        su.updateSingle(new ExecutorResourceSnapshot(EXECUTOR_ID,
                                                     new AvailableCPU(Map.of(), Map.of()),
                                                     new AvailableMemory(Map.of(), Map.of())), instance);
        assertEquals(2, counter.get());
    }
}