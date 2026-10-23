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

package com.phonepe.drove.executor.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * Tests command splitting in {@link DockerUtils}. Does not need a docker daemon.
 */
class DockerUtilsCommandSplitTest {

    @Test
    void testSplitCommandSingleSpaces() {
        assertArrayEquals(new String[]{"curl", "-f", "http://localhost:8080/ping"},
                          DockerUtils.splitCommand("curl -f http://localhost:8080/ping"));
    }

    @Test
    void testSplitCommandRepeatedSpaces() {
        assertArrayEquals(new String[]{"curl", "-f", "http://localhost:8080/ping"},
                          DockerUtils.splitCommand("curl  -f   http://localhost:8080/ping"));
    }

    @Test
    void testSplitCommandLeadingAndTrailingSpaces() {
        assertArrayEquals(new String[]{"cat", "/etc/hosts"},
                          DockerUtils.splitCommand("  cat /etc/hosts  "));
    }

    @Test
    void testSplitCommandNoArguments() {
        assertArrayEquals(new String[]{"hostname"}, DockerUtils.splitCommand("hostname"));
    }
}
