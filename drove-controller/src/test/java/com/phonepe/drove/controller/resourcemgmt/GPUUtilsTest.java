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

import com.phonepe.drove.models.application.requirements.GpuType;
import com.phonepe.drove.models.application.requirements.GpuVendor;
import com.phonepe.drove.models.info.GPUInfo;
import lombok.val;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GPUUtilsTest {

    @Test
    void testNullRequestedTypeMatchesAnyGpu() {
        val gpu = new GPUInfo("GPU-1", GpuVendor.NVIDIA, "H100", 0);
        assertTrue(GPUUtils.matches(gpu, null));
    }

    @Test
    void testNullGpuInfoDoesNotMatch() {
        val requested = new GpuType(GpuVendor.NVIDIA, "H100");
        assertFalse(GPUUtils.matches(null, requested));
    }

    @Test
    void testVendorMismatchDoesNotMatch() {
        val gpu = new GPUInfo("GPU-1", GpuVendor.NVIDIA, "H100", 0);
        val requested = new GpuType(GpuVendor.AMD, "H100");
        assertFalse(GPUUtils.matches(gpu, requested));
    }

    @Test
    void testExactModelMatch() {
        val gpu = new GPUInfo("GPU-1", GpuVendor.NVIDIA, "H100", 0);
        val requested = new GpuType(GpuVendor.NVIDIA, "H100");
        assertTrue(GPUUtils.matches(gpu, requested));
    }




    @Test
    void testNonAlphanumericCharactersNormalized() {
        val gpu = new GPUInfo("GPU-1", GpuVendor.AMD, "MI300X", 0);
        val requested = new GpuType(GpuVendor.AMD, "mi300x");
        assertTrue(GPUUtils.matches(gpu, requested));
    }

    @Test
    void testModelTokenNotFoundDoesNotMatch() {
        val gpu = new GPUInfo("GPU-1", GpuVendor.NVIDIA, "H100", 0);
        val requested = new GpuType(GpuVendor.NVIDIA, "H200");
        assertFalse(GPUUtils.matches(gpu, requested));
    }

    @Test
    void testEmptyGpuModelDoesNotMatch() {
        val gpu = new GPUInfo("GPU-1", GpuVendor.NVIDIA, "", 0);
        val requested = new GpuType(GpuVendor.NVIDIA, "H100");
        assertFalse(GPUUtils.matches(gpu, requested));
    }

    @Test
    void testMultiTokenRequestFailsIfOneTokenMissing() {
        val gpu = new GPUInfo("GPU-1", GpuVendor.NVIDIA, "NVIDIA H100 80GB HBM3", 0);
        // "H100 HBM4" → "HBM4" does not appear in the model
        val requested = new GpuType(GpuVendor.NVIDIA, "H100 HBM4");
        assertFalse(GPUUtils.matches(gpu, requested));
    }

    @Test
    void testBothNullReturnsTrueForNullType() {
        assertTrue(GPUUtils.matches(null, null));
    }
}

