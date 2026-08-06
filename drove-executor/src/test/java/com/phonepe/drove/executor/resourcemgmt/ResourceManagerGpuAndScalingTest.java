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

package com.phonepe.drove.executor.resourcemgmt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.phonepe.drove.common.CommonTestUtils;
import com.phonepe.drove.models.application.requirements.GpuVendor;
import com.phonepe.drove.models.info.GPUInfo;
import com.phonepe.drove.models.info.resources.PhysicalLayout;

import org.junit.jupiter.api.Test;

import lombok.val;

class ResourceManagerGpuAndScalingTest {

    private static final GPUInfo GPU_0 =
            new GPUInfo("GPU-0", GpuVendor.NVIDIA, "H100", 0);
    private static final GPUInfo GPU_1 =
            new GPUInfo("GPU-1", GpuVendor.NVIDIA, "H100", 1);
    private static final GPUInfo GPU_2 =
            new GPUInfo("GPU-2", GpuVendor.AMD, "MI300X", 0);

    private static Set<Integer> cores(int max) {
        return IntStream.rangeClosed(0, max).boxed().collect(Collectors.toUnmodifiableSet());
    }

    private ResourceManager defaultRm() {
        return new ResourceManager(PerformanceScalingConfig.DEFAULT);
    }

    private void populate(ResourceManager rm, Set<GPUInfo> gpus) {
        rm.populateResources(new ResourceManager.ExecutorResources(
                Map.of(0, ResourceManager.NodeInfo.from(cores(19), 65536)),
                gpus));
    }

    @Test
    void gpuLockSucceedsWhenAvailable() {
        val rm = defaultRm();
        populate(rm, Set.of(GPU_0, GPU_1));

        val locked = rm.lockResources(new ResourceManager.ResourceUsage(
                "app-1", ResourceManager.ResourceLockType.HARD,
                Map.of(0, ResourceManager.NodeInfo.from(Set.of(0, 1), 128)),
                Set.of("GPU-0")));

        assertTrue(locked);
        val state = rm.currentState();
        assertTrue(state.getGpuInfo().getFreeGpus().contains("GPU-1"));
        assertFalse(state.getGpuInfo().getFreeGpus().contains("GPU-0"));
        assertTrue(state.getGpuInfo().getUsedGpus().contains("GPU-0"));
    }

    @Test
    void gpuLockFailsWhenAlreadyAllocated() {
        val rm = defaultRm();
        populate(rm, Set.of(GPU_0, GPU_1));

        assertTrue(rm.lockResources(new ResourceManager.ResourceUsage(
                "app-1", ResourceManager.ResourceLockType.HARD,
                Map.of(0, ResourceManager.NodeInfo.from(Set.of(0), 128)),
                Set.of("GPU-0"))));

        val secondLock = rm.lockResources(new ResourceManager.ResourceUsage(
                "app-2", ResourceManager.ResourceLockType.HARD,
                Map.of(0, ResourceManager.NodeInfo.from(Set.of(1), 128)),
                Set.of("GPU-0")));

        assertFalse(secondLock);
    }

    @Test
    void gpuReclaimRestoresAvailability() {
        val rm = defaultRm();
        populate(rm, Set.of(GPU_0, GPU_1));

        assertTrue(rm.lockResources(new ResourceManager.ResourceUsage(
                "app-1", ResourceManager.ResourceLockType.HARD,
                Map.of(0, ResourceManager.NodeInfo.from(Set.of(0), 128)),
                Set.of("GPU-0"))));

        assertTrue(rm.reclaimResources("app-1"));

        val state = rm.currentState();
        assertTrue(state.getGpuInfo().getFreeGpus().contains("GPU-0"));
        assertFalse(state.getGpuInfo().getUsedGpus().contains("GPU-0"));
    }

    @Test
    void gpuLockFailsForUnknownId() {
        val rm = defaultRm();
        populate(rm, Set.of(GPU_0));

        val locked = rm.lockResources(new ResourceManager.ResourceUsage(
                "app-1", ResourceManager.ResourceLockType.HARD,
                Map.of(0, ResourceManager.NodeInfo.from(Set.of(0), 128)),
                Set.of("GPU-999")));

        assertFalse(locked);
    }

    @Test
    void multipleGpusAllocatedAndReclaimedIndependently() {
        val rm = defaultRm();
        populate(rm, Set.of(GPU_0, GPU_1, GPU_2));

        assertTrue(rm.lockResources(new ResourceManager.ResourceUsage(
                "app-1", ResourceManager.ResourceLockType.HARD,
                Map.of(0, ResourceManager.NodeInfo.from(Set.of(0), 128)),
                Set.of("GPU-0", "GPU-1"))));

        assertTrue(rm.lockResources(new ResourceManager.ResourceUsage(
                "app-2", ResourceManager.ResourceLockType.HARD,
                Map.of(0, ResourceManager.NodeInfo.from(Set.of(1), 128)),
                Set.of("GPU-2"))));

        assertEquals(0, rm.currentState().getGpuInfo().getFreeGpus().size());

        assertTrue(rm.reclaimResources("app-1"));
        val stateAfterReclaim = rm.currentState();
        assertEquals(2, stateAfterReclaim.getGpuInfo().getFreeGpus().size());
        assertTrue(stateAfterReclaim.getGpuInfo().getUsedGpus().contains("GPU-2"));
    }

    @Test
    void cpuAndGpuLockSucceedTogether() {
        val rm = defaultRm();
        populate(rm, Set.of(GPU_0));

        val locked = rm.lockResources(new ResourceManager.ResourceUsage(
                "app-1", ResourceManager.ResourceLockType.HARD,
                Map.of(0, ResourceManager.NodeInfo.from(Set.of(0, 1, 2), 256)),
                Set.of("GPU-0")));

        assertTrue(locked);
        val state = rm.currentState();
        assertFalse(state.getCpu().getFreeCores().get(0).contains(0));
        assertFalse(state.getGpuInfo().getFreeGpus().contains("GPU-0"));
    }

    @Test
    void cpuAndGpuReclaimRestoresBoth() {
        val rm = defaultRm();
        populate(rm, Set.of(GPU_0));

        assertTrue(rm.lockResources(new ResourceManager.ResourceUsage(
                "app-1", ResourceManager.ResourceLockType.HARD,
                Map.of(0, ResourceManager.NodeInfo.from(Set.of(0, 1), 256)),
                Set.of("GPU-0"))));

        assertTrue(rm.reclaimResources("app-1"));

        val state = rm.currentState();
        assertTrue(state.getCpu().getFreeCores().get(0).contains(0));
        assertTrue(state.getGpuInfo().getFreeGpus().contains("GPU-0"));
    }

    @Test
    void scalingFactorsPropagatedToPhysicalLayout() {
        val scaling = PerformanceScalingConfig.builder()
                .cpuScalingFactor(2.0)
                .memoryScalingFactor(1.5)
                .build();

        val rm = new ResourceManager(scaling);
        populate(rm, Set.of(GPU_0));

        val layout = rm.currentState().getPhysicalLayout();
        assertNotNull(layout);
        assertEquals(2.0, layout.getCpuScalingFactor(), 0.001);
        assertEquals(1.5, layout.getMemoryScalingFactor(), 0.001);
    }

    @Test
    void defaultScalingFactorsPropagated() {
        val rm = new ResourceManager(PerformanceScalingConfig.DEFAULT);
        populate(rm, Set.of(GPU_0));

        val layout = rm.currentState().getPhysicalLayout();
        assertEquals(PhysicalLayout.DEFAULT_CPU_SCALING_FACTOR, layout.getCpuScalingFactor(), 0.001);
        assertEquals(PhysicalLayout.DEFAULT_MEMORY_SCALING_FACTOR, layout.getMemoryScalingFactor(), 0.001);
    }

    @Test
    void nullScalingFactorsNormalizeToDefaults() {
        val scaling = new PerformanceScalingConfig(null, null);
        val rm = new ResourceManager(scaling);
        populate(rm, Set.of(GPU_0));

        val layout = rm.currentState().getPhysicalLayout();
        assertEquals(PhysicalLayout.DEFAULT_CPU_SCALING_FACTOR, layout.getCpuScalingFactor(), 0.001);
        assertEquals(PhysicalLayout.DEFAULT_MEMORY_SCALING_FACTOR, layout.getMemoryScalingFactor(), 0.001);
    }

    @Test
    void gpuInfoPopulatedInPhysicalLayout() {
        val rm = defaultRm();
        populate(rm, Set.of(GPU_0, GPU_1));

        val layout = rm.currentState().getPhysicalLayout();
        assertNotNull(layout.getGpus());
        assertEquals(2, layout.getGpus().size());
        assertNotNull(layout.getGpus().get("GPU-0"));
        assertEquals(GpuVendor.NVIDIA, layout.getGpus().get("GPU-0").getGpuVendor());
    }

    @Test
    void noGpusYieldsEmptyMapInPhysicalLayout() {
        val rm = defaultRm();
        populate(rm, Set.of());

        assertTrue(rm.currentState().getPhysicalLayout().getGpus().isEmpty());
    }

    @Test
    void allGpusFreeInitially() {
        val rm = defaultRm();
        populate(rm, Set.of(GPU_0, GPU_1, GPU_2));

        val state = rm.currentState();
        assertNotNull(state.getGpuInfo());
        assertEquals(3, state.getGpuInfo().getFreeGpus().size());
        assertTrue(state.getGpuInfo().getUsedGpus().isEmpty());
    }

    @Test
    void gpuTrackingAfterLockAndReclaim() {
        val rm = defaultRm();
        populate(rm, Set.of(GPU_0, GPU_1));

        rm.lockResources(new ResourceManager.ResourceUsage(
                "app-1", ResourceManager.ResourceLockType.HARD,
                Map.of(0, ResourceManager.NodeInfo.from(Set.of(0), 128)),
                Set.of("GPU-0")));

        val stateAfterLock = rm.currentState();
        assertEquals(1, stateAfterLock.getGpuInfo().getFreeGpus().size());
        assertEquals(1, stateAfterLock.getGpuInfo().getUsedGpus().size());

        rm.reclaimResources("app-1");

        val stateAfterReclaim = rm.currentState();
        assertEquals(2, stateAfterReclaim.getGpuInfo().getFreeGpus().size());
        assertTrue(stateAfterReclaim.getGpuInfo().getUsedGpus().isEmpty());
    }

    @Test
    void resourceUpdatedSignalFiresOnGpuLock() {
        val rm = defaultRm();
        populate(rm, Set.of(GPU_0));

        val signalCount = new java.util.concurrent.atomic.AtomicInteger(0);
        rm.onResourceUpdated().connect(info -> signalCount.incrementAndGet());

        rm.lockResources(new ResourceManager.ResourceUsage(
                "app-1", ResourceManager.ResourceLockType.HARD,
                Map.of(0, ResourceManager.NodeInfo.from(Set.of(0), 128)),
                Set.of("GPU-0")));

        CommonTestUtils.waitUntil(() -> signalCount.get() >= 1);
        assertTrue(signalCount.get() >= 1);
    }

    @Test
    void resourceUpdatedSignalFiresOnGpuReclaim() {
        val rm = defaultRm();
        populate(rm, Set.of(GPU_0));

        rm.lockResources(new ResourceManager.ResourceUsage(
                "app-1", ResourceManager.ResourceLockType.HARD,
                Map.of(0, ResourceManager.NodeInfo.from(Set.of(0), 128)),
                Set.of("GPU-0")));

        val signalCount = new java.util.concurrent.atomic.AtomicInteger(0);
        rm.onResourceUpdated().connect(info -> signalCount.incrementAndGet());

        rm.reclaimResources("app-1");

        CommonTestUtils.waitUntil(() -> signalCount.get() >= 1);
        assertTrue(signalCount.get() >= 1);
    }
}
