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

import com.phonepe.drove.controller.ControllerTestBase;
import com.phonepe.drove.controller.ControllerTestUtils;
import com.phonepe.drove.controller.managed.LeadershipEnsurer;
import com.phonepe.drove.controller.testsupport.InMemoryTaskDB;
import com.phonepe.drove.models.taskinstance.TaskInfo;
import com.phonepe.drove.models.taskinstance.TaskResult;
import com.phonepe.drove.models.taskinstance.TaskState;
import io.appform.signals.signals.ConsumingSyncSignal;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 *
 */
class CachingProxyTaskDBTest extends ControllerTestBase {

    @Test
    void testDBBase() {
        val root = new InMemoryTaskDB();
        val le = mock(LeadershipEnsurer.class);
        when(le.onLeadershipStateChanged()).thenReturn(new ConsumingSyncSignal<>());
        val tdb = new CachingProxyTaskDB(root, le);
        val sourceAppIds = new HashSet<String>();
        val genData = IntStream.rangeClosed(1, 10)
                .mapToObj(i -> "TS_" + i)
                .peek(sourceAppIds::add)
                .flatMap(appName -> IntStream.rangeClosed(1, 100).mapToObj(j -> {
                    val ts = ControllerTestUtils.taskSpec(appName, "TID-" + j);
                    return ControllerTestUtils.generateTaskInfo(ts, j);
                }))
                .peek(taskInfo -> tdb.updateTask(taskInfo.getSourceAppName(), taskInfo.getTaskId(), taskInfo))
                .collect(Collectors.groupingBy(TaskInfo::getSourceAppName));
        val r = tdb.tasks(sourceAppIds, EnumSet.allOf(TaskState.class), false);
        genData.forEach((appName, tasks) -> {
            final var expected = new TreeSet<TaskInfo>(Comparator.comparing(TaskInfo::getTaskId));
            expected.addAll(tasks);
            assertTrue(expected.containsAll(r.get(appName)));
            tasks.forEach(taskInfo -> {
                assertEquals(taskInfo, tdb.task(taskInfo.getSourceAppName(), taskInfo.getTaskId()).orElse(null));
                assertTrue(tdb.deleteTask(taskInfo.getSourceAppName(), taskInfo.getTaskId()));
                assertTrue(tdb.task(taskInfo.getSourceAppName(), taskInfo.getTaskId()).isEmpty());
            });
        });
    }

    @Test
    void testPurge() {
        val root = new InMemoryTaskDB();
        val le = mock(LeadershipEnsurer.class);
        val resetSignal = new ConsumingSyncSignal<Boolean>();
        when(le.onLeadershipStateChanged()).thenReturn(resetSignal);
        val tdb = new CachingProxyTaskDB(root, le);
        val taskInfo = ControllerTestUtils.generateTaskInfo(ControllerTestUtils.taskSpec(), 1);
        val sourceAppName = taskInfo.getSourceAppName();
        val taskId = taskInfo.getTaskId();
        tdb.updateTask(sourceAppName, taskId, taskInfo);
        assertEquals(taskInfo, tdb.task(sourceAppName, taskId).orElse(null));
        //Delete from root
        root.deleteTask(sourceAppName, taskId);
        //Still available as it is cached
        assertEquals(taskInfo, tdb.task(sourceAppName, taskId).orElse(null));

        //Purge now
        resetSignal.dispatch(true);
        assertTrue(tdb.task(sourceAppName, taskId).isEmpty());
    }

    @Test
    void testStaleUpdate() {
        val root = new InMemoryTaskDB();
        val le = mock(LeadershipEnsurer.class);
        when(le.onLeadershipStateChanged()).thenReturn(new ConsumingSyncSignal<Boolean>());
        val tdb = new CachingProxyTaskDB(root, le);
        val taskInfo = ControllerTestUtils.generateTaskInfo(ControllerTestUtils.taskSpec(), 1);
        val sourceAppName = taskInfo.getSourceAppName();
        val taskId = taskInfo.getTaskId();
        tdb.updateTask(sourceAppName, taskId, taskInfo);
        assertEquals(taskInfo, tdb.task(sourceAppName, taskId).orElse(null));

        val updated = new TaskInfo(
                taskInfo.getSourceAppName(),
                taskInfo.getTaskId(),
                taskInfo.getInstanceId(),
                taskInfo.getExecutorId(),
                taskInfo.getHostname(),
                taskInfo.getExecutable(),
                taskInfo.getResources(),
                taskInfo.getVolumes(),
                taskInfo.getLoggingSpec(),
                taskInfo.getEnv(),
                taskInfo.getState(),
                taskInfo.getMetadata(),
                new TaskResult(TaskResult.Status.LOST, -1),
                taskInfo.getErrorMessage(),
                taskInfo.getCreated(),
                new Date(taskInfo.getUpdated().getTime() - 100_000));
        tdb.updateTask(sourceAppName, taskId, updated);

        //Same as before, last stale update has been ignored
        assertEquals(taskInfo, tdb.task(sourceAppName, taskId).orElse(null));
    }
}