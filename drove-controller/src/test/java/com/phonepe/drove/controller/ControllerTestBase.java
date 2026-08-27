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

package com.phonepe.drove.controller;

import com.codahale.metrics.SharedMetricRegistries;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.drove.controller.resourcemgmt.ClusterResourcesDB;
import com.phonepe.drove.controller.resourcemgmt.DefaultInstanceScheduler;
import com.phonepe.drove.controller.rule.RuleEvalStrategy;
import com.phonepe.drove.controller.rule.RuleEvaluator;
import com.phonepe.drove.controller.rule.hope.HopeRuleInstance;
import com.phonepe.drove.controller.rule.mvel.MvelRuleInstance;
import com.phonepe.drove.controller.statedb.TaskDB;
import com.phonepe.drove.controller.testsupport.InMemoryApplicationInstanceInfoDB;
import com.phonepe.drove.controller.testsupport.InMemoryLocalServiceStateDB;
import com.phonepe.drove.controller.testsupport.InMemoryTaskDB;
import com.phonepe.drove.models.application.placement.policies.RuleBasedPlacementPolicy;
import io.appform.functionmetrics.FunctionMetricsManager;
import io.appform.functionmetrics.Pair;
import lombok.val;
import org.junit.jupiter.api.BeforeAll;

import java.util.Map;

import static com.phonepe.drove.common.CommonUtils.configureMapper;

/**
 *
 */
public class ControllerTestBase {
    protected static final ObjectMapper MAPPER = new ObjectMapper();
    @BeforeAll
    public static void init() {
        configureMapper(MAPPER);
        FunctionMetricsManager.initialize("com.phonepe.drove.controller",
                                          SharedMetricRegistries.getOrCreate("test"));
    }

    public static Pair<TaskDB, DefaultInstanceScheduler> createDefaultInstanceScheduler(ClusterResourcesDB cdb) {
        val tdb = new InMemoryTaskDB();
        val localServiceDB = new InMemoryLocalServiceStateDB();
        val instanceDB = new InMemoryApplicationInstanceInfoDB();
        val re = new RuleEvaluator(Map.of(
                                        RuleBasedPlacementPolicy.RuleType.HOPE, HopeRuleInstance.create(MAPPER),
                                        RuleBasedPlacementPolicy.RuleType.MVEL, MvelRuleInstance.create())
                    );
        val sch = new DefaultInstanceScheduler(instanceDB, tdb, localServiceDB, cdb, re);
        return new Pair(tdb, sch);

    }
}
