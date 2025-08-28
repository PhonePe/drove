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

package com.phonepe.drove.controller.rule.hope;

import com.phonepe.drove.common.AbstractTestBase;
import com.phonepe.drove.controller.rule.TestDataNode;
import com.phonepe.drove.models.operation.rule.RuleCallStatus;
import lombok.val;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests {@link HopeRuleStrategy}
 */
class HopeRuleStrategyTest extends AbstractTestBase {

    @Test
    void test() {
        val rs = new HopeRuleStrategy(MAPPER, 0);
        assertEquals(RuleCallStatus.SUCCESS, rs.check("1 == 1").getStatus());
        assertEquals(RuleCallStatus.FAILURE, rs.check("1").getStatus());
        assertEquals(RuleCallStatus.SUCCESS, rs.evaluate("'/data' == 'SS'", new TestDataNode("SS")).getStatus());
        assertEquals(RuleCallStatus.SUCCESS, rs.evaluate("'/data' == 'XX'", new TestDataNode("SS")).getStatus());
        assertEquals(RuleCallStatus.FAILURE, rs.evaluate("'/data'", new TestDataNode("SS")).getStatus());
        assertEquals(RuleCallStatus.FAILURE, rs.evaluate("'/data' == 'SS'", new TestDataNode(null)).getStatus());
    }
}