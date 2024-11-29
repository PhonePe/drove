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
import com.phonepe.drove.controller.ControllerTestUtils;
import com.phonepe.drove.models.taskinstance.TaskInfo;
import com.phonepe.drove.models.taskinstance.TaskState;
import lombok.SneakyThrows;
import lombok.val;
import org.apache.curator.framework.CuratorFramework;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 *
 */
@ExtendWith(ZookeeperTestExtension.class)
class ZkTaskDBTest extends ControllerTestBase {

    @Test
    @SneakyThrows
    void testDB(final CuratorFramework curator) {
        val tdb = new ZkTaskDB(curator, MAPPER);
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

}