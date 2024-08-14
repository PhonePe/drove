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

package com.phonepe.drove.controller.resourcemgmt;

import com.phonepe.drove.controller.ControllerTestBase;
import com.phonepe.drove.controller.ControllerTestUtils;
import com.phonepe.drove.controller.testsupport.InMemoryApplicationInstanceInfoDB;
import com.phonepe.drove.controller.testsupport.InMemoryTaskDB;
import com.phonepe.drove.models.application.ApplicationSpec;
import com.phonepe.drove.models.application.placement.policies.*;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static com.phonepe.drove.controller.ControllerTestUtils.appSpec;
import static com.phonepe.drove.controller.ControllerTestUtils.generateExecutorNode;
import static com.phonepe.drove.models.application.placement.policies.CompositePlacementPolicy.CombinerType.AND;
import static com.phonepe.drove.models.application.placement.policies.CompositePlacementPolicy.CombinerType.OR;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 *
 */
class DefaultInstanceSchedulerTest extends ControllerTestBase {

    @Test
    void testScheduling() {
        val rdb = new InMemoryClusterResourcesDB();
        val instanceInfoDB = new InMemoryApplicationInstanceInfoDB();
        val taskDB = new InMemoryTaskDB();
        val sched = new DefaultInstanceScheduler(instanceInfoDB, taskDB, rdb);
        val spec = appSpec();

        //Load cluster nodes
        rdb.update(IntStream.rangeClosed(1, 5).mapToObj(ControllerTestUtils::generateExecutorNode).toList());
        val schedId = "SCHED_ID_1";
        val node = sched.schedule(schedId, spec).orElse(null);
        IntStream.rangeClosed(2, 25)
                .forEach(i -> assertNotNull(sched.schedule(schedId, spec).orElse(null)));
        assertNull(sched.schedule(schedId, spec).orElse(null));
        sched.discardAllocation(schedId, node);
        assertNotNull(sched.schedule(schedId, spec).orElse(null));

    }

    @Test
    void testOnePerHostPlacementPolicy() {
        val rdb = new InMemoryClusterResourcesDB();
        val instanceInfoDB = new InMemoryApplicationInstanceInfoDB();
        val taskDB = new InMemoryTaskDB();
        val sched = new DefaultInstanceScheduler(instanceInfoDB, taskDB, rdb);
        val originalSpec = appSpec();
        val spec = new ApplicationSpec(originalSpec.getName(),
                                       originalSpec.getVersion(),
                                       originalSpec.getExecutable(),
                                       originalSpec.getExposedPorts(),
                                       originalSpec.getVolumes(),
                                       originalSpec.getConfigs(),
                                       originalSpec.getType(),
                                       originalSpec.getLogging(),
                                       originalSpec.getResources(),
                                       new OnePerHostPlacementPolicy(),
                                       originalSpec.getHealthcheck(),
                                       originalSpec.getReadiness(),
                                       originalSpec.getTags(),
                                       originalSpec.getEnv(),
                                       originalSpec.getArgs(),
                                       originalSpec.getExposureSpec(),
                                       originalSpec.getPreShutdown());
        rdb.update(IntStream.rangeClosed(1, 5).mapToObj(ControllerTestUtils::generateExecutorNode).toList());
        val schedId = "SCHED_ID_1";
        val node = sched.schedule(schedId, spec).orElse(null);
        IntStream.rangeClosed(2, 5)
                .forEach(i -> assertNotNull(sched.schedule(schedId, spec).orElse(null)));
        assertNull(sched.schedule(schedId, spec).orElse(null));
        sched.discardAllocation(schedId, node);
        assertNotNull(sched.schedule(schedId, spec).orElse(null));
    }

    @Test
    void testMaxNPerHostPlacementPolicy() {
        val rdb = new InMemoryClusterResourcesDB();
        val instanceInfoDB = new InMemoryApplicationInstanceInfoDB();
        val taskDB = new InMemoryTaskDB();
        val sched = new DefaultInstanceScheduler(instanceInfoDB, taskDB, rdb);
        val originalSpec = appSpec();
        val spec = new ApplicationSpec(originalSpec.getName(),
                                       originalSpec.getVersion(),
                                       originalSpec.getExecutable(),
                                       originalSpec.getExposedPorts(),
                                       originalSpec.getVolumes(),
                                       originalSpec.getConfigs(),
                                       originalSpec.getType(),
                                       originalSpec.getLogging(),
                                       originalSpec.getResources(),
                                       new MaxNPerHostPlacementPolicy(2),
                                       originalSpec.getHealthcheck(),
                                       originalSpec.getReadiness(),
                                       originalSpec.getTags(),
                                       originalSpec.getEnv(),
                                       originalSpec.getArgs(),
                                       originalSpec.getExposureSpec(),
                                       originalSpec.getPreShutdown());
        rdb.update(IntStream.rangeClosed(1, 5).mapToObj(ControllerTestUtils::generateExecutorNode).toList());
        val schedId = "SCHED_ID_1";
        val node = sched.schedule(schedId, spec).orElse(null);
        IntStream.rangeClosed(2, 10)
                .forEach(i -> assertNotNull(sched.schedule(schedId, spec).orElse(null)));
        assertNull(sched.schedule(schedId, spec).orElse(null));
        sched.discardAllocation(schedId, node);
        assertNotNull(sched.schedule(schedId, spec).orElse(null));
    }

    @Test
    void testMatchTagPlacementPolicyNormal() {
        val rdb = new InMemoryClusterResourcesDB();
        val instanceInfoDB = new InMemoryApplicationInstanceInfoDB();
        val taskDB = new InMemoryTaskDB();
        val sched = new DefaultInstanceScheduler(instanceInfoDB, taskDB, rdb);
        val originalSpec = appSpec();
        val spec = new ApplicationSpec(originalSpec.getName(),
                                       originalSpec.getVersion(),
                                       originalSpec.getExecutable(),
                                       originalSpec.getExposedPorts(),
                                       originalSpec.getVolumes(),
                                       originalSpec.getConfigs(),
                                       originalSpec.getType(),
                                       originalSpec.getLogging(),
                                       originalSpec.getResources(),
                                       new MatchTagPlacementPolicy("test"),
                                       originalSpec.getHealthcheck(),
                                       originalSpec.getReadiness(),
                                       originalSpec.getTags(),
                                       originalSpec.getEnv(),
                                       originalSpec.getArgs(),
                                       originalSpec.getExposureSpec(),
                                       originalSpec.getPreShutdown());
        rdb.update(IntStream.rangeClosed(1, 3).mapToObj(i -> generateExecutorNode(i, Set.of("test"))).toList());
        rdb.update(IntStream.rangeClosed(4, 5).mapToObj(ControllerTestUtils::generateExecutorNode).toList());
        val schedId = "SCHED_ID_1";
        val node = sched.schedule(schedId, spec).orElse(null);
        IntStream.rangeClosed(2, 15)
                .forEach(i -> assertNotNull(sched.schedule(schedId, spec).orElse(null)));
        assertNull(sched.schedule(schedId, spec).orElse(null));
        sched.discardAllocation(schedId, node);
        assertNotNull(sched.schedule(schedId, spec).orElse(null));
    }

    @Test
    void testNoTagPlacementPolicy() {
        val rdb = new InMemoryClusterResourcesDB();
        val instanceInfoDB = new InMemoryApplicationInstanceInfoDB();
        val taskDB = new InMemoryTaskDB();
        val sched = new DefaultInstanceScheduler(instanceInfoDB, taskDB, rdb);
        val originalSpec = appSpec();
        val spec = new ApplicationSpec(originalSpec.getName(),
                                       originalSpec.getVersion(),
                                       originalSpec.getExecutable(),
                                       originalSpec.getExposedPorts(),
                                       originalSpec.getVolumes(),
                                       originalSpec.getConfigs(),
                                       originalSpec.getType(),
                                       originalSpec.getLogging(),
                                       originalSpec.getResources(),
                                       new NoTagPlacementPolicy(),
                                       originalSpec.getHealthcheck(),
                                       originalSpec.getReadiness(),
                                       originalSpec.getTags(),
                                       originalSpec.getEnv(),
                                       originalSpec.getArgs(),
                                       originalSpec.getExposureSpec(),
                                       originalSpec.getPreShutdown());
        rdb.update(IntStream.rangeClosed(1, 3).mapToObj(i -> generateExecutorNode(i, Set.of("test"))).toList());
        rdb.update(IntStream.rangeClosed(4, 5).mapToObj(ControllerTestUtils::generateExecutorNode).toList());
        val schedId = "SCHED_ID_1";
        val node = sched.schedule(schedId, spec).orElse(null);
        IntStream.rangeClosed(2, 10)
                .forEach(i -> assertNotNull(sched.schedule(schedId, spec).orElse(null)));
        assertNull(sched.schedule(schedId, spec).orElse(null));
        sched.discardAllocation(schedId, node);
        assertNotNull(sched.schedule(schedId, spec).orElse(null));
    }

    @Test
    void testCompositePlacementPolicyAnd() {
        val rdb = new InMemoryClusterResourcesDB();
        val instanceInfoDB = new InMemoryApplicationInstanceInfoDB();
        val taskDB = new InMemoryTaskDB();
        val sched = new DefaultInstanceScheduler(instanceInfoDB, taskDB, rdb);
        val originalSpec = appSpec();
        val spec = new ApplicationSpec(originalSpec.getName(),
                                       originalSpec.getVersion(),
                                       originalSpec.getExecutable(),
                                       originalSpec.getExposedPorts(),
                                       originalSpec.getVolumes(),
                                       originalSpec.getConfigs(),
                                       originalSpec.getType(),
                                       originalSpec.getLogging(),
                                       originalSpec.getResources(),
                                       new CompositePlacementPolicy(
                                               List.of(new MatchTagPlacementPolicy("test"),
                                                       new MaxNPerHostPlacementPolicy(2)),
                                               AND),
                                       originalSpec.getHealthcheck(),
                                       originalSpec.getReadiness(),
                                       originalSpec.getTags(),
                                       originalSpec.getEnv(),
                                       originalSpec.getArgs(),
                                       originalSpec.getExposureSpec(),
                                       originalSpec.getPreShutdown());
        rdb.update(IntStream.rangeClosed(1, 3).mapToObj(i -> generateExecutorNode(i, Set.of("test"))).toList());
        rdb.update(IntStream.rangeClosed(4, 5).mapToObj(ControllerTestUtils::generateExecutorNode).toList());
        val schedId = "SCHED_ID_1";
        val node = sched.schedule(schedId, spec).orElse(null);
        IntStream.rangeClosed(2, 6)
                .forEach(i -> assertNotNull(sched.schedule(schedId, spec).orElse(null)));
        assertNull(sched.schedule(schedId, spec).orElse(null));
        sched.discardAllocation(schedId, node);
        assertNotNull(sched.schedule(schedId, spec).orElse(null));
    }

    @Test
    void testCompositePlacementPolicyOR() {
        val rdb = new InMemoryClusterResourcesDB();
        val instanceInfoDB = new InMemoryApplicationInstanceInfoDB();
        val taskDB = new InMemoryTaskDB();
        val sched = new DefaultInstanceScheduler(instanceInfoDB, taskDB, rdb);
        val originalSpec = appSpec();
        val spec = new ApplicationSpec(originalSpec.getName(),
                                       originalSpec.getVersion(),
                                       originalSpec.getExecutable(),
                                       originalSpec.getExposedPorts(),
                                       originalSpec.getVolumes(),
                                       originalSpec.getConfigs(),
                                       originalSpec.getType(),
                                       originalSpec.getLogging(),
                                       originalSpec.getResources(),
                                       new CompositePlacementPolicy(
                                               List.of(new MatchTagPlacementPolicy("test"),
                                                       new MatchTagPlacementPolicy("test2")),
                                               OR),
                                       originalSpec.getHealthcheck(),
                                       originalSpec.getReadiness(),
                                       originalSpec.getTags(),
                                       originalSpec.getEnv(),
                                       originalSpec.getArgs(),
                                       originalSpec.getExposureSpec(),
                                       originalSpec.getPreShutdown());
        rdb.update(IntStream.rangeClosed(1, 2).mapToObj(i -> generateExecutorNode(i, Set.of("test"))).toList());
        rdb.update(IntStream.rangeClosed(2, 3).mapToObj(i -> generateExecutorNode(i, Set.of("test2"))).toList());
        rdb.update(IntStream.rangeClosed(4, 5).mapToObj(ControllerTestUtils::generateExecutorNode).toList());
        val schedId = "SCHED_ID_1";
        val node = sched.schedule(schedId, spec).orElse(null);
        IntStream.rangeClosed(2, 15)
                .forEach(i -> assertNotNull(sched.schedule(schedId, spec).orElse(null)));
        assertNull(sched.schedule(schedId, spec).orElse(null));
        sched.discardAllocation(schedId, node);
        assertNotNull(sched.schedule(schedId, spec).orElse(null));
    }


    @Test
    void testMultiSessionAllocation() {
        val rdb = new InMemoryClusterResourcesDB();
        val instanceInfoDB = new InMemoryApplicationInstanceInfoDB();
        val taskDB = new InMemoryTaskDB();
        val sched = new DefaultInstanceScheduler(instanceInfoDB, taskDB, rdb);
        val spec = appSpec();

        //Load cluster nodes
        rdb.update(IntStream.rangeClosed(1, 5).mapToObj(ControllerTestUtils::generateExecutorNode).toList());
        {
            val schedId = "SCHED_ID_1";
            IntStream.rangeClosed(1, 20)
                    .forEach(i -> assertNotNull(sched.schedule(schedId, spec).orElse(null)));
            sched.finaliseSession(schedId);
        }
        {
            val schedId = "SCHED_ID_2";
            IntStream.rangeClosed(1, 5)
                    .forEach(i -> assertNotNull(sched.schedule(schedId, spec).orElse(null)));
            sched.finaliseSession(schedId);
        }

    }

    @Test
    void testBlacklistSkipping() {
        val rdb = new InMemoryClusterResourcesDB();
        val instanceInfoDB = new InMemoryApplicationInstanceInfoDB();
        val taskDB = new InMemoryTaskDB();
        val sched = new DefaultInstanceScheduler(instanceInfoDB, taskDB, rdb);
        val spec = appSpec();

        //Load cluster nodes
        rdb.update(IntStream.rangeClosed(1, 3).mapToObj(ControllerTestUtils::generateExecutorNode).toList());
        rdb.update(IntStream.rangeClosed(4, 5).mapToObj(index -> ControllerTestUtils.generateExecutorNode(index, Set.of(), true)).toList());
        val schedId = "SCHED_ID_1";
        val node = sched.schedule(schedId, spec).orElse(null);
        IntStream.rangeClosed(2, 15)
                .forEach(i -> assertNotNull(sched.schedule(schedId, spec).orElse(null)));
        assertNull(sched.schedule(schedId, spec).orElse(null));
        sched.discardAllocation(schedId, node);
        assertNotNull(sched.schedule(schedId, spec).orElse(null));
    }
}