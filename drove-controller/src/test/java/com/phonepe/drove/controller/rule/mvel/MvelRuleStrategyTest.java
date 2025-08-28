/*
 *  Copyright (c) 2025 Original Author(s), PhonePe India Pvt. Ltd.
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

package com.phonepe.drove.controller.rule.mvel;

import com.phonepe.drove.controller.rule.TestDataNode;
import com.phonepe.drove.models.operation.rule.RuleCallStatus;
import lombok.val;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests {@link MvelRuleStrategy}
 */
class MvelRuleStrategyTest {

    @Test
    void test() {
        val rs = new MvelRuleStrategy(0);
        assertEquals(RuleCallStatus.SUCCESS, rs.check("1 == 1").getStatus());
        assertEquals(RuleCallStatus.FAILURE, rs.check("ab cd").getStatus());
        assertTrue(rs.evaluate("data == 'SS'", new TestDataNode("SS")).isResult());
        assertFalse(rs.evaluate("data == 'XX'", new TestDataNode("SS")).isResult());
        assertFalse(rs.evaluate("data == 'SS'", new TestDataNode(null)).isResult());
        assertEquals(RuleCallStatus.FAILURE, rs.evaluate("'ab cd", new TestDataNode("SS")).getStatus());
    }
}