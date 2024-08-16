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

package com.phonepe.drove.auth.core;

import com.phonepe.drove.auth.config.ApplicationAuthConfig;
import com.phonepe.drove.auth.model.DroveApplicationInstanceInfo;
import lombok.val;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 *
 */
class JWTApplicationInstanceTokenManagerTest {

    @Test
    void test() {
        val mgr = new JWTApplicationInstanceTokenManager(new ApplicationAuthConfig("test-secret"));
        val info = new DroveApplicationInstanceInfo("test_app", "inst1", "exec1");
        val token = mgr.generate(info).orElse(null);
        assertNotNull(token);
        val retrieved = mgr.verify(token).orElse(null);
        assertNotNull(retrieved);
        assertEquals(info, retrieved);

        assertNull(mgr.verify("WrongToken").orElse(null));
    }

}