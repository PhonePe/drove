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

package com.phonepe.drove.controller.utils;

import dev.failsafe.RetryPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 *
 */
class ControllerUtilsTest {
    @Test
    void waitForStateSuccess() {
        assertEquals(StateCheckStatus.MATCH,
                     ControllerUtils.waitForState(
                             () -> StateCheckStatus.MATCH,
                             RetryPolicy.<StateCheckStatus>builder().withMaxAttempts(1).build()));
    }

    @Test
    void waitForStateFailure() {
        assertThrows(RuntimeException.class,
                     () -> ControllerUtils.waitForState(
                             () -> {
                                 throw new RuntimeException("Test");
                             },
                             RetryPolicy.<StateCheckStatus>builder().withMaxAttempts(1).build()));
    }
}