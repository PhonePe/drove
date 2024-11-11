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

package com.phonepe.drove.controller.resourcemgmt;

import com.google.common.collect.Sets;
import com.phonepe.drove.controller.ControllerTestBase;
import com.phonepe.drove.controller.ControllerTestUtils;
import com.phonepe.drove.models.application.requirements.CPURequirement;
import com.phonepe.drove.models.application.requirements.MemoryRequirement;
import com.phonepe.drove.models.info.ExecutorResourceSnapshot;
import com.phonepe.drove.models.info.nodedata.ExecutorState;
import com.phonepe.drove.models.info.resources.PhysicalLayout;
import com.phonepe.drove.models.info.resources.available.AvailableCPU;
import com.phonepe.drove.models.info.resources.available.AvailableMemory;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.phonepe.drove.controller.ControllerTestUtils.executorId;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 */
class InMemoryClusterResourcesDBTest extends ControllerTestBase {

    @Test
    void testHostDetails() {
        val db = new InMemoryClusterResourcesDB();
        db.update(IntStream.rangeClosed(1, 100)
                          .mapToObj(ControllerTestUtils::generateExecutorNode)
                          .toList());
        {
            val snap = db.currentSnapshot(false);
            assertEquals(100, snap.size());
        }
        {
            IntStream.rangeClosed(1, 100)
                    .forEach(i -> assertEquals(executorId(i),
                                               db.currentSnapshot(executorId(i))
                                                       .map(ExecutorHostInfo::getExecutorId)
                                                       .orElse(null)));
        }
        {
            db.remove(IntStream.rangeClosed(1, 100).mapToObj(ControllerTestUtils::executorId).toList());
            assertTrue(db.currentSnapshot(false).isEmpty());
            IntStream.rangeClosed(1, 100)
                    .forEach(i -> assertEquals(executorId(i),
                                               db.lastKnownSnapshot(executorId(i))
                                                       .map(ExecutorHostInfo::getExecutorId)
                                                       .orElse(null)));
        }
        db.update(IntStream.rangeClosed(1, 100)
                          .mapToObj(ControllerTestUtils::generateExecutorNode)
                          .toList());
        {
            IntStream.rangeClosed(1, 100)
                    .forEach(i -> {
                        assertEquals(executorId(i),
                                     db.currentSnapshot(executorId(i))
                                             .map(ExecutorHostInfo::getExecutorId)
                                             .orElse(null));
                        assertNull(db.lastKnownSnapshot(ControllerTestUtils.executorId(i)).orElse(null));
                    });
        }
    }

    @Test
    void testResources() {
        val db = new InMemoryClusterResourcesDB();
        assertTrue(db.currentSnapshot(false).isEmpty());
        db.update(new ExecutorResourceSnapshot("INVALID_EXECUTOR",
                                               new AvailableCPU(Map.of(0, Set.of(2, 3, 4)),
                                                                Map.of(1, Set.of(0, 1))),
                                               new AvailableMemory(
                                                       Map.of(0, 3 * 128 * (2L ^ 20)),
                                                       Map.of(0, 128 * (2L ^ 20))),
                                               new PhysicalLayout(Map.of(0,
                                                                         Set.of(0, 1, 2, 3, 4),
                                                                         1,
                                                                         Set.of(0, 1, 2, 3, 4)),
                                                                  Map.of(0,
                                                                         4 * 128 * (2L ^ 20),
                                                                         1,
                                                                         4 * 128 * (2L ^ 20)))));
        assertTrue(db.currentSnapshot(false).isEmpty());
        val originalNodeData = ControllerTestUtils.generateExecutorNode(1);
        db.update(List.of(originalNodeData));
        val allocatedNode = db.selectNodes(List.of(new CPURequirement(2), new MemoryRequirement(128)),
                                           EnumSet.of(ExecutorState.ACTIVE),
                                           node -> true
                                          )
                .orElse(null);
        assertNotNull(allocatedNode);
        //Ensure nodes are allocated correctly
        {
            val nodeInfo = db.currentSnapshot(allocatedNode.getExecutorId()).orElse(null);
            assertNotNull(nodeInfo);
            //1. Free nodes have not been returned as allocated
            assertTrue(Sets.intersection(allocatedNode.getCpu().getCores().get(0),
                                         nodeInfo.getNodes()
                                                 .get(0)
                                                 .getCores()
                                                 .entrySet()
                                                 .stream()
                                                 .filter(e -> e.getValue()
                                                         .equals(ExecutorHostInfo.CoreState.FREE))
                                                 .map(Map.Entry::getKey)
                                                 .collect(Collectors.toSet())).isEmpty());
            //2. Nodes marked as allocated are indeed accounted as allocated
            assertEquals(allocatedNode.getCpu().getCores().get(0),
                         nodeInfo.getNodes()
                                 .get(0)
                                 .getCores()
                                 .entrySet()
                                 .stream()
                                 .filter(e -> e.getValue()
                                         .equals(ExecutorHostInfo.CoreState.ALLOCATED))
                                 .map(Map.Entry::getKey)
                                 .collect(Collectors.toSet()));
        }

        //Receive update to mark allocated nodes as in use
        val originalResource = originalNodeData.getState();
        db.update(new ExecutorResourceSnapshot(allocatedNode.getExecutorId(),
                                               new AvailableCPU(Map.of(0,
                                                                       Sets.difference(originalResource.getCpus()
                                                                                               .getFreeCores()
                                                                                               .get(0),
                                                                                       allocatedNode.getCpu()
                                                                                               .getCores()
                                                                                               .get(0))),
                                                                allocatedNode.getCpu().getCores()),
                                               new AvailableMemory(Map.of(0,
                                                                          originalResource.getMemory()
                                                                                  .getFreeMemory()
                                                                                  .get(0) - allocatedNode.getMemory()
                                                                                  .getMemoryInMB()
                                                                                  .get(0)),
                                                                   allocatedNode.getMemory().getMemoryInMB()),
                                               originalResource.getLayout()));
        //Ensure states have reflected properly
        {
            val nodeInfo = db.currentSnapshot(allocatedNode.getExecutorId()).orElse(null);
            assertNotNull(nodeInfo);
            assertEquals(allocatedNode.getCpu().getCores().get(0),
                         nodeInfo.getNodes()
                                 .get(0)
                                 .getCores()
                                 .entrySet()
                                 .stream()
                                 .filter(e -> e.getValue()
                                         .equals(ExecutorHostInfo.CoreState.IN_USE))
                                 .map(Map.Entry::getKey)
                                 .collect(Collectors.toSet()));
        }
        assertNull(db.selectNodes(List.of(new CPURequirement(4), new MemoryRequirement(128)),
                                  EnumSet.of(ExecutorState.ACTIVE),
                                  node -> true
                                 )
                           .orElse(null));
        db.deselectNode(allocatedNode.getExecutorId(), allocatedNode.getCpu(), allocatedNode.getMemory()); // Free up cores
        //Now it should be available
        assertNotNull(db.selectNodes(List.of(new CPURequirement(4), new MemoryRequirement(128)),
                                     EnumSet.of(ExecutorState.ACTIVE),
                                     node -> true
                                    )
                              .orElse(null));
    }

    @Test
    void testBlacklisting() {
        val db = new InMemoryClusterResourcesDB();
        IntStream.rangeClosed(1, 10)
                .forEach(i -> db.markBlacklisted(executorId(i)));
        assertTrue(IntStream.rangeClosed(1, 10)
                .allMatch(i -> db.isBlacklisted(executorId(i))));
        db.update(IntStream.rangeClosed(1, 5)
                .mapToObj(ControllerTestUtils::generateExecutorNode)
                .toList());
        assertTrue(IntStream.rangeClosed(1, 10)
                           .allMatch(i -> db.isBlacklisted(executorId(i)))); //This will prioritise the info in local map
        IntStream.rangeClosed(1, 10)
                .forEach(i -> db.unmarkBlacklisted(executorId(i)));
        assertTrue(IntStream.rangeClosed(1, 10)
                           .noneMatch(i -> db.isBlacklisted(executorId(i))));
        db.update(IntStream.rangeClosed(1, 10)
                          .mapToObj(index -> ControllerTestUtils.generateExecutorNode(index, Set.of(), true))
                          .toList());
        assertTrue(IntStream.rangeClosed(1, 10)
                           .allMatch(i -> db.isBlacklisted(executorId(i)))); //This will use node level data

    }

}