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

import com.phonepe.drove.auth.config.ClusterAuthenticationConfig;
import com.phonepe.drove.auth.model.DroveClusterNode;
import com.phonepe.drove.models.info.nodedata.NodeType;
import lombok.val;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 *
 */
class DroveClusterSecretAuthenticatorTest {

    @Test
    void test() {

        val authenticator = new DroveClusterSecretAuthenticator(ClusterAuthenticationConfig.DEFAULT);
        assertEquals(NodeType.CONTROLLER,
                     authenticator.authenticate(new ClusterCredentials("c1", "DefaultControllerSecret"))
                             .map(u -> ((DroveClusterNode) u).getNodeType())
                             .orElse(null));
        assertEquals(NodeType.EXECUTOR,
                     authenticator.authenticate(new ClusterCredentials("e1", "DefaultExecutorSecret"))
                             .map(u -> ((DroveClusterNode) u).getNodeType())
                             .orElse(null));
        assertTrue(authenticator.authenticate(new ClusterCredentials("e2", "WrongExecutorSecret")).isEmpty());
    }

}