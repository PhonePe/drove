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
import lombok.val;
import org.junit.jupiter.api.Test;

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
}
