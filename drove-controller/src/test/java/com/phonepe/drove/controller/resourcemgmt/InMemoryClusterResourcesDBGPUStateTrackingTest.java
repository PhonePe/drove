package com.phonepe.drove.controller.resourcemgmt;

import com.phonepe.drove.controller.resourcemgmt.allocator.GpuAwareNumaExecutorResourceAllocator;
import com.phonepe.drove.controller.resourcemgmt.selector.CompositeExecutorSelector;
import com.phonepe.drove.controller.resourcemgmt.selector.FirstFitExecutorSelector;
import com.phonepe.drove.controller.resourcemgmt.selector.scored.MaxGpuNumaLocalAllocation;
import com.phonepe.drove.controller.resourcemgmt.selector.scored.ScoredExecutorSelector;
import com.phonepe.drove.models.application.requirements.CPURequirement;
import com.phonepe.drove.models.application.requirements.GPURequirement;
import com.phonepe.drove.models.application.requirements.GpuType;
import com.phonepe.drove.models.application.requirements.GpuVendor;
import com.phonepe.drove.models.application.requirements.MemoryRequirement;
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
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test case for GPU State Tracking Inconsistency.
 * 
 * ISSUE: GPU state transitions (FREE → ALLOCATED → IN_USE) may not be tracked
 *        consistently between executor and controller
 * 
 * Current State:
 *   - Exec sends GPU state updates but controller may not have consistent view
 *   - GPU transitions not properly tracked
 *   - Race conditions in state transitions
 * Expected State:
 *   - GPU state should be consistent across all components
 *   - Transitions should be atomic and tracked properly
 * 
 * Rationale: Inconsistent GPU state tracking causes:
 * - Double allocation of same GPU
 * - GPU not released after use
 * - Scheduler making wrong decisions based on stale state
 */
class InMemoryClusterResourcesDBGPUStateTrackingTest {

    private InMemoryClusterResourcesDB createDBWithSingleGpu(String executorId) {
        val freeMemory = Map.of(0, 2048L);
        val freeCores = Map.of(0, Set.of(0, 1, 2, 3));
        val usedMemory = Map.of(0, 0L);

        val gpuInfo = new GPUInfo("GPU-0", GpuVendor.NVIDIA, "H100", 0);

        val snapshot = new ExecutorResourceSnapshot(
            executorId,
            new AvailableCPU(freeCores, Map.of()),
            new AvailableMemory(freeMemory, usedMemory),
            new AvailableGPU(List.of("GPU-0"), List.of()),
            new PhysicalLayout(freeCores, freeMemory, Map.of("GPU-0", gpuInfo))
        );

        val nodeData = new ExecutorNodeData(
            "test-host",
            8080,
            NodeTransportType.HTTP,
            new Date(),
            snapshot,
            List.of(),
            List.of(),
            List.of(),
            Set.of(),
            Map.of(),
            ExecutorState.ACTIVE
        );

        val db = new InMemoryClusterResourcesDB(new CompositeExecutorSelector(new FirstFitExecutorSelector(new GpuAwareNumaExecutorResourceAllocator()), new ScoredExecutorSelector(new GpuAwareNumaExecutorResourceAllocator(), new MaxGpuNumaLocalAllocation())));
        db.update(List.of(nodeData));
        return db;
    }

    @Test
    void testGpuTransitionFromFreeToAllocated() {
        /**
         * ISSUE: GPU state transitions may not be tracked consistently
         * 
         * SCENARIO: Allocate GPU, verify it's marked as ALLOCATED
         * EXPECTED: Controller should see GPU as ALLOCATED
         */
        
        val db = createDBWithSingleGpu("TEST-EXECUTOR");

        // Initial state: GPU should be FREE
        val node1 = db.currentSnapshot("TEST-EXECUTOR").orElse(null);
        assertNotNull(node1);
        assertEquals(ExecutorHostInfo.CoreState.FREE, 
            node1.getGpuInfo().getGpus().get("GPU-0"),
            "GPU-0 should start as FREE");

        // Allocate the GPU
        val allocationResult = db.selectNodes(
            List.of(new CPURequirement(1), new MemoryRequirement(128), 
                   new GPURequirement(1, new GpuType(GpuVendor.NVIDIA, "h100"))),
            EnumSet.of(ExecutorState.ACTIVE),
            node -> true,
            false
        );

        assertTrue(allocationResult.isPresent(), "Should successfully allocate GPU");
        
        val allocatedNode = allocationResult.get();
        assertEquals(1, allocatedNode.getGpu().getGpuIds().size());
        assertTrue(allocatedNode.getGpu().getGpuIds().contains("GPU-0"));

        // After soft lock, GPU should be ALLOCATED
        val node2 = db.currentSnapshot("TEST-EXECUTOR").orElse(null);
        assertNotNull(node2);
        assertEquals(ExecutorHostInfo.CoreState.ALLOCATED, 
            node2.getGpuInfo().getGpus().get("GPU-0"),
            "GPU-0 should be ALLOCATED after selection");
    }

    @Test
    void testGpuTransitionFromAllocatedToInUse() {
        /**
         * ISSUE: Transition from ALLOCATED to IN_USE may not be tracked
         * 
         * SCENARIO: Executor updates GPU state to IN_USE
         * EXPECTED: Controller should see GPU as IN_USE
         */
        
        val db = createDBWithSingleGpu("TEST-EXECUTOR");

        // Allocate GPU (marks as ALLOCATED)
        db.selectNodes(
            List.of(new CPURequirement(1), new MemoryRequirement(128), 
                   new GPURequirement(1, new GpuType(GpuVendor.NVIDIA, "h100"))),
            EnumSet.of(ExecutorState.ACTIVE),
            node -> true,
            false
        );

        // Simulate executor updating state to IN_USE
        val executorResourceSnapshot = new ExecutorResourceSnapshot(
            "TEST-EXECUTOR",
            new AvailableCPU(Map.of(), Map.of(0, Set.of(0))),
            new AvailableMemory(Map.of(), Map.of(0, 128L)),
            new AvailableGPU(List.of(), List.of("GPU-0")),  // GPU now in use
            new PhysicalLayout(Map.of(0, Set.of(0)), Map.of(0, 128L), 
                             Map.of("GPU-0", new GPUInfo("GPU-0", GpuVendor.NVIDIA, "H100", 0)))
        );

        db.update(executorResourceSnapshot);

        // GPU should now be IN_USE
        val node = db.currentSnapshot("TEST-EXECUTOR").orElse(null);
        assertNotNull(node);
        assertEquals(ExecutorHostInfo.CoreState.IN_USE, 
            node.getGpuInfo().getGpus().get("GPU-0"),
            "GPU-0 should be IN_USE after executor update");
    }

    @Test
    void testGpuTransitionFromInUseToFree() {
        /**
         * ISSUE: GPU release (IN_USE → FREE) may not be tracked
         * 
         * SCENARIO: Executor releases GPU
         * EXPECTED: Controller should see GPU as FREE again
         */
        
        val db = createDBWithSingleGpu("TEST-EXECUTOR");

        // Allocate GPU
        val allocationResult = db.selectNodes(
            List.of(new CPURequirement(1), new MemoryRequirement(128), 
                   new GPURequirement(1, new GpuType(GpuVendor.NVIDIA, "h100"))),
            EnumSet.of(ExecutorState.ACTIVE),
            node -> true,
            false
        );

        // Update to IN_USE
        val inUseSnapshot = new ExecutorResourceSnapshot(
            "TEST-EXECUTOR",
            new AvailableCPU(Map.of(), Map.of(0, Set.of(0))),
            new AvailableMemory(Map.of(), Map.of(0, 128L)),
            new AvailableGPU(List.of(), List.of("GPU-0")),
            new PhysicalLayout(Map.of(0, Set.of(0)), Map.of(0, 128L), 
                             Map.of("GPU-0", new GPUInfo("GPU-0", GpuVendor.NVIDIA, "H100", 0)))
        );
        db.update(inUseSnapshot);

        // Release GPU (deselect)
        val allocated = allocationResult.get();
        db.deselectNode(allocated.getExecutorId(), 
                       allocated.getCpu(), 
                       allocated.getMemory(), 
                       allocated.getGpu());

        // GPU should be back to FREE
        val node = db.currentSnapshot("TEST-EXECUTOR").orElse(null);
        assertNotNull(node);
        assertEquals(ExecutorHostInfo.CoreState.FREE, 
            node.getGpuInfo().getGpus().get("GPU-0"),
            "GPU-0 should be FREE after deselect");
    }

    @Test
    void testMultipleGpuStateConsistency() {
        /**
         * ISSUE: Multiple GPUs may have inconsistent states
         * 
         * SCENARIO: Update multiple GPUs, all should have consistent state
         */
        
        val gpuCount = 4;
        Map<String, Integer> gpuMap = new LinkedHashMap<>();
        for (int i = 0; i < gpuCount; i++) {
            gpuMap.put("GPU-" + i, i);
        }

        val db = createDBWithGpus("TEST-EXECUTOR", gpuMap);

        // Allocate all GPUs
        val allocationResult = db.selectNodes(
            List.of(new CPURequirement(1), new MemoryRequirement(128), 
                   new GPURequirement(gpuCount, new GpuType(GpuVendor.NVIDIA, "h100"))),
            EnumSet.of(ExecutorState.ACTIVE),
            node -> true,
            false
        );

        assertTrue(allocationResult.isPresent(), "Should allocate all GPUs");
        assertEquals(gpuCount, allocationResult.get().getGpu().getGpuIds().size());

        // All GPUs should be ALLOCATED
        val node1 = db.currentSnapshot("TEST-EXECUTOR").orElse(null);
        assertNotNull(node1);

        for (int i = 0; i < gpuCount; i++) {
            String gpuId = "GPU-" + i;
            val state = node1.getGpuInfo().getGpus().get(gpuId);
            assertEquals(ExecutorHostInfo.CoreState.ALLOCATED, state,
                "GPU " + gpuId + " should be ALLOCATED");
        }

        // Update all to IN_USE
        val inUseGpus = new ArrayList<>(gpuMap.keySet());
        val inUseSnapshot = new ExecutorResourceSnapshot(
            "TEST-EXECUTOR",
            new AvailableCPU(Map.of(), Map.of(0, Set.of(0))),
            new AvailableMemory(Map.of(), Map.of(0, 128L)),
            new AvailableGPU(List.of(), inUseGpus),
            new PhysicalLayout(Map.of(0, Set.of(0)), Map.of(0, 128L),
                             gpuMap.keySet().stream()
                                 .collect(Collectors.toUnmodifiableMap(
                                     id -> id,
                                     id -> new GPUInfo(id, GpuVendor.NVIDIA, "H100", 
                                                       gpuMap.get(id)))))
        );
        db.update(inUseSnapshot);

        // All GPUs should be IN_USE
        val node2 = db.currentSnapshot("TEST-EXECUTOR").orElse(null);
        assertNotNull(node2);

        for (int i = 0; i < gpuCount; i++) {
            String gpuId = "GPU-" + i;
            val state = node2.getGpuInfo().getGpus().get(gpuId);
            assertEquals(ExecutorHostInfo.CoreState.IN_USE, state,
                "GPU " + gpuId + " should be IN_USE");
        }
    }

    @Test
    void testConcurrentGpuStateUpdates() throws InterruptedException {
        /**
         * ISSUE: Concurrent updates may cause inconsistent state
         * 
         * SCENARIO: Multiple threads update GPU states simultaneously
         * EXPECTED: Final state should be consistent
         */
        
        val db = createDBWithSingleGpu("TEST-EXECUTOR");

        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(100);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < 100; i++) {
            executor.submit(() -> {
                try {
                    // Try to update GPU state
                    val currentSnapshot = db.currentSnapshot("TEST-EXECUTOR").orElse(null);
                    if (currentSnapshot != null) {
                        db.update(List.of(currentSnapshot.getNodeData()));
                    }
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // Expected
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        // Verify state is still consistent
        val node = db.currentSnapshot("TEST-EXECUTOR").orElse(null);
        assertNotNull(node);

        val gpuState = node.getGpuInfo().getGpus().get("GPU-0");
        assertNotNull(gpuState, "GPU state should not be null");
        assertTrue(gpuState == ExecutorHostInfo.CoreState.FREE ||
                  gpuState == ExecutorHostInfo.CoreState.ALLOCATED ||
                  gpuState == ExecutorHostInfo.CoreState.IN_USE,
                  "GPU state should be valid enum value");
    }

    @Test
    void testExecutorAndControllerStateSynchronization() {
        /**
         * ISSUE: Executor's view of GPU state may differ from controller's
         * 
         * SCENARIO: Simulate executor-controller state sync
         * EXPECTED: Both should have consistent view after sync
         */
        
        val db = createDBWithSingleGpu("TEST-EXECUTOR");

        // Executor action 1: Allocate GPU
        val allocation = db.selectNodes(
            List.of(new CPURequirement(1), new MemoryRequirement(128), 
                   new GPURequirement(1, new GpuType(GpuVendor.NVIDIA, "h100"))),
            EnumSet.of(ExecutorState.ACTIVE),
            node -> true,
            false
        );

        assertTrue(allocation.isPresent());
        
        // Controller's view: should show ALLOCATED
        val controllerView1 = db.currentSnapshot("TEST-EXECUTOR").orElse(null);
        assertEquals(ExecutorHostInfo.CoreState.ALLOCATED,
            controllerView1.getGpuInfo().getGpus().get("GPU-0"));

        // Executor action 2: Update to IN_USE
        val executorUpdate = new ExecutorResourceSnapshot(
            "TEST-EXECUTOR",
            new AvailableCPU(Map.of(), Map.of(0, Set.of(0))),
            new AvailableMemory(Map.of(), Map.of(0, 128L)),
            new AvailableGPU(List.of(), List.of("GPU-0")),
            new PhysicalLayout(Map.of(0, Set.of(0)), Map.of(0, 128L), 
                             Map.of("GPU-0", new GPUInfo("GPU-0", GpuVendor.NVIDIA, "H100", 0)))
        );
        db.update(executorUpdate);

        // Controller's view: should show IN_USE
        val controllerView2 = db.currentSnapshot("TEST-EXECUTOR").orElse(null);
        assertEquals(ExecutorHostInfo.CoreState.IN_USE,
            controllerView2.getGpuInfo().getGpus().get("GPU-0"));

        // Executor action 3: Release GPU
        db.deselectNode("TEST-EXECUTOR", 
                       allocation.get().getCpu(), 
                       allocation.get().getMemory(), 
                       allocation.get().getGpu());

        // Controller's view: should show FREE
        val controllerView3 = db.currentSnapshot("TEST-EXECUTOR").orElse(null);
        assertEquals(ExecutorHostInfo.CoreState.FREE,
            controllerView3.getGpuInfo().getGpus().get("GPU-0"));
    }

    private InMemoryClusterResourcesDB createDBWithGpus(String executorId, 
                                                        Map<String, Integer> gpuNumaAffinities) {
        val freeMemory = Map.of(0, 2048L);
        val freeCores = Map.of(0, Set.of(0, 1, 2, 3));
        val usedMemory = Map.of(0, 0L);

        val physicalGpus = gpuNumaAffinities.entrySet().stream()
            .collect(Collectors.toUnmodifiableMap(
                Map.Entry::getKey,
                entry -> new GPUInfo(entry.getKey(), GpuVendor.NVIDIA, "H100", 
                                     entry.getValue())));

        val freeGpuIds = new ArrayList<>(gpuNumaAffinities.keySet());

        val snapshot = new ExecutorResourceSnapshot(
            executorId,
            new AvailableCPU(freeCores, Map.of()),
            new AvailableMemory(freeMemory, usedMemory),
            new AvailableGPU(freeGpuIds, List.of()),
            new PhysicalLayout(freeCores, freeMemory, physicalGpus)
        );

        val nodeData = new ExecutorNodeData(
            "test-host",
            8080,
            NodeTransportType.HTTP,
            new Date(),
            snapshot,
            List.of(),
            List.of(),
            List.of(),
            Set.of(),
            Map.of(),
            ExecutorState.ACTIVE
        );

        val db = new InMemoryClusterResourcesDB(new CompositeExecutorSelector(new FirstFitExecutorSelector(new GpuAwareNumaExecutorResourceAllocator()), new ScoredExecutorSelector(new GpuAwareNumaExecutorResourceAllocator(), new MaxGpuNumaLocalAllocation())));
        db.update(List.of(nodeData));
        return db;
    }
}
