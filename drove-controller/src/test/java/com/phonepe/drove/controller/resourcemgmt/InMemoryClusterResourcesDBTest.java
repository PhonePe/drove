package com.phonepe.drove.controller.resourcemgmt;

import com.google.common.collect.Sets;
import com.phonepe.drove.controller.ControllerTestUtils;
import com.phonepe.drove.models.application.requirements.CPURequirement;
import com.phonepe.drove.models.application.requirements.MemoryRequirement;
import com.phonepe.drove.models.info.ExecutorResourceSnapshot;
import com.phonepe.drove.models.info.resources.available.AvailableCPU;
import com.phonepe.drove.models.info.resources.available.AvailableMemory;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 *
 */
class InMemoryClusterResourcesDBTest {

    @Test
    void testHostDetails() {
        val db = new InMemoryClusterResourcesDB();
        db.update(IntStream.rangeClosed(1, 100)
                          .mapToObj(ControllerTestUtils::generateExecutorNode)
                          .toList());
        {
            val snap = db.currentSnapshot();
            assertEquals(100, snap.size());
        }
        {
            IntStream.rangeClosed(1, 100)
                    .forEach(i -> assertEquals(ControllerTestUtils.executorId(i),
                                               db.currentSnapshot(ControllerTestUtils.executorId(i))
                                                       .map(ExecutorHostInfo::getExecutorId)
                                                       .orElse(null)));
        }
        {
            db.remove(IntStream.rangeClosed(1, 100).mapToObj(ControllerTestUtils::executorId).toList());
            assertTrue(db.currentSnapshot().isEmpty());
        }
    }

    @Test
    void testResources() {
        val db = new InMemoryClusterResourcesDB();
        assertTrue(db.currentSnapshot().isEmpty());
        db.update(new ExecutorResourceSnapshot("INVALID_EXECUTOR",
                                               new AvailableCPU(Map.of(0, Set.of(2, 3, 4)),
                                                                Map.of(1, Set.of(0, 1))),
                                               new AvailableMemory(
                                                       Map.of(0, 3 * 128 * (2L ^ 20)),
                                                       Map.of(0, 128 * (2L ^ 20)))));
        assertTrue(db.currentSnapshot().isEmpty());
        val originalNodeData = ControllerTestUtils.generateExecutorNode(1);
        db.update(List.of(originalNodeData));
        val allocatedNode = db.selectNodes(List.of(new CPURequirement(2), new MemoryRequirement(128)), node -> true)
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
                                                                   allocatedNode.getMemory().getMemoryInMB())));
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
        assertNull(db.selectNodes(List.of(new CPURequirement(4), new MemoryRequirement(128)), node -> true)
                           .orElse(null));
        db.deselectNode(allocatedNode); // Free up cores
        //Now it should be available
        assertNotNull(db.selectNodes(List.of(new CPURequirement(4), new MemoryRequirement(128)), node -> true)
                              .orElse(null));
    }


}