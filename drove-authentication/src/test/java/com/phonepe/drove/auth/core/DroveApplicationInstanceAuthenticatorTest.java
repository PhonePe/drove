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

package com.phonepe.drove.auth.core;

import com.phonepe.drove.auth.model.DroveApplicationInstanceInfo;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 *
 */
class DroveApplicationInstanceAuthenticatorTest {

    @Test
    @SneakyThrows
    void testSuccess() {
        val mgr = mock(ApplicationInstanceTokenManager.class);
        when(mgr.verify(anyString()))
                .thenReturn(Optional.of(new DroveApplicationInstanceInfo("app1",
                                                                         "instance1",
                                                                         "ex1")));

        val auth = new DroveApplicationInstanceAuthenticator(mgr);
        assertTrue(auth.authenticate("Test").isPresent());
    }

    @Test
    @SneakyThrows
    void testFailure() {
        val mgr = mock(ApplicationInstanceTokenManager.class);
        when(mgr.verify(anyString())).thenReturn(Optional.empty());

        val auth = new DroveApplicationInstanceAuthenticator(mgr);
        assertTrue(auth.authenticate("Test").isEmpty());
    }

}