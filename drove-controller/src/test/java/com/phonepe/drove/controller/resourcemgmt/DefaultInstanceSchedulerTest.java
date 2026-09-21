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

import com.phonepe.drove.controller.ControllerTestBase;
import com.phonepe.drove.controller.ControllerTestUtils;
import com.phonepe.drove.models.application.placement.policies.RuleBasedPlacementPolicy;
import com.phonepe.drove.models.application.placement.policies.OnePerHostPlacementPolicy;
import com.phonepe.drove.models.application.placement.policies.MaxNPerHostPlacementPolicy;
import com.phonepe.drove.models.application.placement.policies.CompositePlacementPolicy;
import com.phonepe.drove.models.application.placement.policies.AnyPlacementPolicy;
import com.phonepe.drove.models.application.placement.policies.MatchTagPlacementPolicy;
import com.phonepe.drove.models.application.placement.policies.NoTagPlacementPolicy;
import com.phonepe.drove.models.application.requirements.GpuType;
import com.phonepe.drove.models.application.requirements.GpuVendor;
import com.phonepe.drove.models.info.ExecutorResourceSnapshot;
import com.phonepe.drove.models.info.GPUInfo;
import com.phonepe.drove.models.info.nodedata.ExecutorNodeData;
import com.phonepe.drove.models.info.nodedata.ExecutorState;
import com.phonepe.drove.models.info.nodedata.NodeTransportType;
import com.phonepe.drove.models.info.resources.PhysicalLayout;
import com.phonepe.drove.models.info.resources.available.AvailableCPU;
import com.phonepe.drove.models.info.resources.available.AvailableGPU;
import com.phonepe.drove.models.info.resources.available.AvailableMemory;
import lombok.val;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import static com.phonepe.drove.controller.ControllerTestUtils.appSpec;
import static com.phonepe.drove.controller.ControllerTestUtils.generateExecutorNode;
import static com.phonepe.drove.controller.ControllerTestUtils.localServiceSpec;
import static com.phonepe.drove.controller.ControllerTestUtils.taskSpec;
import static com.phonepe.drove.models.application.placement.policies.CompositePlacementPolicy.CombinerType.AND;
import static com.phonepe.drove.models.application.placement.policies.CompositePlacementPolicy.CombinerType.OR;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 *
 */
class DefaultInstanceSchedulerTest extends ControllerTestBase {

    @Test
    void testScheduling() {
        val rdb = new InMemoryClusterResourcesDB();
        val sched = createDefaultInstanceScheduler(rdb).getValue();
        val spec = appSpec();

        //Load cluster nodes
        rdb.update(IntStream.rangeClosed(1, 5).mapToObj(ControllerTestUtils::generateExecutorNode).toList());
        val schedId = "SCHED_ID_1";
        val instanceId = "I1";
        val node = sched.schedule(schedId, instanceId, spec).orElse(null);
        IntStream.rangeClosed(2, 25)
                .forEach(i -> {
                    val iId = "I" + i;
                    assertNotNull(sched.schedule(schedId, iId, spec).orElse(null));
                });
        assertNull(sched.schedule(schedId, instanceId, spec).orElse(null));
        sched.discardAllocation(schedId, instanceId, node);
        assertNotNull(sched.schedule(schedId, instanceId, spec).orElse(null));

    }

    @Test
    void testOnePerHostPlacementPolicy() {
        val rdb = new InMemoryClusterResourcesDB();
        val sched = createDefaultInstanceScheduler(rdb).getValue();
        val originalSpec = appSpec();
        val spec = originalSpec.withPlacementPolicy(new OnePerHostPlacementPolicy());
        rdb.update(IntStream.rangeClosed(1, 5).mapToObj(ControllerTestUtils::generateExecutorNode).toList());
        val schedId = "SCHED_ID_1";
        val instanceId = "I1";
        val node = sched.schedule(schedId, instanceId, spec).orElse(null);
        IntStream.rangeClosed(2, 5)
                .forEach(i -> {
                    val iId = "I" + i;
                    assertNotNull(sched.schedule(schedId, iId, spec).orElse(null));
                });
        assertNull(sched.schedule(schedId, instanceId, spec).orElse(null));
        sched.discardAllocation(schedId, instanceId, node);
        assertNotNull(sched.schedule(schedId, instanceId, spec).orElse(null));
    }

    @Test
    void testMaxNPerHostPlacementPolicy() {
        val rdb = new InMemoryClusterResourcesDB();
        val sched = createDefaultInstanceScheduler(rdb).getValue();
        val originalSpec = appSpec();
        val spec = originalSpec.withPlacementPolicy(new MaxNPerHostPlacementPolicy(2));
        rdb.update(IntStream.rangeClosed(1, 5).mapToObj(ControllerTestUtils::generateExecutorNode).toList());
        val schedId = "SCHED_ID_1";
        val instanceId = "I1";
        val node = sched.schedule(schedId, instanceId, spec).orElse(null);
        IntStream.rangeClosed(2, 10)
                .forEach(i -> {
                    val iId = "I" + i;
                    assertNotNull(sched.schedule(schedId, iId, spec).orElse(null));
                });
        assertNull(sched.schedule(schedId, instanceId, spec).orElse(null));
        sched.discardAllocation(schedId, instanceId, node);
        assertNotNull(sched.schedule(schedId, instanceId, spec).orElse(null));
    }

    @Test
    void testRuleBasedPolicyForSpecificNode() {
        val rdb = new InMemoryClusterResourcesDB();
        val sched = createDefaultInstanceScheduler(rdb).getValue();
        val originalSpec = appSpec();
        rdb.update(IntStream.rangeClosed(1, 3).mapToObj( i -> ControllerTestUtils.generateExecutorNode(i, Set.of(), false, Map.of("envvar1", "val" + i))).toList());
        rdb.update(IntStream.rangeClosed(4, 5).mapToObj( i -> ControllerTestUtils.generateExecutorNode(i, Set.of(), false, Map.of("envvar1", "val" + i, "specialNode", "yes"))).toList());
        val schedId = "SCHED_ID_1";
        val instanceId = "I1";
        int nodeToBeSelected = 2;
        val spec = originalSpec.withPlacementPolicy(new RuleBasedPlacementPolicy("ctx.allocation.node.metadata.envvar1 == \"val" + nodeToBeSelected + "\""));
        var node = sched.schedule(schedId, instanceId, spec).orElse(null);
        assertEquals("EXECUTOR_" + nodeToBeSelected, node.getExecutorId(), "Specific selected node as per ENV variable");
        sched.discardAllocation(schedId, instanceId, node);
    }

    @Test
    void testRuleBasedPolicyFromSpecialNodes() {
        val rdb = new InMemoryClusterResourcesDB();
        val sched = createDefaultInstanceScheduler(rdb).getValue();
        val originalSpec = appSpec();
        rdb.update(IntStream.rangeClosed(1, 3).mapToObj( i -> ControllerTestUtils.generateExecutorNode(i, Set.of(), false, Map.of("envvar1", "val" + i))).toList());
        rdb.update(IntStream.rangeClosed(4, 5).mapToObj( i -> ControllerTestUtils.generateExecutorNode(i, Set.of(), false, Map.of("envvar1", "val" + i, "specialNode", "yes"))).toList());
        val schedId = "SCHED_ID_1";
        val instanceId = "I1";
        val spec = originalSpec.withPlacementPolicy(new RuleBasedPlacementPolicy(
                "ctx.allocation.node.metadata[\"specialNode\"] == \"yes\""));
        var node = sched.schedule(schedId, instanceId, spec).orElse(null);
        Map<String, String> result = Map.of("EXECUTOR_4", "EXECUTOR_4", "EXECUTOR_5", "EXECUTOR_5");
        assertNotNull(node, "Special node selected as per ENV variable");
        assertTrue(result.containsKey(node.getExecutorId()), "Special node selected as per ENV variable");
        sched.discardAllocation(schedId, instanceId, node);

    }

    @Test
    void testMatchTagPlacementPolicyNormal() {
        val rdb = new InMemoryClusterResourcesDB();
        val sched = createDefaultInstanceScheduler(rdb).getValue();
        val originalSpec = appSpec();
        val spec = originalSpec.withPlacementPolicy(new MatchTagPlacementPolicy("test"));
        rdb.update(IntStream.rangeClosed(1, 3).mapToObj(i -> generateExecutorNode(i, Set.of("test"))).toList());
        rdb.update(IntStream.rangeClosed(4, 5).mapToObj(ControllerTestUtils::generateExecutorNode).toList());
        val schedId = "SCHED_ID_1";
        val instanceId = "I1";
        val node = sched.schedule(schedId, instanceId, spec).orElse(null);
        IntStream.rangeClosed(2, 15)
                .forEach(i -> {
                    val iId = "I" + i;
                    assertNotNull(sched.schedule(schedId, iId, spec).orElse(null));
                });
        assertNull(sched.schedule(schedId, instanceId, spec).orElse(null));
        sched.discardAllocation(schedId, instanceId, node);
        assertNotNull(sched.schedule(schedId, instanceId, spec).orElse(null));
    }

    @Test
    void testNoTagPlacementPolicy() {
        val rdb = new InMemoryClusterResourcesDB();
        val sched = createDefaultInstanceScheduler(rdb).getValue();
        val originalSpec = appSpec();
        val spec = originalSpec.withPlacementPolicy(new NoTagPlacementPolicy());
        rdb.update(IntStream.rangeClosed(1, 3).mapToObj(i -> generateExecutorNode(i, Set.of("test"))).toList());
        rdb.update(IntStream.rangeClosed(4, 5).mapToObj(ControllerTestUtils::generateExecutorNode).toList());
        val schedId = "SCHED_ID_1";
        val instanceId = "I1";
        val node = sched.schedule(schedId, instanceId, spec).orElse(null);
        IntStream.rangeClosed(2, 10)
                .forEach(i -> {
                    val iId = "I" + i;
                    assertNotNull(sched.schedule(schedId, iId, spec).orElse(null));
                });
        assertNull(sched.schedule(schedId, instanceId, spec).orElse(null));
        sched.discardAllocation(schedId, instanceId, node);
        assertNotNull(sched.schedule(schedId, instanceId, spec).orElse(null));
    }

    @Test
    void testCompositePlacementPolicyAnd() {
        val rdb = new InMemoryClusterResourcesDB();
        val sched = createDefaultInstanceScheduler(rdb).getValue();
        val originalSpec = appSpec();
        val spec = originalSpec
                .withPlacementPolicy(new CompositePlacementPolicy(
                        List.of(new MatchTagPlacementPolicy("test"), new MaxNPerHostPlacementPolicy(2)),
                        AND));
        rdb.update(IntStream.rangeClosed(1, 3).mapToObj(i -> generateExecutorNode(i, Set.of("test"))).toList());
        rdb.update(IntStream.rangeClosed(4, 5).mapToObj(ControllerTestUtils::generateExecutorNode).toList());
        val schedId = "SCHED_ID_1";
        val instanceId = "I1";
        val node = sched.schedule(schedId, instanceId, spec).orElse(null);
        IntStream.rangeClosed(2, 6)
                .forEach(i -> {
                    val iId = "I" + i;
                    assertNotNull(sched.schedule(schedId, iId, spec).orElse(null));
                });
        assertNull(sched.schedule(schedId, instanceId, spec).orElse(null));
        sched.discardAllocation(schedId, instanceId, node);
        assertNotNull(sched.schedule(schedId, instanceId, spec).orElse(null));
    }

    @Test
    void testCompositePlacementPolicyOR() {
        val rdb = new InMemoryClusterResourcesDB();
        val sched = createDefaultInstanceScheduler(rdb).getValue();
        val originalSpec = appSpec();
        val spec = originalSpec.withPlacementPolicy(new CompositePlacementPolicy(
                List.of(new MatchTagPlacementPolicy("test"),
                        new MatchTagPlacementPolicy("test2")),
                OR));
        rdb.update(IntStream.rangeClosed(1, 2).mapToObj(i -> generateExecutorNode(i, Set.of("test"))).toList());
        rdb.update(IntStream.rangeClosed(2, 3).mapToObj(i -> generateExecutorNode(i, Set.of("test2"))).toList());
        rdb.update(IntStream.rangeClosed(4, 5).mapToObj(ControllerTestUtils::generateExecutorNode).toList());
        val schedId = "SCHED_ID_1";
        val instanceId = "I1";
        val node = sched.schedule(schedId, instanceId, spec).orElse(null);
        IntStream.rangeClosed(2, 15)
                .forEach(i -> {
                    val iId = "I" + i;
                    assertNotNull(sched.schedule(schedId, iId, spec).orElse(null));
                });
        assertNull(sched.schedule(schedId, instanceId, spec).orElse(null));
        sched.discardAllocation(schedId, instanceId, node);
        assertNotNull(sched.schedule(schedId, instanceId, spec).orElse(null));
    }


    @Test
    void testMultiSessionAllocation() {
        val rdb = new InMemoryClusterResourcesDB();
        val sched = createDefaultInstanceScheduler(rdb).getValue();
        val spec = appSpec();

        //Load cluster nodes
        rdb.update(IntStream.rangeClosed(1, 5).mapToObj(ControllerTestUtils::generateExecutorNode).toList());
        {
            val schedId = "SCHED_ID_1";
            IntStream.rangeClosed(1, 20)
                    .forEach(i -> {
                        val instanceId = "I" + i;
                        assertNotNull(sched.schedule(schedId, instanceId, spec).orElse(null));
                    });
            sched.finaliseSession(schedId);
        }
        {
            val schedId = "SCHED_ID_2";
            IntStream.rangeClosed(1, 5)
                    .forEach(i -> {
                        val instanceId = "I" + i + 10;
                        assertNotNull(sched.schedule(schedId, instanceId, spec).orElse(null));
                    });
            sched.finaliseSession(schedId);
        }

    }

    @Test
    void testBlacklistSkipping() {
        val rdb = new InMemoryClusterResourcesDB();
        val sched = createDefaultInstanceScheduler(rdb).getValue();
        val spec = appSpec();

        //Load cluster nodes
        rdb.update(IntStream.rangeClosed(1, 3).mapToObj(ControllerTestUtils::generateExecutorNode).toList());
        rdb.update(IntStream.rangeClosed(4, 5)
                           .mapToObj(index -> ControllerTestUtils.generateExecutorNode(index, Set.of(), true))
                           .toList());
        val schedId = "SCHED_ID_1";
        val instanceId = "I1";
        val node = sched.schedule(schedId, instanceId, spec).orElse(null);
        IntStream.rangeClosed(2, 15)
                .forEach(i -> {
                    val iId = "I" + i;
                    assertNotNull(sched.schedule(schedId, iId, spec).orElse(null));
                });
        assertNull(sched.schedule(schedId, instanceId, spec).orElse(null));
        sched.discardAllocation(schedId, instanceId, node);
        assertNotNull(sched.schedule(schedId, instanceId, spec).orElse(null));
    }


    @Test
    void testSimulatePlacementWithAnyPolicy() {
        val rdb = new InMemoryClusterResourcesDB(new CompositeExecutorSelector(new FirstFitExecutorSelector(new GpuAwareNumaExecutorResourceAllocator()), new ScoredExecutorSelector(new GpuAwareNumaExecutorResourceAllocator(), new MaxGpuNumaLocalAllocation())));
        val sched = createDefaultInstanceScheduler(rdb).getValue();
        rdb.update(IntStream.rangeClosed(1, 5).mapToObj(ControllerTestUtils::generateExecutorNode).toList());
        val placements = sched.simulatePlacement(appSpec(), new AnyPlacementPolicy(), 10);
        assertEquals(10, placements.size());
        val knownExecutors = IntStream.rangeClosed(1, 5)
                .mapToObj(ControllerTestUtils::executorId)
                .collect(Collectors.toSet());
        assertTrue(placements.stream().allMatch(node -> knownExecutors.contains(node.getExecutorId())));
    }

    @Test
    void testSimulatePlacementWithOnePerHostPolicy() {
        val rdb = new InMemoryClusterResourcesDB(new CompositeExecutorSelector(new FirstFitExecutorSelector(new GpuAwareNumaExecutorResourceAllocator()), new ScoredExecutorSelector(new GpuAwareNumaExecutorResourceAllocator(), new MaxGpuNumaLocalAllocation())));
        val sched = createDefaultInstanceScheduler(rdb).getValue();
        val spec = appSpec().withPlacementPolicy(new OnePerHostPlacementPolicy());
        rdb.update(IntStream.rangeClosed(1, 5).mapToObj(ControllerTestUtils::generateExecutorNode).toList());
        val placements = sched.simulatePlacement(spec, new OnePerHostPlacementPolicy(), 6);
        assertEquals(5, placements.size());
        assertEquals(5, placements.stream().map(node -> node.getExecutorId()).collect(Collectors.toSet()).size());
    }

    @Test
    void testSimulatePlacementWithMaxNPerHostPolicy() {
        val rdb = new InMemoryClusterResourcesDB(new CompositeExecutorSelector(new FirstFitExecutorSelector(new GpuAwareNumaExecutorResourceAllocator()), new ScoredExecutorSelector(new GpuAwareNumaExecutorResourceAllocator(), new MaxGpuNumaLocalAllocation())));
        val sched = createDefaultInstanceScheduler(rdb).getValue();
        val spec = appSpec().withPlacementPolicy(new MaxNPerHostPlacementPolicy(2));
        rdb.update(IntStream.rangeClosed(1, 5).mapToObj(ControllerTestUtils::generateExecutorNode).toList());
        val placements = sched.simulatePlacement(spec, new MaxNPerHostPlacementPolicy(2), 12);
        assertEquals(10, placements.size());
        val placementsPerExecutor = placements.stream()
                .collect(Collectors.groupingBy(node -> node.getExecutorId(), Collectors.counting()));
        assertTrue(placementsPerExecutor.values().stream().allMatch(count -> count <= 2));
    }

    @Test
    void testSimulatePlacementWithMatchTagPolicy() {
        val rdb = new InMemoryClusterResourcesDB(new CompositeExecutorSelector(new FirstFitExecutorSelector(new GpuAwareNumaExecutorResourceAllocator()), new ScoredExecutorSelector(new GpuAwareNumaExecutorResourceAllocator(), new MaxGpuNumaLocalAllocation())));
        val sched = createDefaultInstanceScheduler(rdb).getValue();
        val spec = appSpec().withPlacementPolicy(new MatchTagPlacementPolicy("test"));
        rdb.update(IntStream.rangeClosed(1, 3).mapToObj(i -> generateExecutorNode(i, Set.of("test"))).toList());
        rdb.update(IntStream.rangeClosed(4, 5).mapToObj(ControllerTestUtils::generateExecutorNode).toList());
        val placements = sched.simulatePlacement(spec, new MatchTagPlacementPolicy("test"), 15);
        assertEquals(15, placements.size());
        val taggedExecutors = IntStream.rangeClosed(1, 3)
                .mapToObj(ControllerTestUtils::executorId)
                .collect(Collectors.toSet());
        assertTrue(placements.stream().allMatch(node -> taggedExecutors.contains(node.getExecutorId())));
    }

    @Test
    void testSimulatePlacementWithNoTagPolicy() {
        val rdb = new InMemoryClusterResourcesDB(new CompositeExecutorSelector(new FirstFitExecutorSelector(new GpuAwareNumaExecutorResourceAllocator()), new ScoredExecutorSelector(new GpuAwareNumaExecutorResourceAllocator(), new MaxGpuNumaLocalAllocation())));
        val sched = createDefaultInstanceScheduler(rdb).getValue();
        val spec = appSpec().withPlacementPolicy(new NoTagPlacementPolicy());
        rdb.update(IntStream.rangeClosed(1, 3).mapToObj(i -> generateExecutorNode(i, Set.of("test"))).toList());
        rdb.update(IntStream.rangeClosed(4, 5).mapToObj(ControllerTestUtils::generateExecutorNode).toList());
        val placements = sched.simulatePlacement(spec, new NoTagPlacementPolicy(), 10);
        assertEquals(10, placements.size());
        val untaggedExecutors = IntStream.rangeClosed(4, 5)
                .mapToObj(ControllerTestUtils::executorId)
                .collect(Collectors.toSet());
        assertTrue(placements.stream().allMatch(node -> untaggedExecutors.contains(node.getExecutorId())));
    }

    @Test
    void testSimulatePlacementWithRuleBasedPolicy() {
        val rdb = new InMemoryClusterResourcesDB(new CompositeExecutorSelector(new FirstFitExecutorSelector(new GpuAwareNumaExecutorResourceAllocator()), new ScoredExecutorSelector(new GpuAwareNumaExecutorResourceAllocator(), new MaxGpuNumaLocalAllocation())));
        val sched = createDefaultInstanceScheduler(rdb).getValue();
        val spec = appSpec().withPlacementPolicy(new RuleBasedPlacementPolicy(
                "'/allocation/node/metadata/envvar1' == \"val1\"",
                RuleBasedPlacementPolicy.RuleType.HOPE));
        rdb.update(IntStream.rangeClosed(1, 3).mapToObj(i -> ControllerTestUtils.generateExecutorNode(i, Set.of(), false, Map.of("envvar1", "val" + i))).toList());
        val placements = sched.simulatePlacement(spec, null, 3);
        assertEquals(3, placements.size());
        assertTrue(placements.stream().allMatch(node -> "EXECUTOR_1".equals(node.getExecutorId())));
    }

    @Test
    void testSimulatePlacementWithCompositePolicyAnd() {
        val rdb = new InMemoryClusterResourcesDB(new CompositeExecutorSelector(new FirstFitExecutorSelector(new GpuAwareNumaExecutorResourceAllocator()), new ScoredExecutorSelector(new GpuAwareNumaExecutorResourceAllocator(), new MaxGpuNumaLocalAllocation())));
        val sched = createDefaultInstanceScheduler(rdb).getValue();
        val spec = appSpec().withPlacementPolicy(new CompositePlacementPolicy(
                List.of(new MatchTagPlacementPolicy("test"), new MaxNPerHostPlacementPolicy(2)),
                AND));
        rdb.update(IntStream.rangeClosed(1, 3).mapToObj(i -> generateExecutorNode(i, Set.of("test"))).toList());
        rdb.update(IntStream.rangeClosed(4, 5).mapToObj(ControllerTestUtils::generateExecutorNode).toList());
        val placements = sched.simulatePlacement(spec, null, 8);
        assertEquals(6, placements.size());
        val placementsPerExecutor = placements.stream()
                .collect(Collectors.groupingBy(node -> node.getExecutorId(), Collectors.counting()));
        assertTrue(placementsPerExecutor.values().stream().allMatch(count -> count <= 2));
    }

    @Test
    void testSimulatePlacementDoesNotChangeClusterState() {
        val rdb = new InMemoryClusterResourcesDB(new CompositeExecutorSelector(new FirstFitExecutorSelector(new GpuAwareNumaExecutorResourceAllocator()), new ScoredExecutorSelector(new GpuAwareNumaExecutorResourceAllocator(), new MaxGpuNumaLocalAllocation())));
        val sched = createDefaultInstanceScheduler(rdb).getValue();
        rdb.update(IntStream.rangeClosed(1, 5).mapToObj(ControllerTestUtils::generateExecutorNode).toList());
        val usedCoresBefore = usedCores(rdb);
        val usedMemoryBefore = usedMemory(rdb);
        val placements = sched.simulatePlacement(appSpec(), new AnyPlacementPolicy(), 10);
        assertEquals(10, placements.size());
        assertEquals(usedCoresBefore, usedCores(rdb));
        assertEquals(usedMemoryBefore, usedMemory(rdb));
    }

    @Test
    void testSimulatePlacementWithTaskSpec() {
        val rdb = new InMemoryClusterResourcesDB(new CompositeExecutorSelector(new FirstFitExecutorSelector(new GpuAwareNumaExecutorResourceAllocator()), new ScoredExecutorSelector(new GpuAwareNumaExecutorResourceAllocator(), new MaxGpuNumaLocalAllocation())));
        val sched = createDefaultInstanceScheduler(rdb).getValue();
        rdb.update(IntStream.rangeClosed(1, 5).mapToObj(ControllerTestUtils::generateExecutorNode).toList());
        val placements = sched.simulatePlacement(taskSpec(), new AnyPlacementPolicy(), 5);
        assertEquals(5, placements.size());
    }

    @Test
    void testSimulatePlacementWithLocalServiceSpec() {
        val rdb = new InMemoryClusterResourcesDB(new CompositeExecutorSelector(new FirstFitExecutorSelector(new GpuAwareNumaExecutorResourceAllocator()), new ScoredExecutorSelector(new GpuAwareNumaExecutorResourceAllocator(), new MaxGpuNumaLocalAllocation())));
        val sched = createDefaultInstanceScheduler(rdb).getValue();
        rdb.update(IntStream.rangeClosed(1, 5).mapToObj(ControllerTestUtils::generateExecutorNode).toList());
        val placements = sched.simulatePlacement(localServiceSpec(), new AnyPlacementPolicy(), 5);
        assertEquals(5, placements.size());
    }

    @Test
    void testSimulatePlacementDoesNotAffectRealScheduling() {
        val rdb = new InMemoryClusterResourcesDB(new CompositeExecutorSelector(new FirstFitExecutorSelector(new GpuAwareNumaExecutorResourceAllocator()), new ScoredExecutorSelector(new GpuAwareNumaExecutorResourceAllocator(), new MaxGpuNumaLocalAllocation())));
        val sched = createDefaultInstanceScheduler(rdb).getValue();
        rdb.update(IntStream.rangeClosed(1, 5).mapToObj(ControllerTestUtils::generateExecutorNode).toList());
        val spec = appSpec();
        sched.simulatePlacement(spec, new AnyPlacementPolicy(), 10);
        IntStream.rangeClosed(1, 20)
                .forEach(i -> assertNotNull(sched.schedule("SCHED_ID_1", "I" + i, spec).orElse(null)));
    }

    @Test
    void testCheckPlacementCapacityBound() {
        //2 executors x 5 free cores each; 15 x 1-core instances can not fit
        val rdb = new InMemoryClusterResourcesDB(new CompositeExecutorSelector(new FirstFitExecutorSelector(new GpuAwareNumaExecutorResourceAllocator()), new ScoredExecutorSelector(new GpuAwareNumaExecutorResourceAllocator(), new MaxGpuNumaLocalAllocation())));
        val sched = createDefaultInstanceScheduler(rdb).getValue();
        rdb.update(IntStream.rangeClosed(1, 2).mapToObj(ControllerTestUtils::generateExecutorNode).toList());
        val result = sched.checkPlacement(appSpec(), 15);
        assertEquals(15, result.getRequested());
        assertEquals(10, result.getPlaced());
        assertFalse(result.fits());
        assertTrue(result.getErrors().stream().anyMatch(error -> error.contains("only 10 of 15")));
        assertTrue(result.getErrors().stream().anyMatch(error -> error.contains("cores")));
    }

    @Test
    void testCheckPlacementAllFit() {
        //2 executors x 5 free cores each; 10 x 1-core instances fit exactly
        val rdb = new InMemoryClusterResourcesDB(new CompositeExecutorSelector(new FirstFitExecutorSelector(new GpuAwareNumaExecutorResourceAllocator()), new ScoredExecutorSelector(new GpuAwareNumaExecutorResourceAllocator(), new MaxGpuNumaLocalAllocation())));
        val sched = createDefaultInstanceScheduler(rdb).getValue();
        rdb.update(IntStream.rangeClosed(1, 2).mapToObj(ControllerTestUtils::generateExecutorNode).toList());
        val result = sched.checkPlacement(appSpec(), 10);
        assertEquals(10, result.getRequested());
        assertEquals(10, result.getPlaced());
        assertTrue(result.fits());
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    void testCheckPlacementMultiExecutorPacking() {
        //ANY packs across both executors; ONE_PER_HOST places at most 1 per host
        val rdb = new InMemoryClusterResourcesDB(new CompositeExecutorSelector(new FirstFitExecutorSelector(new GpuAwareNumaExecutorResourceAllocator()), new ScoredExecutorSelector(new GpuAwareNumaExecutorResourceAllocator(), new MaxGpuNumaLocalAllocation())));
        val sched = createDefaultInstanceScheduler(rdb).getValue();
        rdb.update(IntStream.rangeClosed(1, 2).mapToObj(ControllerTestUtils::generateExecutorNode).toList());
        val anyResult = sched.checkPlacement(appSpec(), 10);
        assertTrue(anyResult.fits());
        assertTrue(anyResult.getErrors().isEmpty());
        val onePerHostResult = sched.checkPlacement(
                appSpec().withPlacementPolicy(new OnePerHostPlacementPolicy()), 2);
        assertTrue(onePerHostResult.fits());
        val onePerHostOverflow = sched.checkPlacement(
                appSpec().withPlacementPolicy(new OnePerHostPlacementPolicy()), 3);
        assertFalse(onePerHostOverflow.fits());
        assertEquals(2, onePerHostOverflow.getPlaced());
    }

    @Test
    void testCheckPlacementDeterministic() {
        val rdb = new InMemoryClusterResourcesDB(new CompositeExecutorSelector(new FirstFitExecutorSelector(new GpuAwareNumaExecutorResourceAllocator()), new ScoredExecutorSelector(new GpuAwareNumaExecutorResourceAllocator(), new MaxGpuNumaLocalAllocation())));
        val sched = createDefaultInstanceScheduler(rdb).getValue();
        rdb.update(IntStream.rangeClosed(1, 5).mapToObj(ControllerTestUtils::generateExecutorNode).toList());
        val first = sched.checkPlacement(appSpec(), 12);
        val second = sched.checkPlacement(appSpec(), 12);
        assertEquals(first.getPlaced(), second.getPlaced());
        assertEquals(first.getErrors(), second.getErrors());
    }


    @Test
    void testCheckPlacementGpuCapacityBound() {
        //2 executors x 1 GPU each; 3 x 1-GPU instances can not fit
        val rdb = new InMemoryClusterResourcesDB(new CompositeExecutorSelector(new FirstFitExecutorSelector(new GpuAwareNumaExecutorResourceAllocator()), new ScoredExecutorSelector(new GpuAwareNumaExecutorResourceAllocator(), new MaxGpuNumaLocalAllocation())));
        val sched = createDefaultInstanceScheduler(rdb).getValue();
        rdb.update(List.of(gpuExecutorNode("EXECUTOR_1", "H100"),
                           gpuExecutorNode("EXECUTOR_2", "H100")));
        val spec = ControllerTestUtils.withGPU(appSpec(), 1, new GpuType(GpuVendor.NVIDIA, "H100"));
        val result = sched.checkPlacement(spec, 3);
        assertEquals(3, result.getRequested());
        assertEquals(2, result.getPlaced());
        assertFalse(result.fits());
        assertTrue(result.getErrors().stream().anyMatch(error -> error.contains("only 2 of 3")));
    }

    @Test
    void testCheckPlacementGpuTypeMismatch() {
        //Cluster has A100 only; H100 request places nothing
        val rdb = new InMemoryClusterResourcesDB(new CompositeExecutorSelector(new FirstFitExecutorSelector(new GpuAwareNumaExecutorResourceAllocator()), new ScoredExecutorSelector(new GpuAwareNumaExecutorResourceAllocator(), new MaxGpuNumaLocalAllocation())));
        val sched = createDefaultInstanceScheduler(rdb).getValue();
        rdb.update(List.of(gpuExecutorNode("EXECUTOR_1", "A100")));
        val spec = ControllerTestUtils.withGPU(appSpec(), 1, new GpuType(GpuVendor.NVIDIA, "H100"));
        val result = sched.checkPlacement(spec, 1);
        assertEquals(1, result.getRequested());
        assertEquals(0, result.getPlaced());
        assertFalse(result.fits());
    }

    @Test
    void testCheckPlacementGpuAllFit() {
        //2 executors x 1 GPU each; 2 x 1-GPU instances fit exactly
        val rdb = new InMemoryClusterResourcesDB(new CompositeExecutorSelector(new FirstFitExecutorSelector(new GpuAwareNumaExecutorResourceAllocator()), new ScoredExecutorSelector(new GpuAwareNumaExecutorResourceAllocator(), new MaxGpuNumaLocalAllocation())));
        val sched = createDefaultInstanceScheduler(rdb).getValue();
        rdb.update(List.of(gpuExecutorNode("EXECUTOR_1", "H100"),
                           gpuExecutorNode("EXECUTOR_2", "H100")));
        val spec = ControllerTestUtils.withGPU(appSpec(), 1, new GpuType(GpuVendor.NVIDIA, "H100"));
        val result = sched.checkPlacement(spec, 2);
        assertEquals(2, result.getPlaced());
        assertTrue(result.fits());
        assertTrue(result.getErrors().isEmpty());
    }

    private static ExecutorNodeData gpuExecutorNode(String executorId, String gpuModel) {
        val freeCores = Map.of(0, Set.of(0, 1, 2, 3, 4));
        val freeMemory = Map.of(0, 1024L * 1024 * 1024);
        val gpuId = "GPU-" + executorId;
        val snapshot = new ExecutorResourceSnapshot(
                executorId,
                new AvailableCPU(freeCores, Map.of()),
                new AvailableMemory(freeMemory, Map.of(0, 0L)),
                new AvailableGPU(List.of(gpuId), List.of()),
                new PhysicalLayout(freeCores, freeMemory, Map.of(gpuId, new GPUInfo(gpuId, GpuVendor.NVIDIA, gpuModel, 0))));
        return new ExecutorNodeData("host-" + executorId,
                                    8080,
                                    NodeTransportType.HTTP,
                                    new Date(),
                                    snapshot,
                                    List.of(),
                                    List.of(),
                                    List.of(),
                                    Set.of(),
                                    Map.of(),
                                    ExecutorState.ACTIVE);
    }

    private static long usedCores(InMemoryClusterResourcesDB rdb) {
        return rdb.currentSnapshot(false)
                .stream()
                .mapToInt(hostInfo -> hostInfo.getNodeData().getState().getCpus().getUsedCores().values()
                        .stream()
                        .mapToInt(Set::size)
                        .sum())
                .sum();
    }

    private static long usedMemory(InMemoryClusterResourcesDB rdb) {
        return rdb.currentSnapshot(false)
                .stream()
                .mapToLong(hostInfo -> hostInfo.getNodeData().getState().getMemory().getUsedMemory().values()
                        .stream()
                        .mapToLong(value -> value)
                        .sum())
                .sum();
    }

    @Test
    void testCheckPlacementMemoryBound() {
        //2 executors x 1 core each; memory is the binding resource, cores are not
        val rdb = new InMemoryClusterResourcesDB(new CompositeExecutorSelector(new FirstFitExecutorSelector(new GpuAwareNumaExecutorResourceAllocator()), new ScoredExecutorSelector(new GpuAwareNumaExecutorResourceAllocator(), new MaxGpuNumaLocalAllocation())));
        val sched = createDefaultInstanceScheduler(rdb).getValue();
        val freeCores = Map.of(0, Set.of(0, 1, 2, 3, 4));
        val freeMemory = Map.of(0, 1024L);
        rdb.update(List.of(executorNodeWithMemory("EXECUTOR_1", freeCores, freeMemory),
                           executorNodeWithMemory("EXECUTOR_2", freeCores, freeMemory)));
        //Each instance needs 1 core and 512 MB; 5 x 512 MB > 2048 MB free memory
        val result = sched.checkPlacement(appSpec(), 5);
        assertEquals(5, result.getRequested());
        assertEquals(4, result.getPlaced());
        assertFalse(result.fits());
        assertTrue(result.getErrors().stream().anyMatch(error -> error.contains("only 4 of 5")));
    }

    @Test
    void testCheckPlacementMultiGpuPerInstance() {
        //1 executor with 2 GPUs; 1 instance needs 3 GPUs -> placed 0
        val rdb = new InMemoryClusterResourcesDB(new CompositeExecutorSelector(new FirstFitExecutorSelector(new GpuAwareNumaExecutorResourceAllocator()), new ScoredExecutorSelector(new GpuAwareNumaExecutorResourceAllocator(), new MaxGpuNumaLocalAllocation())));
        val sched = createDefaultInstanceScheduler(rdb).getValue();
        rdb.update(List.of(gpuExecutorNodeWithCount("EXECUTOR_1", "H100", 2)));
        val spec = ControllerTestUtils.withGPU(appSpec(), 3, new GpuType(GpuVendor.NVIDIA, "H100"));
        val result = sched.checkPlacement(spec, 1);
        assertEquals(1, result.getRequested());
        assertEquals(0, result.getPlaced());
        assertFalse(result.fits());
    }

    @Test
    void testCheckPlacementMultiGpuPerInstanceAcrossNodes() {
        //2 executors x 2 GPUs each; 2 instances x 2 GPUs fit exactly, 3 instances place 2
        val rdb = new InMemoryClusterResourcesDB(new CompositeExecutorSelector(new FirstFitExecutorSelector(new GpuAwareNumaExecutorResourceAllocator()), new ScoredExecutorSelector(new GpuAwareNumaExecutorResourceAllocator(), new MaxGpuNumaLocalAllocation())));
        val sched = createDefaultInstanceScheduler(rdb).getValue();
        rdb.update(List.of(gpuExecutorNodeWithCount("EXECUTOR_1", "H100", 2),
                           gpuExecutorNodeWithCount("EXECUTOR_2", "H100", 2)));
        val spec = ControllerTestUtils.withGPU(appSpec(), 2, new GpuType(GpuVendor.NVIDIA, "H100"));
        val fits = sched.checkPlacement(spec, 2);
        assertEquals(2, fits.getPlaced());
        assertTrue(fits.fits());
        val overflow = sched.checkPlacement(spec, 3);
        assertEquals(2, overflow.getPlaced());
        assertFalse(overflow.fits());
    }

    private static ExecutorNodeData executorNodeWithMemory(String executorId, Map<Integer, Set<Integer>> freeCores, Map<Integer, Long> freeMemory) {
        val usedMemory = freeMemory.keySet().stream().collect(Collectors.toMap(k -> k, k -> 0L));
        val snapshot = new ExecutorResourceSnapshot(
                executorId,
                new AvailableCPU(freeCores, Map.of()),
                new AvailableMemory(freeMemory, usedMemory),
                new AvailableGPU(List.of(), List.of()),
                new PhysicalLayout(freeCores, freeMemory, Map.of()));
        return new ExecutorNodeData("host-" + executorId,
                                    8080,
                                    NodeTransportType.HTTP,
                                    new Date(),
                                    snapshot,
                                    List.of(),
                                    List.of(),
                                    List.of(),
                                    Set.of(),
                                    Map.of(),
                                    ExecutorState.ACTIVE);
    }

    private static ExecutorNodeData gpuExecutorNodeWithCount(String executorId, String gpuModel, int gpuCount) {
        val freeCores = Map.of(0, Set.of(0, 1, 2, 3, 4));
        val freeMemory = Map.of(0, 1024L * 1024 * 1024);
        val gpuIds = IntStream.rangeClosed(1, gpuCount)
                .mapToObj(i -> "GPU-" + executorId + "-" + i)
                .toList();
        val layoutGpus = gpuIds.stream()
                .collect(Collectors.toMap(gpuId -> gpuId,
                                          gpuId -> new GPUInfo(gpuId, GpuVendor.NVIDIA, gpuModel, 0)));
        val snapshot = new ExecutorResourceSnapshot(
                executorId,
                new AvailableCPU(freeCores, Map.of()),
                new AvailableMemory(freeMemory, Map.of(0, 0L)),
                new AvailableGPU(gpuIds, List.of()),
                new PhysicalLayout(freeCores, freeMemory, layoutGpus));
        return new ExecutorNodeData("host-" + executorId,
                                    8080,
                                    NodeTransportType.HTTP,
                                    new Date(),
                                    snapshot,
                                    List.of(),
                                    List.of(),
                                    List.of(),
                                    Set.of(),
                                    Map.of(),
                                    ExecutorState.ACTIVE);
    }
}
