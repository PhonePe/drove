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

package com.phonepe.drove.controller.resourcemgmt;

import com.phonepe.drove.controller.ControllerTestBase;
import com.phonepe.drove.controller.ControllerTestUtils;
import com.phonepe.drove.controller.testsupport.InMemoryApplicationInstanceInfoDB;
import com.phonepe.drove.controller.testsupport.InMemoryTaskDB;
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
        val instanceId = "I1";
        val node = sched.schedule(schedId, instanceId, spec).orElse(null);
        IntStream.rangeClosed(2, 25)
                .forEach(i -> {
                    val iId = "I" + i;
                    assertNotNull(sched.schedule(schedId, iId, spec).orElse(null));
                });
        assertNull(sched.schedule(schedId, instanceId, spec).orElse(null));
        sched.discardAllocation(schedId, instanceId, node);
        assertNotNull(sched.schedule(schedId, instanceId, spec).orElse(null));

    }

    @Test
    void testOnePerHostPlacementPolicy() {
        val rdb = new InMemoryClusterResourcesDB();
        val instanceInfoDB = new InMemoryApplicationInstanceInfoDB();
        val taskDB = new InMemoryTaskDB();
        val sched = new DefaultInstanceScheduler(instanceInfoDB, taskDB, rdb);
        val originalSpec = appSpec();
        val spec = originalSpec.withPlacementPolicy(new OnePerHostPlacementPolicy());
        rdb.update(IntStream.rangeClosed(1, 5).mapToObj(ControllerTestUtils::generateExecutorNode).toList());
        val schedId = "SCHED_ID_1";
        val instanceId = "I1";
        val node = sched.schedule(schedId, instanceId, spec).orElse(null);
        IntStream.rangeClosed(2, 5)
                .forEach(i -> {
                    val iId = "I" + i;
                    assertNotNull(sched.schedule(schedId, iId, spec).orElse(null));
                });
        assertNull(sched.schedule(schedId, instanceId, spec).orElse(null));
        sched.discardAllocation(schedId, instanceId, node);
        assertNotNull(sched.schedule(schedId, instanceId, spec).orElse(null));
    }

    @Test
    void testMaxNPerHostPlacementPolicy() {
        val rdb = new InMemoryClusterResourcesDB();
        val instanceInfoDB = new InMemoryApplicationInstanceInfoDB();
        val taskDB = new InMemoryTaskDB();
        val sched = new DefaultInstanceScheduler(instanceInfoDB, taskDB, rdb);
        val originalSpec = appSpec();
        val spec = originalSpec.withPlacementPolicy(new MaxNPerHostPlacementPolicy(2));
        rdb.update(IntStream.rangeClosed(1, 5).mapToObj(ControllerTestUtils::generateExecutorNode).toList());
        val schedId = "SCHED_ID_1";
        val instanceId = "I1";
        val node = sched.schedule(schedId, instanceId, spec).orElse(null);
        IntStream.rangeClosed(2, 10)
                .forEach(i -> {
                    val iId = "I" + i;
                    assertNotNull(sched.schedule(schedId, iId, spec).orElse(null));
                });
        assertNull(sched.schedule(schedId, instanceId, spec).orElse(null));
        sched.discardAllocation(schedId, instanceId, node);
        assertNotNull(sched.schedule(schedId, instanceId, spec).orElse(null));
    }

    @Test
    void testMatchTagPlacementPolicyNormal() {
        val rdb = new InMemoryClusterResourcesDB();
        val instanceInfoDB = new InMemoryApplicationInstanceInfoDB();
        val taskDB = new InMemoryTaskDB();
        val sched = new DefaultInstanceScheduler(instanceInfoDB, taskDB, rdb);
        val originalSpec = appSpec();
        val spec = originalSpec.withPlacementPolicy(new MatchTagPlacementPolicy("test"));
        rdb.update(IntStream.rangeClosed(1, 3).mapToObj(i -> generateExecutorNode(i, Set.of("test"))).toList());
        rdb.update(IntStream.rangeClosed(4, 5).mapToObj(ControllerTestUtils::generateExecutorNode).toList());
        val schedId = "SCHED_ID_1";
        val instanceId = "I1";
        val node = sched.schedule(schedId, instanceId, spec).orElse(null);
        IntStream.rangeClosed(2, 15)
                .forEach(i -> {
                    val iId = "I" + i;
                    assertNotNull(sched.schedule(schedId, iId, spec).orElse(null));
                });
        assertNull(sched.schedule(schedId, instanceId, spec).orElse(null));
        sched.discardAllocation(schedId, instanceId, node);
        assertNotNull(sched.schedule(schedId, instanceId, spec).orElse(null));
    }

    @Test
    void testNoTagPlacementPolicy() {
        val rdb = new InMemoryClusterResourcesDB();
        val instanceInfoDB = new InMemoryApplicationInstanceInfoDB();
        val taskDB = new InMemoryTaskDB();
        val sched = new DefaultInstanceScheduler(instanceInfoDB, taskDB, rdb);
        val originalSpec = appSpec();
        val spec = originalSpec.withPlacementPolicy(new NoTagPlacementPolicy());
        rdb.update(IntStream.rangeClosed(1, 3).mapToObj(i -> generateExecutorNode(i, Set.of("test"))).toList());
        rdb.update(IntStream.rangeClosed(4, 5).mapToObj(ControllerTestUtils::generateExecutorNode).toList());
        val schedId = "SCHED_ID_1";
        val instanceId = "I1";
        val node = sched.schedule(schedId, instanceId, spec).orElse(null);
        IntStream.rangeClosed(2, 10)
                .forEach(i -> {
                    val iId = "I" + i;
                    assertNotNull(sched.schedule(schedId, iId, spec).orElse(null));
                });
        assertNull(sched.schedule(schedId, instanceId, spec).orElse(null));
        sched.discardAllocation(schedId, instanceId, node);
        assertNotNull(sched.schedule(schedId, instanceId, spec).orElse(null));
    }

    @Test
    void testCompositePlacementPolicyAnd() {
        val rdb = new InMemoryClusterResourcesDB();
        val instanceInfoDB = new InMemoryApplicationInstanceInfoDB();
        val taskDB = new InMemoryTaskDB();
        val sched = new DefaultInstanceScheduler(instanceInfoDB, taskDB, rdb);
        val originalSpec = appSpec();
        val spec = originalSpec
                .withPlacementPolicy(new CompositePlacementPolicy(
                        List.of(new MatchTagPlacementPolicy("test"), new MaxNPerHostPlacementPolicy(2)),
                        AND));
        rdb.update(IntStream.rangeClosed(1, 3).mapToObj(i -> generateExecutorNode(i, Set.of("test"))).toList());
        rdb.update(IntStream.rangeClosed(4, 5).mapToObj(ControllerTestUtils::generateExecutorNode).toList());
        val schedId = "SCHED_ID_1";
        val instanceId = "I1";
        val node = sched.schedule(schedId, instanceId, spec).orElse(null);
        IntStream.rangeClosed(2, 6)
                .forEach(i -> {
                    val iId = "I" + i;
                    assertNotNull(sched.schedule(schedId, iId, spec).orElse(null));
                });
        assertNull(sched.schedule(schedId, instanceId, spec).orElse(null));
        sched.discardAllocation(schedId, instanceId, node);
        assertNotNull(sched.schedule(schedId, instanceId, spec).orElse(null));
    }

    @Test
    void testCompositePlacementPolicyOR() {
        val rdb = new InMemoryClusterResourcesDB();
        val instanceInfoDB = new InMemoryApplicationInstanceInfoDB();
        val taskDB = new InMemoryTaskDB();
        val sched = new DefaultInstanceScheduler(instanceInfoDB, taskDB, rdb);
        val originalSpec = appSpec();
        val spec = originalSpec.withPlacementPolicy(new CompositePlacementPolicy(
                List.of(new MatchTagPlacementPolicy("test"),
                        new MatchTagPlacementPolicy("test2")),
                OR));
        rdb.update(IntStream.rangeClosed(1, 2).mapToObj(i -> generateExecutorNode(i, Set.of("test"))).toList());
        rdb.update(IntStream.rangeClosed(2, 3).mapToObj(i -> generateExecutorNode(i, Set.of("test2"))).toList());
        rdb.update(IntStream.rangeClosed(4, 5).mapToObj(ControllerTestUtils::generateExecutorNode).toList());
        val schedId = "SCHED_ID_1";
        val instanceId = "I1";
        val node = sched.schedule(schedId, instanceId, spec).orElse(null);
        IntStream.rangeClosed(2, 15)
                .forEach(i -> {
                    val iId = "I" + i;
                    assertNotNull(sched.schedule(schedId, iId, spec).orElse(null));
                });
        assertNull(sched.schedule(schedId, instanceId, spec).orElse(null));
        sched.discardAllocation(schedId, instanceId, node);
        assertNotNull(sched.schedule(schedId, instanceId, spec).orElse(null));
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
                    .forEach(i -> {
                        val instanceId = "I" + i;
                        assertNotNull(sched.schedule(schedId, instanceId, spec).orElse(null));
                    });
            sched.finaliseSession(schedId);
        }
        {
            val schedId = "SCHED_ID_2";
            IntStream.rangeClosed(1, 5)
                    .forEach(i -> {
                        val instanceId = "I" + i + 10;
                        assertNotNull(sched.schedule(schedId, instanceId, spec).orElse(null));
                    });
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
        rdb.update(IntStream.rangeClosed(4, 5)
                           .mapToObj(index -> ControllerTestUtils.generateExecutorNode(index, Set.of(), true))
                           .toList());
        val schedId = "SCHED_ID_1";
        val instanceId = "I1";
        val node = sched.schedule(schedId, instanceId, spec).orElse(null);
        IntStream.rangeClosed(2, 15)
                .forEach(i -> {
                    val iId = "I" + i;
                    assertNotNull(sched.schedule(schedId, iId, spec).orElse(null));
                });
        assertNull(sched.schedule(schedId, instanceId, spec).orElse(null));
        sched.discardAllocation(schedId, instanceId, node);
        assertNotNull(sched.schedule(schedId, instanceId, spec).orElse(null));
    }
}