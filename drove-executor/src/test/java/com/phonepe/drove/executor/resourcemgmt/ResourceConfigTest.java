/*
 *  Copyright (c) 2022 Original Author(s), PhonePe India Pvt. Ltd.
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

import org.junit.jupiter.api.Test;


import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class ResourceConfigTest {


    @Test
    void validateDefaultValuesAreGeneratedForResourceConfig() {
        var resourceConfig = new ResourceConfig();
        assertEquals(Collections.emptySet(), resourceConfig.getOsCores());
        assertEquals(100, resourceConfig.getExposedMemPercentage());
        assertFalse(resourceConfig.isDisableNUMAPinning());
        assertEquals(Collections.emptySet(), resourceConfig.getTags());
        assertFalse(resourceConfig.getOverProvisioning().isEnabled());
    }

}