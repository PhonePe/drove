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

package com.phonepe.drove.controller.engine;

import com.phonepe.drove.common.CommonTestUtils;
import com.phonepe.drove.common.model.MessageDeliveryStatus;
import com.phonepe.drove.common.model.MessageResponse;
import com.phonepe.drove.common.model.TaskInstanceSpec;
import com.phonepe.drove.common.model.executor.StartTaskMessage;
import com.phonepe.drove.common.model.executor.StopTaskMessage;
import com.phonepe.drove.controller.ControllerTestBase;
import com.phonepe.drove.controller.ControllerTestUtils;
import com.phonepe.drove.controller.config.ControllerOptions;
import com.phonepe.drove.controller.managed.LeadershipEnsurer;
import com.phonepe.drove.controller.resourcemgmt.InMemoryClusterResourcesDB;
import com.phonepe.drove.controller.securityprofile.SecurityPolicyEnforcer;
import com.phonepe.drove.controller.statedb.TaskDB;
import com.phonepe.drove.controller.testsupport.InMemoryClusterStateDB;
import com.phonepe.drove.jobexecutor.JobExecutor;
import com.phonepe.drove.jobexecutor.JobTopology;
import com.phonepe.drove.models.application.MountedVolume;
import com.phonepe.drove.models.application.requirements.CPURequirement;
import com.phonepe.drove.models.application.requirements.GPURequirement;
import com.phonepe.drove.models.application.requirements.GpuType;
import com.phonepe.drove.models.application.requirements.GpuVendor;
import com.phonepe.drove.models.application.requirements.MemoryRequirement;
import com.phonepe.drove.models.info.ExecutorResourceSnapshot;
import com.phonepe.drove.models.info.nodedata.ExecutorNodeData;
import com.phonepe.drove.models.info.nodedata.ExecutorState;
import com.phonepe.drove.models.info.nodedata.NodeTransportType;
import com.phonepe.drove.models.info.resources.PhysicalLayout;
import com.phonepe.drove.models.info.resources.available.AvailableCPU;
import com.phonepe.drove.models.info.resources.available.AvailableGPU;
import com.phonepe.drove.models.info.resources.available.AvailableMemory;
import com.phonepe.drove.models.operation.taskops.TaskCreateOperation;
import com.phonepe.drove.models.operation.taskops.TaskKillOperation;
import com.phonepe.drove.models.taskinstance.TaskInfo;
import com.phonepe.drove.models.taskinstance.TaskResult;
import com.phonepe.drove.models.application.placement.policies.MatchTagPlacementPolicy;
import com.phonepe.drove.models.application.placement.policies.RuleBasedPlacementPolicy;
import com.phonepe.drove.models.application.placement.policies.LocalPlacementPolicy;

import io.appform.signals.signals.ConsumingFireForgetSignal;
import lombok.val;
import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import javax.validation.constraints.NotNull;

import static com.phonepe.drove.common.CommonTestUtils.httpCaller;
import static com.phonepe.drove.controller.ControllerTestUtils.taskSpec;
import static com.phonepe.drove.models.taskinstance.TaskState.RUNNING;
import static com.phonepe.drove.models.taskinstance.TaskState.STOPPED;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.phonepe.drove.controller.securityprofile.ContainerSecurityPolicyConfig;
import com.phonepe.drove.models.application.devices.DirectDeviceSpec;
import com.phonepe.drove.models.policies.ContainerSecurityProfile;

/**
 *
 */
@Slf4j
class TaskEngineTest extends ControllerTestBase {

    @Test
    void testRunStartStop() {
        val cdb = getCdb();
        val pair = createDefaultInstanceScheduler(cdb);
        cdb.update(IntStream.rangeClosed(1, 5).mapToObj(ControllerTestUtils::generateExecutorNode).toList());
        val le = mock(LeadershipEnsurer.class);
        when(le.isLeader()).thenReturn(true);

        val inst = new AtomicReference<TaskInstanceSpec>();

        val comm = mock(ControllerCommunicator.class);
        when(comm.send(any(StartTaskMessage.class))).thenAnswer(invocationOnMock -> {
            val msg = invocationOnMock.getArgument(0, StartTaskMessage.class);
            val instSpec = msg.getSpec();
            inst.set(instSpec);
            pair.getKey().updateTask(instSpec.getSourceAppName(),
                           instSpec.getTaskId(),
                           new TaskInfo(instSpec.getSourceAppName(),
                                        instSpec.getTaskId(),
                                        instSpec.getInstanceId(),
                                        "EXECUTOR_5",
                                        "localhost",
                                        instSpec.getExecutable(),
                                        instSpec.getResources(),
                                        instSpec.getVolumes(),
                                        instSpec.getLoggingSpec(),
                                        instSpec.getEnv(),
                                        RUNNING,
                                        Map.of(),
                                        null,
                                        "",
                                        new Date(),
                                        new Date()));
            return new MessageResponse(msg.getHeader(), MessageDeliveryStatus.ACCEPTED);
        });
        when(comm.send(any(StopTaskMessage.class))).thenAnswer(invocationOnMock -> {
            val msg = invocationOnMock.getArgument(0, StopTaskMessage.class);
            val instSpec = inst.get();
            pair.getKey().updateTask(instSpec.getSourceAppName(),
                           instSpec.getTaskId(),
                           new TaskInfo(instSpec.getSourceAppName(),
                                        instSpec.getTaskId(),
                                        instSpec.getInstanceId(),
                                        "EXECUTOR_5",
                                        "localhost",
                                        instSpec.getExecutable(),
                                        instSpec.getResources(),
                                        instSpec.getVolumes(),
                                        instSpec.getLoggingSpec(),
                                        instSpec.getEnv(),
                                        STOPPED,
                                        Map.of(),
                                        new TaskResult(TaskResult.Status.SUCCESSFUL, 0),
                                        "",
                                        new Date(),
                                        new Date()));
            return new MessageResponse(msg.getHeader(), MessageDeliveryStatus.ACCEPTED);
        });
        val taskSpec = taskSpec();
        val executor = Executors.newCachedThreadPool();
        val te = new TaskEngine(pair.getKey(),
                                cdb,
                                pair.getValue(),
                                comm,
                                new DefaultControllerRetrySpecFactory(),
                                new RandomInstanceIdGenerator(),
                                ControllerTestUtils.defaultEnforcer(),
                                ControllerTestUtils.placementRuleEvaluator(MAPPER),
                                Executors.defaultThreadFactory(),
                                executor,
                                new JobExecutor<>(executor),
                                new InMemoryClusterStateDB(),
                                le,
                                ControllerTestUtils.DEFAULT_CLUSTER_OP,
                                httpCaller());

        te.handleTaskOp(new TaskCreateOperation(taskSpec, ControllerTestUtils.DEFAULT_CLUSTER_OP));
        CommonTestUtils.waitUntil(() -> !te.activeTasks().isEmpty());
        assertFalse(te.activeTasks().isEmpty());
        val task = te.activeTasks().get(0);
        te.handleTaskOp(new TaskKillOperation(task.getSourceAppName(),
                                              task.getTaskId(),
                                              ControllerTestUtils.DEFAULT_CLUSTER_OP));
        CommonTestUtils.waitUntil(() -> te.activeTasks().isEmpty());
        assertTrue(te.activeTasks().isEmpty());
    }

    @Test
    void testRunAutoStop() {
        val cdb = getCdb();
        val pair = createDefaultInstanceScheduler(cdb);
        cdb.update(IntStream.rangeClosed(1, 5).mapToObj(ControllerTestUtils::generateExecutorNode).toList());
        val le = mock(LeadershipEnsurer.class);
        when(le.isLeader()).thenReturn(true);

        val comm = mock(ControllerCommunicator.class);
        when(comm.send(any(StartTaskMessage.class))).thenAnswer(invocationOnMock -> {
            val msg = invocationOnMock.getArgument(0, StartTaskMessage.class);
            val instSpec = msg.getSpec();
            pair.getKey().updateTask(instSpec.getSourceAppName(),
                           instSpec.getTaskId(),
                           new TaskInfo(instSpec.getSourceAppName(),
                                        instSpec.getTaskId(),
                                        instSpec.getInstanceId(),
                                        "EXECUTOR_5",
                                        "localhost",
                                        instSpec.getExecutable(),
                                        instSpec.getResources(),
                                        instSpec.getVolumes(),
                                        instSpec.getLoggingSpec(),
                                        instSpec.getEnv(),
                                        RUNNING,
                                        Map.of(),
                                        null,
                                        "",
                                        new Date(),
                                        new Date()));
            return new MessageResponse(msg.getHeader(), MessageDeliveryStatus.ACCEPTED);
        });
        val taskSpec = taskSpec();
        val executor = Executors.newCachedThreadPool();
        val te = new TaskEngine(pair.getKey(),
                                cdb,
                                pair.getValue(),
                                comm,
                                new DefaultControllerRetrySpecFactory(),
                                new RandomInstanceIdGenerator(),
                                ControllerTestUtils.defaultEnforcer(),
                                ControllerTestUtils.placementRuleEvaluator(MAPPER),
                                Executors.defaultThreadFactory(),
                                executor,
                                new JobExecutor<>(executor),
                                new InMemoryClusterStateDB(),
                                le,
                                ControllerTestUtils.DEFAULT_CLUSTER_OP,
                                httpCaller());

        te.handleTaskOp(new TaskCreateOperation(taskSpec, ControllerTestUtils.DEFAULT_CLUSTER_OP));
        CommonTestUtils.waitUntil(() -> !te.activeTasks().isEmpty());
        assertFalse(te.activeTasks().isEmpty());
        val task = te.activeTasks().get(0);
        //This simulates task completion
        pair.getKey().updateTask(task.getSourceAppName(),
                       task.getTaskId(),
                       new TaskInfo(task.getSourceAppName(),
                                    task.getTaskId(),
                                    task.getInstanceId(),
                                    task.getExecutorId(),
                                    task.getHostname(),
                                    task.getExecutable(),
                                    task.getResources(),
                                    task.getVolumes(),
                                    task.getLoggingSpec(),
                                    task.getEnv(),
                                    STOPPED,
                                    task.getMetadata(),
                                    new TaskResult(TaskResult.Status.SUCCESSFUL, 0),
                                    "",
                                    task.getCreated(),
                                    new Date()));
        CommonTestUtils.waitUntil(() -> te.activeTasks().isEmpty());
        assertTrue(te.activeTasks().isEmpty());
    }

    @Test
    void testRunTaskLoss() {
        val cdb = getCdb();
        val pair = createDefaultInstanceScheduler(cdb);
        cdb.update(IntStream.rangeClosed(1, 5).mapToObj(ControllerTestUtils::generateExecutorNode).toList());
        val le = mock(LeadershipEnsurer.class);
        when(le.isLeader()).thenReturn(true);

        val comm = mock(ControllerCommunicator.class);
        val oldDate = new Date(new Date().getTime() - 100_000L);
        when(comm.send(any(StartTaskMessage.class))).thenAnswer(invocationOnMock -> {
            val msg = invocationOnMock.getArgument(0, StartTaskMessage.class);
            val instSpec = msg.getSpec();
            pair.getKey().updateTask(instSpec.getSourceAppName(),
                           instSpec.getTaskId(),
                           new TaskInfo(instSpec.getSourceAppName(),
                                        instSpec.getTaskId(),
                                        instSpec.getInstanceId(),
                                        "EXECUTOR_5",
                                        "localhost",
                                        instSpec.getExecutable(),
                                        instSpec.getResources(),
                                        instSpec.getVolumes(),
                                        instSpec.getLoggingSpec(),
                                        instSpec.getEnv(),
                                        RUNNING,
                                        Map.of(),
                                        null,
                                        "",
                                        new Date(),
                                        new Date()));
            return new MessageResponse(msg.getHeader(), MessageDeliveryStatus.ACCEPTED);
        });
        val taskSpec = taskSpec();
        val executor = Executors.newCachedThreadPool();
        val te = new TaskEngine(pair.getKey(),
                                cdb,
                                pair.getValue(),
                                comm,
                                new DefaultControllerRetrySpecFactory(),
                                new RandomInstanceIdGenerator(),
                                ControllerTestUtils.defaultEnforcer(),
                                ControllerTestUtils.placementRuleEvaluator(MAPPER),
                                Executors.defaultThreadFactory(),
                                executor,
                                new JobExecutor<>(executor),
                                new InMemoryClusterStateDB(),
                                le,
                                ControllerTestUtils.DEFAULT_CLUSTER_OP,
                                httpCaller());

        te.handleTaskOp(new TaskCreateOperation(taskSpec, ControllerTestUtils.DEFAULT_CLUSTER_OP));
        CommonTestUtils.waitUntil(() -> !te.activeTasks().isEmpty());
        assertFalse(te.activeTasks().isEmpty());
        val task = te.activeTasks().get(0);
        //This simulates task completion
        pair.getKey().updateTask(task.getSourceAppName(),
                       task.getTaskId(),
                       new TaskInfo(task.getSourceAppName(),
                                    task.getTaskId(),
                                    task.getInstanceId(),
                                    task.getExecutorId(),
                                    task.getHostname(),
                                    task.getExecutable(),
                                    task.getResources(),
                                    task.getVolumes(),
                                    task.getLoggingSpec(),
                                    task.getEnv(),
                                    RUNNING,
                                    task.getMetadata(),
                                    null,
                                    "",
                                    task.getCreated(),
                                    oldDate));
        CommonTestUtils.waitUntil(() -> te.activeTasks().isEmpty());
        assertTrue(te.activeTasks().isEmpty());
    }

    @Test
    void testRunZombieKill() {
        val cdb = getCdb();
        val pair = createDefaultInstanceScheduler(cdb);
        cdb.update(IntStream.rangeClosed(1, 5).mapToObj(ControllerTestUtils::generateExecutorNode).toList());
        val le = mock(LeadershipEnsurer.class);
        when(le.isLeader()).thenReturn(true);

        val comm = mock(ControllerCommunicator.class);

        val taskSpec = taskSpec();
        when(comm.send(any(StopTaskMessage.class))).thenAnswer(invocationOnMock -> {
            val msg = invocationOnMock.getArgument(0, StopTaskMessage.class);
            pair.getKey().updateTask(taskSpec.getSourceAppName(),
                           taskSpec.getTaskId(),
                           new TaskInfo(taskSpec.getSourceAppName(),
                                        taskSpec.getTaskId(),
                                        "TI00001",
                                        "EXECUTOR_5",
                                        "localhost",
                                        taskSpec.getExecutable(),
                                        null,
                                        taskSpec.getVolumes(),
                                        taskSpec.getLogging(),
                                        taskSpec.getEnv(),
                                        STOPPED,
                                        Map.of(),
                                        new TaskResult(TaskResult.Status.SUCCESSFUL, 0),
                                        "",
                                        new Date(),
                                        new Date()));
            return new MessageResponse(msg.getHeader(), MessageDeliveryStatus.ACCEPTED);
        });
        pair.getKey().updateTask(taskSpec.getSourceAppName(),
                       taskSpec.getTaskId(),
                       new TaskInfo(taskSpec.getSourceAppName(),
                                    taskSpec.getTaskId(),
                                    "TI00001",
                                    "EXECUTOR_5",
                                    "localhost",
                                    taskSpec.getExecutable(),
                                    null,
                                    taskSpec.getVolumes(),
                                    taskSpec.getLogging(),
                                    taskSpec.getEnv(),
                                    RUNNING,
                                    Map.of(),
                                    new TaskResult(TaskResult.Status.SUCCESSFUL, 0),
                                    "",
                                    new Date(),
                                    new Date()));
        val executor = Executors.newCachedThreadPool();
        val te = new TaskEngine(pair.getKey(),
                                cdb,
                                pair.getValue(),
                                comm,
                                new DefaultControllerRetrySpecFactory(),
                                new RandomInstanceIdGenerator(),
                                ControllerTestUtils.defaultEnforcer(),
                                ControllerTestUtils.placementRuleEvaluator(MAPPER),
                                Executors.defaultThreadFactory(),
                                executor,
                                new JobExecutor<>(executor),
                                new InMemoryClusterStateDB(),
                                le,
                                ControllerTestUtils.DEFAULT_CLUSTER_OP,
                                httpCaller());
        te.handleZombieTask(taskSpec.getSourceAppName(), taskSpec.getTaskId());
        CommonTestUtils.waitUntil(() -> pair.getKey().task(taskSpec.getSourceAppName(), taskSpec.getTaskId())
                .map(TaskInfo::getState)
                .filter(STOPPED::equals)
                .isPresent());
        assertTrue(pair.getKey().task(taskSpec.getSourceAppName(), taskSpec.getTaskId())
                           .map(TaskInfo::getState)
                           .filter(STOPPED::equals)
                           .isPresent());
    }

    @Test
    void testRunRecovery() {
        val cdb = getCdb();
        val pair = createDefaultInstanceScheduler(cdb);
        cdb.update(IntStream.rangeClosed(1, 5).mapToObj(ControllerTestUtils::generateExecutorNode).toList());
        val le = mock(LeadershipEnsurer.class);
        when(le.isLeader()).thenReturn(true);

        val comm = mock(ControllerCommunicator.class);
        val taskSpec = taskSpec();
        when(comm.send(any(StopTaskMessage.class))).thenAnswer(invocationOnMock -> {
            val msg = invocationOnMock.getArgument(0, StopTaskMessage.class);
            pair.getKey().updateTask(taskSpec.getSourceAppName(),
                           taskSpec.getTaskId(),
                           new TaskInfo(taskSpec.getSourceAppName(),
                                        taskSpec.getTaskId(),
                                        "TI00001",
                                        "EXECUTOR_5",
                                        "localhost",
                                        taskSpec.getExecutable(),
                                        null,
                                        taskSpec.getVolumes(),
                                        taskSpec.getLogging(),
                                        taskSpec.getEnv(),
                                        STOPPED,
                                        Map.of(),
                                        null,
                                        "",
                                        new Date(),
                                        new Date()));
            return new MessageResponse(msg.getHeader(), MessageDeliveryStatus.ACCEPTED);
        });
        val executor = Executors.newCachedThreadPool();
        val te = new TaskEngine(pair.getKey(),
                                cdb,
                                pair.getValue(),
                                comm,
                                new DefaultControllerRetrySpecFactory(),
                                new RandomInstanceIdGenerator(),
                                ControllerTestUtils.defaultEnforcer(),
                                ControllerTestUtils.placementRuleEvaluator(MAPPER),
                                Executors.defaultThreadFactory(),
                                executor,
                                new JobExecutor<>(executor),
                                new InMemoryClusterStateDB(),
                                le,
                                ControllerTestUtils.DEFAULT_CLUSTER_OP,
                                httpCaller());
        te.registerTaskRunner(taskSpec.getSourceAppName(), taskSpec.getTaskId());
        pair.getKey().updateTask(taskSpec.getSourceAppName(),
                       taskSpec.getTaskId(),
                       new TaskInfo(taskSpec.getSourceAppName(),
                                    taskSpec.getTaskId(),
                                    "TI00001",
                                    "EXECUTOR_5",
                                    "localhost",
                                    taskSpec.getExecutable(),
                                    null,
                                    taskSpec.getVolumes(),
                                    taskSpec.getLogging(),
                                    taskSpec.getEnv(),
                                    RUNNING,
                                    Map.of(),
                                    null,
                                    "",
                                    new Date(),
                                    new Date()));
        CommonTestUtils.waitUntil(() -> !te.activeTasks().isEmpty());
        val task = te.activeTasks().get(0);
        te.handleTaskOp(new TaskKillOperation(task.getSourceAppName(),
                                              task.getTaskId(),
                                              ControllerTestUtils.DEFAULT_CLUSTER_OP));
        CommonTestUtils.waitUntil(() -> te.activeTasks().isEmpty());
        assertTrue(te.activeTasks().isEmpty());
    }

    @Test
    void testTaskStartFailIdCollision() {
        val cdb = getCdb();
        val pair = createDefaultInstanceScheduler(cdb);
        cdb.update(IntStream.rangeClosed(1, 5).mapToObj(ControllerTestUtils::generateExecutorNode).toList());
        val le = mock(LeadershipEnsurer.class);
        when(le.isLeader()).thenReturn(true);

        val comm = mock(ControllerCommunicator.class);
        val taskSpec = taskSpec();
        pair.getKey().updateTask(taskSpec.getSourceAppName(),
                       taskSpec.getTaskId(),
                       new TaskInfo(taskSpec.getSourceAppName(),
                                    taskSpec.getTaskId(),
                                    "TI00001",
                                    "EXECUTOR_5",
                                    "localhost",
                                    taskSpec.getExecutable(),
                                    null,
                                    taskSpec.getVolumes(),
                                    taskSpec.getLogging(),
                                    taskSpec.getEnv(),
                                    STOPPED,
                                    Map.of(),
                                    new TaskResult(TaskResult.Status.SUCCESSFUL, 0),
                                    "",
                                    new Date(),
                                    new Date()));
        val executor = Executors.newCachedThreadPool();
        val te = new TaskEngine(pair.getKey(),
                                cdb,
                                pair.getValue(),
                                comm,
                                new DefaultControllerRetrySpecFactory(),
                                new RandomInstanceIdGenerator(),
                                ControllerTestUtils.defaultEnforcer(),
                                ControllerTestUtils.placementRuleEvaluator(MAPPER),
                                Executors.defaultThreadFactory(),
                                executor,
                                new JobExecutor<>(executor),
                                new InMemoryClusterStateDB(),
                                le,
                                ControllerTestUtils.DEFAULT_CLUSTER_OP,
                                httpCaller());
        val r = te.handleTaskOp(new TaskCreateOperation(taskSpec, ControllerTestUtils.DEFAULT_CLUSTER_OP));
        assertEquals(ValidationStatus.FAILURE, r.getStatus());
        assertEquals(
                "Task already exists for TEST_TASK_SPEC/TEST_TASK_SPEC00001 with taskID: " +
                        "TEST_TASK_SPEC-TEST_TASK_SPEC00001",
                r.getMessages().get(0));
    }

    @Test
    void testTaskStartFailNoResource() {
        val cdb = getCdb();
        val pair = createDefaultInstanceScheduler(cdb);
        cdb.update(IntStream.rangeClosed(1, 5).mapToObj(ControllerTestUtils::generateExecutorNode).toList());
        val le = mock(LeadershipEnsurer.class);
        when(le.isLeader()).thenReturn(true);

        val comm = mock(ControllerCommunicator.class);
        val taskSpec = taskSpec()
                .withResources(List.of(new CPURequirement(10000), new MemoryRequirement(512000000)));
        pair.getKey().updateTask(taskSpec.getSourceAppName(),
                       taskSpec.getTaskId(),
                       new TaskInfo(taskSpec.getSourceAppName(),
                                    taskSpec.getTaskId(),
                                    "TI00001",
                                    "EXECUTOR_5",
                                    "localhost",
                                    taskSpec.getExecutable(),
                                    null,
                                    taskSpec.getVolumes(),
                                    taskSpec.getLogging(),
                                    taskSpec.getEnv(),
                                    STOPPED,
                                    Map.of(),
                                    new TaskResult(TaskResult.Status.SUCCESSFUL, 0),
                                    "",
                                    new Date(),
                                    new Date()));
        val executor = Executors.newCachedThreadPool();
        val te = new TaskEngine(pair.getKey(),
                                cdb,
                                pair.getValue(),
                                comm,
                                new DefaultControllerRetrySpecFactory(),
                                new RandomInstanceIdGenerator(),
                                ControllerTestUtils.defaultEnforcer(),
                                ControllerTestUtils.placementRuleEvaluator(MAPPER),
                                Executors.defaultThreadFactory(),
                                executor,
                                new JobExecutor<>(executor),
                                new InMemoryClusterStateDB(),
                                le,
                                ControllerTestUtils.DEFAULT_CLUSTER_OP,
                                httpCaller());
        val r = te.handleTaskOp(new TaskCreateOperation(taskSpec, ControllerTestUtils.DEFAULT_CLUSTER_OP));
        assertEquals(ValidationStatus.FAILURE, r.getStatus());
        assertTrue(r.getMessages().stream().anyMatch(m -> m.contains("Cluster can place only 0 of 1")));
        assertTrue(r.getMessages().stream().anyMatch(m -> m.contains("Required cores exceed the maximum free cores on a single")));
    }

    @NotNull
    private static InMemoryClusterResourcesDB getCdb() {
        return ControllerTestUtils.clusterResourcesDB();
    }

    @Test
    void testTaskStartFailNoWhitelisting() {
        val cdb = getCdb();
        val pair = createDefaultInstanceScheduler(cdb);
        cdb.update(IntStream.rangeClosed(1, 5).mapToObj(ControllerTestUtils::generateExecutorNode).toList());
        val le = mock(LeadershipEnsurer.class);
        when(le.isLeader()).thenReturn(true);

        val comm = mock(ControllerCommunicator.class);
        val taskSpec = taskSpec()
                .withVolumes(List.of(new MountedVolume("/etc", "/etc", MountedVolume.MountMode.READ_WRITE)));
        pair.getKey().updateTask(taskSpec.getSourceAppName(),
                       taskSpec.getTaskId(),
                       new TaskInfo(taskSpec.getSourceAppName(),
                                    taskSpec.getTaskId(),
                                    "TI00001",
                                    "EXECUTOR_5",
                                    "localhost",
                                    taskSpec.getExecutable(),
                                    null,
                                    taskSpec.getVolumes(),
                                    taskSpec.getLogging(),
                                    taskSpec.getEnv(),
                                    STOPPED,
                                    Map.of(),
                                    new TaskResult(TaskResult.Status.SUCCESSFUL, 0),
                                    "",
                                    new Date(),
                                    new Date()));
        val executor = Executors.newCachedThreadPool();
        val securityEnforcer = new SecurityPolicyEnforcer(List.of(),
                ControllerOptions.DEFAULT
                    .withAllowedMountDirs(List.of("/tmp")));
        val te = new TaskEngine(pair.getKey(),
                                cdb,
                                pair.getValue(),
                                comm,
                                new DefaultControllerRetrySpecFactory(),
                                new RandomInstanceIdGenerator(),
                                securityEnforcer,
                                ControllerTestUtils.placementRuleEvaluator(MAPPER),
                                Executors.defaultThreadFactory(),
                                executor,
                                new JobExecutor<>(executor),
                                new InMemoryClusterStateDB(),
                                le,
                                ControllerTestUtils.DEFAULT_CLUSTER_OP,
                                httpCaller());
        val r = te.handleTaskOp(new TaskCreateOperation(taskSpec, ControllerTestUtils.DEFAULT_CLUSTER_OP));
        assertEquals(ValidationStatus.FAILURE, r.getStatus());
        assertEquals("Volume mount requested on non whitelisted host directory: /etc", r.getMessages().get(0));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testTaskStartSchedFail() {
        val cdb = getCdb();
        val pair = createDefaultInstanceScheduler(cdb);
        cdb.update(IntStream.rangeClosed(1, 5).mapToObj(ControllerTestUtils::generateExecutorNode).toList());
        val le = mock(LeadershipEnsurer.class);
        when(le.isLeader()).thenReturn(true);

        val comm = mock(ControllerCommunicator.class);
        val taskSpec = taskSpec();
        val executor = Executors.newCachedThreadPool();
        val jobSched = (JobExecutor<Boolean>) mock(JobExecutor.class);
        when(jobSched.schedule(any(JobTopology.class), any(), any())).thenReturn(null);
        val te = new TaskEngine(pair.getKey(),
                                cdb,
                                pair.getValue(),
                                comm,
                                new DefaultControllerRetrySpecFactory(),
                                new RandomInstanceIdGenerator(),
                                ControllerTestUtils.defaultEnforcer(),
                                ControllerTestUtils.placementRuleEvaluator(MAPPER),
                                Executors.defaultThreadFactory(),
                                executor,
                                jobSched,
                                new InMemoryClusterStateDB(),
                                le,
                                ControllerTestUtils.DEFAULT_CLUSTER_OP,
                                httpCaller());
        val r = te.handleTaskOp(new TaskCreateOperation(taskSpec, ControllerTestUtils.DEFAULT_CLUSTER_OP));
        assertEquals(ValidationStatus.FAILURE, r.getStatus());
        assertEquals("Could not schedule job to start the task.", r.getMessages().get(0));
    }

    @Test
    void testTaskKillFail() {
        val cdb = getCdb();
        val pair = createDefaultInstanceScheduler(cdb);
        cdb.update(IntStream.rangeClosed(1, 5).mapToObj(ControllerTestUtils::generateExecutorNode).toList());
        val le = mock(LeadershipEnsurer.class);
        when(le.isLeader()).thenReturn(true);

        val comm = mock(ControllerCommunicator.class);
        val taskSpec = taskSpec();
        val executor = Executors.newCachedThreadPool();
        val te = new TaskEngine(pair.getKey(),
                                cdb,
                                pair.getValue(),
                                comm,
                                new DefaultControllerRetrySpecFactory(),
                                new RandomInstanceIdGenerator(),
                                ControllerTestUtils.defaultEnforcer(),
                                ControllerTestUtils.placementRuleEvaluator(MAPPER),
                                Executors.defaultThreadFactory(),
                                executor,
                                new JobExecutor<>(executor),
                                new InMemoryClusterStateDB(),
                                le,
                                ControllerTestUtils.DEFAULT_CLUSTER_OP,
                                httpCaller());
        val r = te.handleTaskOp(new TaskKillOperation(taskSpec.getSourceAppName(),
                                                      taskSpec.getTaskId(),
                                                      ControllerTestUtils.DEFAULT_CLUSTER_OP));
        assertEquals(ValidationStatus.FAILURE, r.getStatus());
        assertEquals("Either task does not exist or has already finished for TEST_TASK_SPEC/TEST_TASK_SPEC00001",
                     r.getMessages().get(0));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testTaskKillSchedFail() {
        val cdb = getCdb();
        val pair = createDefaultInstanceScheduler(cdb);
        var taskDB = mock(TaskDB.class);

        cdb.update(IntStream.rangeClosed(1, 5).mapToObj(ControllerTestUtils::generateExecutorNode).toList());
        val le = mock(LeadershipEnsurer.class);
        when(le.isLeader()).thenReturn(true);

        val comm = mock(ControllerCommunicator.class);
        val taskSpec = taskSpec();
        val executor = Executors.newCachedThreadPool();
        val jobSched = (JobExecutor<Boolean>) mock(JobExecutor.class);
        when(jobSched.schedule(any(JobTopology.class), any(), any())).thenReturn(null);
        when(taskDB.task(taskSpec.getSourceAppName(), taskSpec.getTaskId()))
                .thenReturn(Optional.of(
                        new TaskInfo(taskSpec.getSourceAppName(),
                                     taskSpec.getTaskId(),
                                     "TI00001",
                                     "EXECUTOR_5",
                                     "localhost",
                                     taskSpec.getExecutable(),
                                     null,
                                     taskSpec.getVolumes(),
                                     taskSpec.getLogging(),
                                     taskSpec.getEnv(),
                                     RUNNING,
                                     Map.of(),
                                     null,
                                     "",
                                     new Date(),
                                     new Date())));
        when(taskDB.onStateChange()).thenReturn(new ConsumingFireForgetSignal<>());
        val te = new TaskEngine(pair.getKey(),
                                cdb,
                                pair.getValue(),
                                comm,
                                new DefaultControllerRetrySpecFactory(),
                                new RandomInstanceIdGenerator(),
                                ControllerTestUtils.defaultEnforcer(),
                                ControllerTestUtils.placementRuleEvaluator(MAPPER),
                                Executors.defaultThreadFactory(),
                                executor,
                                jobSched,
                                new InMemoryClusterStateDB(),
                                le,
                                ControllerTestUtils.DEFAULT_CLUSTER_OP,
                                httpCaller());
        te.registerTaskRunner(taskSpec.getSourceAppName(), taskSpec.getTaskId());

        val r = te.handleTaskOp(new TaskKillOperation(taskSpec.getSourceAppName(),
                                                      taskSpec.getTaskId(),
                                                      ControllerTestUtils.DEFAULT_CLUSTER_OP));
        assertEquals(ValidationStatus.FAILURE, r.getStatus());
        assertEquals("Could not schedule job to stop the task.", r.getMessages().get(0));
    }

    @Test
    void testResourceCrunch() {
        val cdb = getCdb();
        val pair = createDefaultInstanceScheduler(cdb);
        cdb.update(List.of(new ExecutorNodeData("poor-host",
                                                8080,
                                                NodeTransportType.HTTP,
                                                new Date(),
                                                new ExecutorResourceSnapshot("PE1",
                                                                             new AvailableCPU(Map.of(),
                                                                                              Map.of()),
                                                                             new AvailableMemory(
                                                                                     Map.of(),
                                                                                     Map.of()),
                                                                             new AvailableGPU(List.of(), List.of()),
                                                                             new PhysicalLayout(Map.of(),
                                                                                                Map.of(),
                                                                                                Map.of(),
                                                                                                PhysicalLayout.DEFAULT_CPU_SCALING_FACTOR,
                                                                                                PhysicalLayout.DEFAULT_MEMORY_SCALING_FACTOR)),
                                                List.of(),
                                                List.of(),
                                                List.of(),
                                                Set.of(),
                                                Map.of(),
                                                ExecutorState.ACTIVE)));

        val le = mock(LeadershipEnsurer.class);
        when(le.isLeader()).thenReturn(true);

        val comm = mock(ControllerCommunicator.class);
        val executor = Executors.newCachedThreadPool();
        val te = new TaskEngine(pair.getKey(),
                                cdb,
                                pair.getValue(),
                                comm,
                                new DefaultControllerRetrySpecFactory(),
                                new RandomInstanceIdGenerator(),
                                ControllerTestUtils.defaultEnforcer(),
                                ControllerTestUtils.placementRuleEvaluator(MAPPER),
                                Executors.defaultThreadFactory(),
                                executor,
                                new JobExecutor<>(executor),
                                new InMemoryClusterStateDB(),
                                le,
                                ControllerTestUtils.DEFAULT_CLUSTER_OP,
                                httpCaller());
        val r = te.handleTaskOp(new TaskCreateOperation(ControllerTestUtils.withGPU(ControllerTestUtils.taskSpec(), 1, new GpuType(GpuVendor.NVIDIA, "h100")),
                                                        ControllerTestUtils.DEFAULT_CLUSTER_OP));
        assertEquals(ValidationStatus.FAILURE, r.getStatus());
        assertEquals(5, r.getMessages().size());
    }

    @Test
    void testTaskValidation() {
        val cdb = getCdb();
        val pair = createDefaultInstanceScheduler(cdb);

        // Setup 2 nodes:
        // 1. Generic node (no tags, limited resources)
        // 2. GPU node (tag=GPU, high resources)
        cdb.update(List.of(
                new ExecutorNodeData("generic-host",
                                     8080,
                                     NodeTransportType.HTTP,
                                     new Date(),
                                     new ExecutorResourceSnapshot("GEN1",
                                                                  new AvailableCPU(Map.of(0, Set.of(0, 1)),
                                                                                   Map.of(0, Set.of())),
                                                                  new AvailableMemory(
                                                                          Map.of(0, 2048L),
                                                                          Map.of(0, 0L)),
                                                                  new AvailableGPU(List.of("GPU-1"), List.of("GPU-2")),
                                                                   new PhysicalLayout(Map.of(0, Set.of(0, 1)),
                                                                                      Map.of(0, 2048L),
                                                                                      Map.of(),
                                                                                      PhysicalLayout.DEFAULT_CPU_SCALING_FACTOR,
                                                                                      PhysicalLayout.DEFAULT_MEMORY_SCALING_FACTOR)),
                                     List.of(),
                                     List.of(),
                                     List.of(),
                                     Set.of(), // No tags
                                     Map.of(),
                                     ExecutorState.ACTIVE),
                new ExecutorNodeData("gpu-host",
                                     8081,
                                     NodeTransportType.HTTP,
                                     new Date(),
                                     new ExecutorResourceSnapshot("GPU1",
                                                                  new AvailableCPU(Map.of(0, Set.of(0, 1, 2, 3)),
                                                                                   Map.of(0, Set.of())),
                                                                  new AvailableMemory(
                                                                          Map.of(0, 16384L),
                                                                          Map.of(0, 0L)),
                                                                  new AvailableGPU(List.of("GPU-1"),
                                                                                   List.of("GPU-2")),
                                                                   new PhysicalLayout(Map.of(0, Set.of(0, 1, 2, 3)),
                                                                                      Map.of(0, 16384L),
                                                                                      Map.of(),
                                                                                      PhysicalLayout.DEFAULT_CPU_SCALING_FACTOR,
                                                                                      PhysicalLayout.DEFAULT_MEMORY_SCALING_FACTOR)),
                                     List.of(),
                                     List.of(),
                                     List.of(),
                                     Set.of("GPU"), // Tagged
                                     Map.of(),
                                     ExecutorState.ACTIVE)
                          ));

        val le = mock(LeadershipEnsurer.class);
        when(le.isLeader()).thenReturn(true);
        val comm = mock(ControllerCommunicator.class);
        val executor = Executors.newCachedThreadPool();
        val te = new TaskEngine(pair.getKey(),
                                cdb,
                                pair.getValue(),
                                comm,
                                new DefaultControllerRetrySpecFactory(),
                                new RandomInstanceIdGenerator(),
                                ControllerTestUtils.defaultEnforcer(),
                                ControllerTestUtils.placementRuleEvaluator(MAPPER),
                                Executors.defaultThreadFactory(),
                                executor,
                                new JobExecutor<>(executor),
                                new InMemoryClusterStateDB(),
                                le,
                                ControllerTestUtils.DEFAULT_CLUSTER_OP,
                                httpCaller());

        // Case 1: Small task, no placement policy -> Should fit on generic-host (implied NoTag)
        {
            val spec = taskSpec()
                    .withResources(List.of(new CPURequirement(1), new MemoryRequirement(512)));
            val r = te.validateSpec(spec);
            assertEquals(ValidationStatus.SUCCESS, r.getStatus(), "Small task should succeed on generic host");
        }

        // Case 2: Large task, no placement policy -> Should fail (implied NoTag, generic host too small)
        // Even though gpu-host has resources, it's tagged, so implicit NoTag excludes it.
        {
            val spec = taskSpec()
                    .withResources(List.of(new CPURequirement(4), new MemoryRequirement(8192)));
            val r = te.validateSpec(spec);
            assertEquals(ValidationStatus.FAILURE, r.getStatus(), "Large task without tag should fail");
            assertTrue(r.getMessages().stream().anyMatch(m -> m.contains("Cluster can place only 0 of 1")));
        }

        // Case 3: Large task, explicit MATCH_TAG=GPU -> Should succeed on gpu-host
        {
            val spec = taskSpec()
                    .withResources(List.of(new CPURequirement(4), new MemoryRequirement(8192)))
                    .withPlacementPolicy(new MatchTagPlacementPolicy("GPU"));
            val r = te.validateSpec(spec);
            assertEquals(ValidationStatus.SUCCESS,
                         r.getStatus(),
                         "Large task with GPU tag should succeed on gpu host");
        }

        // Case 4: Moderate task, MATCH_TAG=NON_EXISTENT -> Should fail
        {
            val spec = taskSpec()
                    .withResources(List.of(new CPURequirement(1), new MemoryRequirement(512)))
                    .withPlacementPolicy(new MatchTagPlacementPolicy("NON_EXISTENT"));
            val r = te.validateSpec(spec);
            assertEquals(ValidationStatus.FAILURE, r.getStatus(), "Task with non-existent tag should fail");
            assertTrue(r.getMessages().stream().anyMatch(m -> m.contains("Cluster can place only 0 of 1"))); // Filtered out all nodes -> 0 placed
        }

        // Case 5: Huge task, MATCH_TAG=GPU -> Should fail (gpu-host has resources, but not ENOUGH)
        {
            val spec = taskSpec()
                    .withResources(List.of(new CPURequirement(100), new MemoryRequirement(100000))) // excessively large
                    .withPlacementPolicy(new MatchTagPlacementPolicy("GPU"));
            val r = te.validateSpec(spec);
            assertEquals(ValidationStatus.FAILURE, r.getStatus(), "Huge task should fail even on tagged host");
            assertTrue(r.getMessages().stream().anyMatch(m -> m.contains("Cluster can place only 0 of 1")));
        }
    }

    @Test
    void testValidateTaskSpecFailsForBrokenPlacementRule() {
        val cdb = getCdb();
        val pair = createDefaultInstanceScheduler(cdb);
        cdb.update(IntStream.rangeClosed(1, 5).mapToObj(ControllerTestUtils::generateExecutorNode).toList());
        val le = mock(LeadershipEnsurer.class);
        when(le.isLeader()).thenReturn(true);
        val comm = mock(ControllerCommunicator.class);
        val executor = Executors.newCachedThreadPool();
        val te = new TaskEngine(pair.getKey(),
                                cdb,
                                pair.getValue(),
                                comm,
                                new DefaultControllerRetrySpecFactory(),
                                new RandomInstanceIdGenerator(),
                                ControllerTestUtils.defaultEnforcer(),
                                ControllerTestUtils.placementRuleEvaluator(MAPPER),
                                Executors.defaultThreadFactory(),
                                executor,
                                new JobExecutor<>(executor),
                                new InMemoryClusterStateDB(),
                                le,
                                ControllerTestUtils.DEFAULT_CLUSTER_OP,
                                httpCaller());

        val spec = taskSpec()
                .withPlacementPolicy(new RuleBasedPlacementPolicy("(true &&"));
        val r = te.validateSpec(spec);
        assertEquals(ValidationStatus.FAILURE, r.getStatus());
        assertTrue(r.getMessages().stream().anyMatch(m -> m.startsWith("Placement rule failed to compile: (true && - ")));
    }

    @Test
    void testValidateTaskSpecPassesForValidPlacementRule() {
        val cdb = getCdb();
        val pair = createDefaultInstanceScheduler(cdb);
        cdb.update(IntStream.rangeClosed(1, 5).mapToObj(ControllerTestUtils::generateExecutorNode).toList());
        val le = mock(LeadershipEnsurer.class);
        when(le.isLeader()).thenReturn(true);
        val comm = mock(ControllerCommunicator.class);
        val executor = Executors.newCachedThreadPool();
        val te = new TaskEngine(pair.getKey(),
                                cdb,
                                pair.getValue(),
                                comm,
                                new DefaultControllerRetrySpecFactory(),
                                new RandomInstanceIdGenerator(),
                                ControllerTestUtils.defaultEnforcer(),
                                ControllerTestUtils.placementRuleEvaluator(MAPPER),
                                Executors.defaultThreadFactory(),
                                executor,
                                new JobExecutor<>(executor),
                                new InMemoryClusterStateDB(),
                                le,
                                ControllerTestUtils.DEFAULT_CLUSTER_OP,
                                httpCaller());

        val spec = taskSpec()
                .withPlacementPolicy(new RuleBasedPlacementPolicy("ctx.allocation.node.executorId != 'excluded'"));
        val r = te.validateSpec(spec);
        assertEquals(ValidationStatus.SUCCESS, r.getStatus(), () -> "Messages: " + r.getMessages());
    }



    @Test
    void testCreateTaskMultipleGpuRequirements() {
        val cdb = getCdb();
        val pair = createDefaultInstanceScheduler(cdb);
        cdb.update(IntStream.rangeClosed(1, 5)
                           .mapToObj(ControllerTestUtils::generateExecutorNode)
                           .toList());
        val le = mock(LeadershipEnsurer.class);
        when(le.isLeader()).thenReturn(true);
        val comm = mock(ControllerCommunicator.class);
        val taskSpec = taskSpec()
                .withResources(List.of(new CPURequirement(1),
                                       new MemoryRequirement(512),
                                       new GPURequirement(1, new GpuType(GpuVendor.NVIDIA, "H100")),
                                       new GPURequirement(1, new GpuType(GpuVendor.NVIDIA, "A100"))));
        val executor = Executors.newCachedThreadPool();
        val te = new TaskEngine(pair.getKey(),
                                cdb,
                                pair.getValue(),
                                comm,
                                new DefaultControllerRetrySpecFactory(),
                                new RandomInstanceIdGenerator(),
                                ControllerTestUtils.defaultEnforcer(),
                                ControllerTestUtils.placementRuleEvaluator(MAPPER),
                                Executors.defaultThreadFactory(),
                                executor,
                                new JobExecutor<>(executor),
                                new InMemoryClusterStateDB(),
                                le,
                                ControllerTestUtils.DEFAULT_CLUSTER_OP,
                                httpCaller());
        val r = te.handleTaskOp(new TaskCreateOperation(taskSpec, ControllerTestUtils.DEFAULT_CLUSTER_OP));
        assertEquals(ValidationStatus.FAILURE, r.getStatus());
        assertTrue(r.getMessages().stream()
                           .anyMatch(m -> m.contains("Multiple GPU requirements specified in resource requirements.")));
    }

    @Test
    void testCreateTaskMultipleGpuRequirementsCollectsAllErrors() {
        val cdb = getCdb();
        val pair = createDefaultInstanceScheduler(cdb);
        cdb.update(IntStream.rangeClosed(1, 5)
                           .mapToObj(ControllerTestUtils::generateExecutorNode)
                           .toList());
        val le = mock(LeadershipEnsurer.class);
        when(le.isLeader()).thenReturn(true);
        val comm = mock(ControllerCommunicator.class);
        val taskSpec = taskSpec()
                .withResources(List.of(new CPURequirement(1),
                                       new MemoryRequirement(512),
                                       new GPURequirement(1, new GpuType(GpuVendor.NVIDIA, "H100")),
                                       new GPURequirement(1, new GpuType(GpuVendor.NVIDIA, "A100"))))
                .withPlacementPolicy(new LocalPlacementPolicy(true));
        val executor = Executors.newCachedThreadPool();
        val te = new TaskEngine(pair.getKey(),
                                cdb,
                                pair.getValue(),
                                comm,
                                new DefaultControllerRetrySpecFactory(),
                                new RandomInstanceIdGenerator(),
                                ControllerTestUtils.defaultEnforcer(),
                                ControllerTestUtils.placementRuleEvaluator(MAPPER),
                                Executors.defaultThreadFactory(),
                                executor,
                                new JobExecutor<>(executor),
                                new InMemoryClusterStateDB(),
                                le,
                                ControllerTestUtils.DEFAULT_CLUSTER_OP,
                                httpCaller());
        val r = te.handleTaskOp(new TaskCreateOperation(taskSpec, ControllerTestUtils.DEFAULT_CLUSTER_OP));
        assertEquals(ValidationStatus.FAILURE, r.getStatus());
        assertTrue(r.getMessages().stream()
                           .anyMatch(m -> m.contains("Multiple GPU requirements specified in resource requirements.")));
        assertTrue(r.getMessages().stream()
                           .anyMatch(m -> m.contains("Local service placement is not allowed for tasks")));
    }


    @Test
    void testCreateTaskCustomPolicyBlocksDevice() {
        val cdb = getCdb();
        val pair = createDefaultInstanceScheduler(cdb);
        cdb.update(IntStream.rangeClosed(1, 5)
                .mapToObj(ControllerTestUtils::generateExecutorNode)
                .toList());
        val le = mock(LeadershipEnsurer.class);
        when(le.isLeader()).thenReturn(true);
        val comm = mock(ControllerCommunicator.class);
        val taskSpec = taskSpec()
                .withDevices(List.of(DirectDeviceSpec.builder()
                                .pathOnHost("/dev/sda")
                                .build()));
        val profile = ContainerSecurityProfile.builder()
                .policyName("no-device")
                .enableRawDeviceAccess(false)
                .build();
        val config = ContainerSecurityPolicyConfig.builder()
                .match(List.of("COMPUTATION/*"))
                .profile(profile)
                .build();
        val securityEnforcer = new SecurityPolicyEnforcer(List.of(config), ControllerOptions.DEFAULT);
        val executor = Executors.newCachedThreadPool();
        val te = new TaskEngine(pair.getKey(),
                                cdb,
                                pair.getValue(),
                                comm,
                                new DefaultControllerRetrySpecFactory(),
                                new RandomInstanceIdGenerator(),
                                securityEnforcer,
                                ControllerTestUtils.placementRuleEvaluator(MAPPER),
                                Executors.defaultThreadFactory(),
                                executor,
                                new JobExecutor<>(executor),
                                new InMemoryClusterStateDB(),
                                le,
                                ControllerTestUtils.DEFAULT_CLUSTER_OP,
                                httpCaller());
        val r = te.handleTaskOp(new TaskCreateOperation(taskSpec, ControllerTestUtils.DEFAULT_CLUSTER_OP));
        assertEquals(ValidationStatus.FAILURE, r.getStatus());
        assertTrue(r.getMessages().get(0).contains("Device access is disabled"));
    }

    @Test
    void testCreateTaskCustomPolicyAllowsVolume() {
        val cdb = getCdb();
        val pair = createDefaultInstanceScheduler(cdb);
        cdb.update(IntStream.rangeClosed(1, 5).mapToObj(ControllerTestUtils::generateExecutorNode).toList());
        val le = mock(LeadershipEnsurer.class);
        when(le.isLeader()).thenReturn(true);
        val comm = mock(ControllerCommunicator.class);
        // /var/log volume — blocked by default but allowed by custom policy
        val taskSpec = taskSpec()
                .withVolumes(List.of(new MountedVolume("/var/log", "/var/log",
                                                       MountedVolume.MountMode.READ_ONLY)));
        val profile = ContainerSecurityProfile.builder()
                .policyName("allow-var-log")
                .allowedMountDirs(List.of("/var/log"))
                .build();
        val config = ContainerSecurityPolicyConfig.builder()
                .match(List.of("COMPUTATION/*"))
                .profile(profile)
                .build();
        val securityEnforcer = new SecurityPolicyEnforcer(
                List.of(config), ControllerOptions.DEFAULT.withAllowedMountDirs(List.of("/tmp")));
        when(comm.send(any(StartTaskMessage.class)))
                .thenReturn(new MessageResponse(null, MessageDeliveryStatus.ACCEPTED));
        val executor = Executors.newCachedThreadPool();
        val te = new TaskEngine(pair.getKey(),
                                cdb,
                                pair.getValue(),
                                comm,
                                new DefaultControllerRetrySpecFactory(),
                                new RandomInstanceIdGenerator(),
                                securityEnforcer,
                                ControllerTestUtils.placementRuleEvaluator(MAPPER),
                                Executors.defaultThreadFactory(),
                                executor,
                                new JobExecutor<>(executor),
                                new InMemoryClusterStateDB(),
                                le,
                                ControllerTestUtils.DEFAULT_CLUSTER_OP,
                                httpCaller());
        val r = te.handleTaskOp(new TaskCreateOperation(taskSpec, ControllerTestUtils.DEFAULT_CLUSTER_OP));
        // Validation must pass (no volume error), even though taskSpec has /var/log
        assertNotEquals("Volume mount requested on non whitelisted host directory: /var/log",
                        r.getMessages().isEmpty() ? "" : r.getMessages().get(0));
    }
}


