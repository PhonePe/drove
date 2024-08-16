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

import com.google.common.collect.Sets;
import com.phonepe.drove.common.AbstractTestBase;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import static com.phonepe.drove.common.CommonTestUtils.set;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 */
class ResourceManagerTest extends AbstractTestBase {

    @Test
    void testBasicResourceMgmt() {
        val rm = new ResourceManager();
        rm.populateResources(Map.of(
                0, ResourceManager.NodeInfo.from(set(19), 2 ^ 16),
                1, ResourceManager.NodeInfo.from(set(19), 2 ^ 16)));
        testZeroState(rm.currentState());

        assertTrue(rm.lockResources(
                new ResourceManager.ResourceUsage(
                        "test-usage", ResourceManager.ResourceLockType.HARD,
                        Map.of(1, ResourceManager.NodeInfo.from(set(3, 12), 2 ^ 10)))));
        assertFalse(rm.lockResources(
                new ResourceManager.ResourceUsage(
                        "test-usage", ResourceManager.ResourceLockType.HARD,
                        Map.of(1, ResourceManager.NodeInfo.from(set(3, 12), 2 ^ 10)))));
        assertFalse(rm.lockResources(
                new ResourceManager.ResourceUsage(
                        "test-usage-fail", ResourceManager.ResourceLockType.HARD,
                        Map.of(1, ResourceManager.NodeInfo.from(set(3, 12), 2 ^ 10)))));
        assertFalse(rm.lockResources(
                new ResourceManager.ResourceUsage(
                        "test-usage-fail-2", ResourceManager.ResourceLockType.HARD,
                        Map.of(2, ResourceManager.NodeInfo.from(set(3, 12), 2 ^ 10)))));
        assertFalse(rm.lockResources(
                new ResourceManager.ResourceUsage(
                        "test-usage-fail-3", ResourceManager.ResourceLockType.HARD,
                        Map.of(0, ResourceManager.NodeInfo.from(set(3, 33), 2 ^ 10)))));
        testAllocatedState(rm.currentState());
        assertTrue(rm.reclaimResources("test-usage"));
        assertFalse(rm.reclaimResources("test-usage-fail"));
        testZeroState(rm.currentState());
    }


    @Test
    void testSignal() {
        val rm = new ResourceManager();
        rm.populateResources(Map.of(
                0, ResourceManager.NodeInfo.from(set(19), 2 ^ 16),
                1, ResourceManager.NodeInfo.from(set(19), 2 ^ 16)));
        testZeroState(rm.currentState());
        rm.onResourceUpdated().connect(this::testAllocatedState);
        assertTrue(rm.lockResources(
                new ResourceManager.ResourceUsage(
                        "test-usage", ResourceManager.ResourceLockType.HARD,
                        Map.of(1, ResourceManager.NodeInfo.from(set(3, 12), 2 ^ 10)))));
    }

    @Test
    void testExhaustionReclaim() {
        val rm = new ResourceManager();
        rm.populateResources(Map.of(
                0, ResourceManager.NodeInfo.from(set(19), 2 ^ 16),
                1, ResourceManager.NodeInfo.from(set(19), 2 ^ 16)));
        testZeroState(rm.currentState());
        IntStream.range(0, 20)
                .forEach(i -> assertTrue(rm.lockResources(
                        new ResourceManager.ResourceUsage(
                                "test-usage-" + i, ResourceManager.ResourceLockType.HARD,
                                Map.of(0, ResourceManager.NodeInfo.from(Set.of(i), (2 ^ 16) / 20))))));
        assertFalse(rm.lockResources(
                new ResourceManager.ResourceUsage(
                        "test-usage-fail", ResourceManager.ResourceLockType.HARD,
                        Map.of(0, ResourceManager.NodeInfo.from(set(2,9), (2 ^ 16) / 20)))));
        IntStream.range(5, 10)
                .forEach(i -> rm.reclaimResources("test-usage-" + i));
        IntStream.range(5, 10)
                .forEach(i -> assertTrue(rm.lockResources(
                        new ResourceManager.ResourceUsage(
                                "test-usage-at2-" + i, ResourceManager.ResourceLockType.HARD,
                                Map.of(0, ResourceManager.NodeInfo.from(Set.of(i), (2 ^ 16) / 20))))));


    }

    @Test
    void testMultiNodeAllocation() {
        val rm = new ResourceManager();
        rm.populateResources(Map.of(
                0, ResourceManager.NodeInfo.from(set(19), 2 ^ 16),
                1, ResourceManager.NodeInfo.from(set(19), 2 ^ 16)));
        testZeroState(rm.currentState());
        assertTrue(rm.lockResources(
                new ResourceManager.ResourceUsage(
                        "test-usage", ResourceManager.ResourceLockType.HARD,
                        Map.of(0, ResourceManager.NodeInfo.from(set(3, 12), 2 ^ 10),
                               1, ResourceManager.NodeInfo.from(set(3, 12), 2 ^ 10)))));
        val info = rm.currentState();
        val freeCores = info.getCpu().getFreeCores();
        assertFalse(freeCores.isEmpty());
        assertEquals(10, freeCores.get(0).size());
        assertTrue(Sets.difference(Sets.difference(set(19), freeCores.get(0)), set(3, 12)).isEmpty());
        assertEquals(10, freeCores.get(1).size());
        assertTrue(Sets.difference(Sets.difference(set(19), freeCores.get(1)), set(3, 12)).isEmpty());

        val freeMemory = info.getMemory().getFreeMemory();
        assertEquals(2 ^ 16 - (2 ^ 10), freeMemory.get(0));
        assertEquals(2 ^ 16 - (2 ^ 10), freeMemory.get(1));

        rm.reclaimResources("test-usage");
        testZeroState(rm.currentState());
    }

    private void testZeroState(final ResourceInfo info) {
        val freeCores = info.getCpu().getFreeCores();
        assertFalse(freeCores.isEmpty());
        assertTrue(Sets.difference(freeCores.get(0), set(19)).isEmpty());
        assertTrue(Sets.difference(freeCores.get(1), set(19)).isEmpty());

        val freeMemory = info.getMemory().getFreeMemory();
        assertEquals(2 ^ 16, freeMemory.get(0));
        assertEquals(2 ^ 16, freeMemory.get(1));
    }


    private void testAllocatedState(ResourceInfo info) {
        val freeCores = info.getCpu().getFreeCores();
        assertFalse(freeCores.isEmpty());
        assertTrue(Sets.difference(freeCores.get(0), set(19)).isEmpty());
        assertEquals(10, freeCores.get(1).size());
        assertTrue(Sets.difference(Sets.difference(set(19), freeCores.get(1)), set(3, 12)).isEmpty());

        val freeMemory = info.getMemory().getFreeMemory();
        assertEquals(2 ^ 16, freeMemory.get(0));
        assertEquals(2 ^ 16 - (2 ^ 10), freeMemory.get(1));
    }
}