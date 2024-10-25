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

package com.phonepe.drove.controller.managed;

import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.SharedMetricRegistries;
import com.google.common.collect.Sets;
import com.phonepe.drove.common.CommonTestUtils;
import com.phonepe.drove.controller.ControllerTestUtils;
import com.phonepe.drove.controller.engine.ApplicationLifecycleManagentEngine;
import com.phonepe.drove.controller.metrics.ClusterMetricNames;
import com.phonepe.drove.controller.metrics.ClusterMetricsRegistry;
import com.phonepe.drove.controller.resourcemgmt.ClusterResourcesDB;
import com.phonepe.drove.controller.statedb.ApplicationStateDB;
import com.phonepe.drove.models.application.ApplicationInfo;
import com.phonepe.drove.models.application.ApplicationState;
import io.appform.signals.signals.ScheduledSignal;
import io.dropwizard.lifecycle.setup.LifecycleEnvironment;
import io.dropwizard.setup.Environment;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test for {@link ClusterMetricsCollector}
 */
class ClusterMetricsCollectorTest {

    @Test
    void testMetricsCollection() {
        val crdb = mock(ClusterResourcesDB.class);
        val asDB = mock(ApplicationStateDB.class);
        val ae = mock(ApplicationLifecycleManagentEngine.class);
        val le = mock(LeadershipEnsurer.class);
        val env = mock(Environment.class);
        val lenv = mock(LifecycleEnvironment.class);

        val metricRegistry = SharedMetricRegistries.getOrCreate("test");

        when(crdb.currentSnapshot(anyBoolean())).thenReturn(List.of(ControllerTestUtils.executorHost(8080)));
        when(asDB.applications(0, Integer.MAX_VALUE))
                .thenReturn(List.of(new ApplicationInfo("testapp-1",
                                                        ControllerTestUtils.appSpec(),
                                                        1,
                                                        new Date(),
                                                        new Date())));
        when(ae.currentState("testapp-1")).thenReturn(Optional.of(ApplicationState.RUNNING));
        when(lenv.getMetricRegistry()).thenReturn(metricRegistry);
        when(env.lifecycle()).thenReturn(lenv);
        when(env.metrics()).thenReturn(metricRegistry);

        when(le.isLeader()).thenReturn(true);

        val cmr = new ClusterMetricsRegistry(env);

        val cmc = new ClusterMetricsCollector(crdb, asDB, ae, le, env, cmr, new ScheduledSignal(Duration.ofSeconds(1)));

        cmc.serverStarted(null);

        cmr.markMeter(ClusterMetricNames.Meters.CLUSTER_EVENTS);
        CommonTestUtils.delay(Duration.ofSeconds(3));
        val guages = metricRegistry.getGauges(MetricFilter.ALL)
                .keySet();
        val metricNames = gaugeNames();
        assertTrue(guages.containsAll(metricNames),
                   "Actual " + guages + " missing: " + Sets.difference(metricNames, guages));
        cmc.stop();
    }

    @SneakyThrows
    private static Set<String> gaugeNames() {
        val names = new HashSet<String>();
        for (Field field : ClusterMetricNames.Gauges.class.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers)) {
                names.add("com.phonepe.drove." + field.get(null));
            }
        }
        return names;
    }

}