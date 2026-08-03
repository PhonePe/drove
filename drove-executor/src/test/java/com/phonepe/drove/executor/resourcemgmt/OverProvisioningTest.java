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
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import lombok.val;

class OverProvisioningTest {

    @Test
    void defaultValues() {
        val op = OverProvisioning.DEFAULT;
        assertFalse(op.isEnabled());
        assertEquals(OverProvisioning.DEFAULT_MULTIPLIER, op.getCpuMultiplier());
        assertEquals(OverProvisioning.DEFAULT_MULTIPLIER, op.getMemoryMultiplier());
    }

    @Test
    void builderProducesExpectedValues() {
        val op = OverProvisioning.builder()
                .enabled(true)
                .cpuMultiplier(3)
                .memoryMultiplier(5)
                .build();
        assertTrue(op.isEnabled());
        assertEquals(3, op.getCpuMultiplier());
        assertEquals(5, op.getMemoryMultiplier());
    }

    @Test
    void withMethodsProduceNewInstances() {
        val original = OverProvisioning.DEFAULT;
        val modified = original.withEnabled(true)
                              .withCpuMultiplier(4)
                              .withMemoryMultiplier(2);
        assertNotSame(original, modified);
        assertFalse(original.isEnabled());
        assertEquals(OverProvisioning.DEFAULT_MULTIPLIER, original.getCpuMultiplier());
        assertTrue(modified.isEnabled());
        assertEquals(4, modified.getCpuMultiplier());
        assertEquals(2, modified.getMemoryMultiplier());
    }

    @Test
    void allArgsConstructorMatchesBuilder() {
        val fromConstructor = new OverProvisioning(true, 3, 5);
        val fromBuilder = OverProvisioning.builder()
                .enabled(true)
                .cpuMultiplier(3)
                .memoryMultiplier(5)
                .build();
        assertEquals(fromConstructor, fromBuilder);
    }

    @Test
    void noArgsConstructorProducesDefaults() {
        val op = new OverProvisioning();
        assertFalse(op.isEnabled());
        assertEquals(OverProvisioning.DEFAULT_MULTIPLIER, op.getCpuMultiplier());
        assertEquals(OverProvisioning.DEFAULT_MULTIPLIER, op.getMemoryMultiplier());
    }
}
