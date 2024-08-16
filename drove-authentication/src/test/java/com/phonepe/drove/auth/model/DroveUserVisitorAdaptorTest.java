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

package com.phonepe.drove.auth.model;

import com.phonepe.drove.models.info.nodedata.NodeType;
import lombok.val;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 *
 */
class DroveUserVisitorAdaptorTest {

    @Test
    void test() {
        val visitor = new DroveUserVisitorAdaptor<>(false) {};
        assertFalse(new DroveClusterNode("test", NodeType.EXECUTOR).accept(visitor));
        assertFalse(new DroveApplicationInstance("test", null).accept(visitor));
        assertFalse(new DroveExternalUser("test", DroveUserRole.EXTERNAL_READ_WRITE, null).accept(visitor));
    }

}