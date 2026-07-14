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

package com.phonepe.drove.controller.engine.jobs;

import static com.phonepe.drove.common.CommonTestUtils.httpCaller;
import static com.phonepe.drove.controller.ControllerTestUtils.taskSpec;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.google.common.util.concurrent.MoreExecutors;
import com.phonepe.drove.common.model.MessageDeliveryStatus;
import com.phonepe.drove.common.model.MessageResponse;
import com.phonepe.drove.common.model.TaskInstanceSpec;
import com.phonepe.drove.common.model.executor.StartTaskMessage;
import com.phonepe.drove.controller.ControllerTestBase;
import com.phonepe.drove.controller.ControllerTestUtils;
import com.phonepe.drove.controller.config.ControllerOptions;
import com.phonepe.drove.controller.engine.ControllerCommunicator;
import com.phonepe.drove.controller.engine.ControllerRetrySpecFactory;
import com.phonepe.drove.controller.engine.InstanceIdGenerator;
import com.phonepe.drove.controller.engine.RandomInstanceIdGenerator;
import com.phonepe.drove.controller.resourcemgmt.InstanceScheduler;
import com.phonepe.drove.controller.securityprofile.ContainerSecurityPolicyConfig;
import com.phonepe.drove.controller.securityprofile.SecurityPolicyEnforcer;
import com.phonepe.drove.controller.statedb.TaskDB;
import com.phonepe.drove.jobexecutor.JobExecutor;
import com.phonepe.drove.models.policies.ContainerSecurityProfile;
import com.phonepe.drove.models.task.TaskSpec;
import com.phonepe.drove.models.taskinstance.TaskState;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

import lombok.val;

/**
 * Tests for StartTaskJob.
 */
class StartTaskJobTest extends ControllerTestBase {

    private static final TaskSpec TASK_SPEC = taskSpec();

    private final InstanceIdGenerator instanceIdGenerator = new RandomInstanceIdGenerator();

    final InstanceScheduler scheduler = mock(InstanceScheduler.class);
    final TaskDB taskDB = mock(TaskDB.class);
    final ControllerCommunicator comm = mock(ControllerCommunicator.class);
    final ControllerRetrySpecFactory rf = mock(ControllerRetrySpecFactory.class);

    @AfterEach
    void resetMocks() {
        reset(scheduler, taskDB, comm, rf);
    }

    @Test
    void testJobSuccess() {
        val sessionId = UUID.randomUUID().toString();
        val allocatedNode = ControllerTestUtils.allocatedExecutorNode(8080);

        when(scheduler.schedule(eq(sessionId), anyString(), eq(TASK_SPEC)))
                .thenReturn(Optional.of(allocatedNode));
        when(comm.send(any(StartTaskMessage.class)))
                .thenAnswer((Answer<MessageResponse>) inv ->
                        new MessageResponse(inv.<StartTaskMessage>getArgument(0).getHeader(),
                                            MessageDeliveryStatus.ACCEPTED));
        when(taskDB.task(eq(TASK_SPEC.getSourceAppName()), eq(TASK_SPEC.getTaskId())))
                .thenReturn(Optional.of(ControllerTestUtils.generateTaskInfo(TASK_SPEC, 1, TaskState.RUNNING)));

        when(rf.jobRetrySpec()).thenReturn(ControllerTestUtils.NO_RETRY_SPEC);
        when(rf.instanceStateCheckRetrySpec(anyLong())).thenReturn(ControllerTestUtils.NO_RETRY_SPEC);

        val job = new StartTaskJob(TASK_SPEC,
                                   ControllerTestUtils.DEFAULT_CLUSTER_OP,
                                   scheduler,
                                   taskDB,
                                   comm,
                                   sessionId,
                                   rf,
                                   instanceIdGenerator,
                                   ControllerTestUtils.defaultEnforcer(),
                                   httpCaller());
        val testStatus = new AtomicBoolean();
        val exec = new JobExecutor<Boolean>(MoreExecutors.newDirectExecutorService());
        exec.schedule(List.of(job), new BooleanResponseCombiner(),
                      r -> testStatus.set(r.getResult()));
        assertTrue(testStatus.get());
    }


    @Test
    void testSecuritySpecPropagated() {
        val sessionId = UUID.randomUUID().toString();

        val allocatedNode = ControllerTestUtils.allocatedExecutorNode(8080);

        when(scheduler.schedule(eq(sessionId), anyString(), eq(TASK_SPEC)))
                .thenReturn(Optional.of(allocatedNode));

        val capturedSpec = new AtomicReference<TaskInstanceSpec>();
        when(comm.send(any(StartTaskMessage.class)))
                .thenAnswer((Answer<MessageResponse>) inv -> {
                    capturedSpec.set(inv.<StartTaskMessage>getArgument(0).getSpec());
                    return new MessageResponse(
                            inv.<StartTaskMessage>getArgument(0).getHeader(),
                            MessageDeliveryStatus.ACCEPTED);
                });
        when(taskDB.task(eq(TASK_SPEC.getSourceAppName()), eq(TASK_SPEC.getTaskId())))
                .thenReturn(Optional.of(ControllerTestUtils.generateTaskInfo(TASK_SPEC, 1, TaskState.RUNNING)));

        val profile = ContainerSecurityProfile.builder()
                .policyName("privileged-task-policy")
                .enablePrivilegedMode(true)
                .build();
        val config = ContainerSecurityPolicyConfig.builder()
                .match(List.of("COMPUTATION/TEST_TASK_SPEC.*"))
                .profile(profile)
                .build();
        val enforcer = new SecurityPolicyEnforcer(
                List.of(config),
                ControllerOptions.DEFAULT);

        when(rf.jobRetrySpec()).thenReturn(ControllerTestUtils.NO_RETRY_SPEC);
        when(rf.instanceStateCheckRetrySpec(anyLong())).thenReturn(ControllerTestUtils.NO_RETRY_SPEC);

        val job = new StartTaskJob(TASK_SPEC,
                                   ControllerTestUtils.DEFAULT_CLUSTER_OP,
                                   scheduler,
                                   taskDB,
                                   comm,
                                   sessionId,
                                   rf,
                                   instanceIdGenerator,
                                   enforcer,
                                   httpCaller());
        val testStatus = new AtomicBoolean();
        val exec = new JobExecutor<Boolean>(MoreExecutors.newDirectExecutorService());
        exec.schedule(List.of(job), new BooleanResponseCombiner(),
                      r -> testStatus.set(r.getResult()));

        assertTrue(testStatus.get());
        assertNotNull(capturedSpec.get(), "StartTaskMessage spec must be captured");
        assertNotNull(capturedSpec.get().getSecuritySpec(), "securitySpec must not be null");
        assertTrue(capturedSpec.get().getSecuritySpec().getEnablePrivilegedMode(),
                   "enablePrivilegedMode must be true for privileged-mode enforcer");
    }


    @Test
    void testJobCommFailure() {
        val sessionId = UUID.randomUUID().toString();

        val allocatedNode = ControllerTestUtils.allocatedExecutorNode(8080);

        when(scheduler.schedule(eq(sessionId), anyString(), eq(TASK_SPEC)))
                .thenReturn(Optional.of(allocatedNode));
        when(comm.send(any(StartTaskMessage.class)))
                .thenAnswer((Answer<MessageResponse>) inv ->
                        new MessageResponse(inv.<StartTaskMessage>getArgument(0).getHeader(),
                                            MessageDeliveryStatus.REJECTED));

        when(rf.jobRetrySpec()).thenReturn(ControllerTestUtils.NO_RETRY_SPEC);
        when(rf.instanceStateCheckRetrySpec(anyLong())).thenReturn(ControllerTestUtils.NO_RETRY_SPEC);

        val job = new StartTaskJob(TASK_SPEC,
                                   ControllerTestUtils.DEFAULT_CLUSTER_OP,
                                   scheduler,
                                   taskDB,
                                   comm,
                                   sessionId,
                                   rf,
                                   instanceIdGenerator,
                                   ControllerTestUtils.defaultEnforcer(),
                                   httpCaller());
        assertJobFails(job);
    }


    @Test
    void testJobNoNode() {
        val sessionId = UUID.randomUUID().toString();

        when(scheduler.schedule(eq(sessionId), anyString(), eq(TASK_SPEC)))
                .thenReturn(Optional.empty());

        when(rf.jobRetrySpec()).thenReturn(ControllerTestUtils.NO_RETRY_SPEC);
        when(rf.instanceStateCheckRetrySpec(anyLong())).thenReturn(ControllerTestUtils.NO_RETRY_SPEC);

        val job = new StartTaskJob(TASK_SPEC,
                                   ControllerTestUtils.DEFAULT_CLUSTER_OP,
                                   scheduler,
                                   taskDB,
                                   comm,
                                   sessionId,
                                   rf,
                                   instanceIdGenerator,
                                   ControllerTestUtils.defaultEnforcer(),
                                   httpCaller());
        assertJobFails(job);
    }

    private void assertJobFails(StartTaskJob job) {
        val testStatus = new AtomicBoolean(true);
        val exec = new JobExecutor<Boolean>(MoreExecutors.newDirectExecutorService());
        exec.schedule(List.of(job), new BooleanResponseCombiner(),
                      r -> testStatus.set(r.getResult()));
        assertFalse(testStatus.get());
    }
}
