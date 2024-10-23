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

package com.phonepe.drove.controller.engine;

import com.phonepe.drove.common.CommonTestUtils;
import com.phonepe.drove.controller.ControllerTestUtils;
import com.phonepe.drove.controller.event.DroveEventBus;
import com.phonepe.drove.controller.resourcemgmt.ClusterResourcesDB;
import com.phonepe.drove.controller.statedb.ApplicationInstanceInfoDB;
import com.phonepe.drove.controller.statedb.LocalServiceStateDB;
import com.phonepe.drove.controller.statedb.TaskDB;
import com.phonepe.drove.controller.utils.ControllerUtils;
import com.phonepe.drove.models.info.ExecutorResourceSnapshot;
import com.phonepe.drove.models.info.nodedata.ExecutorNodeData;
import com.phonepe.drove.models.info.resources.PhysicalLayout;
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
        val taskDB = mock(TaskDB.class);
        val iiDB = mock(ApplicationInstanceInfoDB.class);
        val lsDB = mock(LocalServiceStateDB.class);
        val spec = appSpec(1);
        val taskSpec = taskSpec(1);
        val instance = generateInstanceInfo(ControllerUtils.deployableObjectId(spec), spec, 0);
        val taskInstance = generateTaskInfo(taskSpec, 0);
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
            return true;
        }).when(iiDB).updateInstanceState(anyString(), anyString(), any(InstanceInfo.class));
        val droveEventBus = mock(DroveEventBus.class);
        val su = new StateUpdater(cDB, taskDB, iiDB, lsDB, droveEventBus);
        su.updateClusterResources(nodes);
        su.updateClusterResources(List.of());
        CommonTestUtils.waitUntil(() -> counter.get() == 2);
        assertEquals(2, counter.get());
    }

    @Test
    void testRemove() {
        val cDB = mock(ClusterResourcesDB.class);
        val taskDB = mock(TaskDB.class);
        val lsDB = mock(LocalServiceStateDB.class);
        val iiDB = mock(ApplicationInstanceInfoDB.class);

        val spec = appSpec(1);
        val taskSpec = taskSpec(1);
        val instance = generateInstanceInfo(ControllerUtils.deployableObjectId(spec), spec, 0);
        val taskInstance = generateTaskInfo(taskSpec, 0);
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
            return true;
        }).when(iiDB).deleteInstanceState(anyString(), anyString());
        val droveEventBus = mock(DroveEventBus.class);

        val su = new StateUpdater(cDB, taskDB, iiDB, lsDB, droveEventBus);
        su.remove(List.of(executor.getExecutorId()));
        CommonTestUtils.waitUntil(() -> count.get() == 2);
        assertEquals(2, count.get());
    }

    @Test
    void testUpdateSingle() {
        val cDB = mock(ClusterResourcesDB.class);
        val lsDB = mock(LocalServiceStateDB.class);
        val taskDB = mock(TaskDB.class);
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
            return true;
        }).when(iiDB).updateInstanceState(anyString(), anyString(), any(InstanceInfo.class));
        val droveEventBus = mock(DroveEventBus.class);

        val su = new StateUpdater(cDB, taskDB, iiDB, lsDB, droveEventBus);
        su.updateSingle(new ExecutorResourceSnapshot(EXECUTOR_ID,
                                                     new AvailableCPU(Map.of(), Map.of()),
                                                     new AvailableMemory(Map.of(), Map.of()),
                                                     new PhysicalLayout(Map.of(), Map.of())), instance);
        CommonTestUtils.waitUntil(() -> counter.get() == 2);
        assertEquals(2, counter.get());
    }
}