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

package com.phonepe.drove.controller.statemachine.localservice.actions;

import static com.phonepe.drove.controller.ControllerTestUtils.DEFAULT_CLUSTER_OP;
import static com.phonepe.drove.controller.ControllerTestUtils.localServiceSpec;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;

import com.phonepe.drove.auth.core.ApplicationInstanceTokenManager;
import com.phonepe.drove.common.net.HttpCaller;
import com.phonepe.drove.controller.ControllerTestUtils;
import com.phonepe.drove.controller.engine.ControllerCommunicator;
import com.phonepe.drove.controller.engine.ControllerRetrySpecFactory;
import com.phonepe.drove.controller.engine.InstanceIdGenerator;
import com.phonepe.drove.controller.resourcemgmt.ClusterResourcesDB;
import com.phonepe.drove.controller.resourcemgmt.InstanceScheduler;
import com.phonepe.drove.controller.statedb.LocalServiceStateDB;
import com.phonepe.drove.controller.statemachine.localservice.LocalServiceActionContext;
import com.phonepe.drove.controller.utils.ControllerUtils;
import com.phonepe.drove.jobexecutor.JobExecutor;
import com.phonepe.drove.models.info.nodedata.ExecutorState;
import com.phonepe.drove.models.localservice.ActivationState;
import com.phonepe.drove.models.localservice.LocalServiceInfo;
import com.phonepe.drove.models.localservice.LocalServiceInstanceInfo;
import com.phonepe.drove.models.localservice.LocalServiceState;
import com.phonepe.drove.models.operation.localserviceops.LocalServiceAdjustInstancesOperation;
import com.phonepe.drove.statemachine.StateData;

import org.junit.jupiter.api.Test;

import lombok.val;

@SuppressWarnings("unchecked")
class AdjustInstancesLocalServiceActionTest {

    @Test
    void testOnlyActiveAndUnreadyExecutorsAreConsidered() {
        val jobExecutor = mock(JobExecutor.class);
        val stateDB = mock(LocalServiceStateDB.class);
        val clusterResourcesDB = mock(ClusterResourcesDB.class);
        val scheduler = mock(InstanceScheduler.class);
        val communicator = mock(ControllerCommunicator.class);
        val retrySpecFactory = mock(ControllerRetrySpecFactory.class);
        val instanceIdGenerator = mock(InstanceIdGenerator.class);
        val tokenManager = mock(ApplicationInstanceTokenManager.class);
        val httpCaller = mock(HttpCaller.class);

        val action = new AdjustInstancesLocalServiceAction(
                jobExecutor,
                stateDB,
                clusterResourcesDB,
                scheduler,
                communicator,
                retrySpecFactory,
                instanceIdGenerator,
                Executors.defaultThreadFactory(),
                tokenManager,
                httpCaller,
                DEFAULT_CLUSTER_OP);

        val spec = localServiceSpec();
        val serviceId = ControllerUtils.deployableObjectId(spec);

        val activeExecutor = ControllerTestUtils.executorHost(1, 8080, List.of(), List.of(), List.of(), ExecutorState.ACTIVE);
        val unreadyExecutor = ControllerTestUtils.executorHost(2, 8081, List.of(), List.of(), List.of(), ExecutorState.UNREADY);
        val blacklistedExecutor = ControllerTestUtils.executorHost(3, 8082, List.of(), List.of(), List.of(), ExecutorState.BLACKLISTED);
        val removedExecutor = ControllerTestUtils.executorHost(4, 8083, List.of(), List.of(), List.of(), ExecutorState.REMOVED);

        when(clusterResourcesDB.currentSnapshot(anyBoolean())).thenReturn(List.of(
                activeExecutor,
                unreadyExecutor,
                blacklistedExecutor,
                removedExecutor
        ));

        when(stateDB.instances(any(), any(), anyBoolean())).thenReturn(List.<LocalServiceInstanceInfo>of());

        val currentState = StateData.create(
                LocalServiceState.ACTIVE,
                new LocalServiceInfo(serviceId,
                                    spec,
                                    2,
                                    ActivationState.ACTIVE,
                                    new Date(),
                                    new Date()));

        val operation = new LocalServiceAdjustInstancesOperation(serviceId, null);
        val context = new LocalServiceActionContext(UUID.randomUUID().toString(), null);

        val result = action.jobsToRun(context, currentState, operation);

        assertTrue(result.isPresent());
    }

    @Test
    void testInstancesOnlyScheduledOnAcceptableExecutors() {
        val jobExecutor = mock(JobExecutor.class);
        val stateDB = mock(LocalServiceStateDB.class);
        val clusterResourcesDB = mock(ClusterResourcesDB.class);
        val scheduler = mock(InstanceScheduler.class);
        val communicator = mock(ControllerCommunicator.class);
        val retrySpecFactory = mock(ControllerRetrySpecFactory.class);
        val instanceIdGenerator = mock(InstanceIdGenerator.class);
        val tokenManager = mock(ApplicationInstanceTokenManager.class);
        val httpCaller = mock(HttpCaller.class);

        val action = new AdjustInstancesLocalServiceAction(
                jobExecutor,
                stateDB,
                clusterResourcesDB,
                scheduler,
                communicator,
                retrySpecFactory,
                instanceIdGenerator,
                Executors.defaultThreadFactory(),
                tokenManager,
                httpCaller,
                DEFAULT_CLUSTER_OP);

        val spec = localServiceSpec();
        val serviceId = ControllerUtils.deployableObjectId(spec);

        val activeExecutor1 = ControllerTestUtils.executorHost(1, 8080, List.of(), List.of(), List.of(), ExecutorState.ACTIVE);
        val activeExecutor2 = ControllerTestUtils.executorHost(2, 8081, List.of(), List.of(), List.of(), ExecutorState.ACTIVE);
        val unreadyExecutor = ControllerTestUtils.executorHost(3, 8082, List.of(), List.of(), List.of(), ExecutorState.UNREADY);
        val blacklistedExecutor = ControllerTestUtils.executorHost(4, 8083, List.of(), List.of(), List.of(), ExecutorState.BLACKLISTED);

        when(clusterResourcesDB.currentSnapshot(anyBoolean())).thenReturn(List.of(
                activeExecutor1,
                activeExecutor2,
                unreadyExecutor,
                blacklistedExecutor
        ));

        when(stateDB.instances(any(), any(), anyBoolean())).thenReturn(List.<LocalServiceInstanceInfo>of());

        val currentState = StateData.create(
                LocalServiceState.ACTIVE,
                new LocalServiceInfo(serviceId,
                                    spec,
                                    2,
                                    ActivationState.ACTIVE,
                                    new Date(),
                                    new Date()));

        val operation = new LocalServiceAdjustInstancesOperation(serviceId, null);
        val context = new LocalServiceActionContext(UUID.randomUUID().toString(), null);

        val result = action.jobsToRun(context, currentState, operation);

        assertTrue(result.isPresent());
    }

    @Test
    void testNoJobsCreatedWhenNoAcceptableExecutors() {
        val jobExecutor = mock(JobExecutor.class);
        val stateDB = mock(LocalServiceStateDB.class);
        val clusterResourcesDB = mock(ClusterResourcesDB.class);
        val scheduler = mock(InstanceScheduler.class);
        val communicator = mock(ControllerCommunicator.class);
        val retrySpecFactory = mock(ControllerRetrySpecFactory.class);
        val instanceIdGenerator = mock(InstanceIdGenerator.class);
        val tokenManager = mock(ApplicationInstanceTokenManager.class);
        val httpCaller = mock(HttpCaller.class);

        val action = new AdjustInstancesLocalServiceAction(
                jobExecutor,
                stateDB,
                clusterResourcesDB,
                scheduler,
                communicator,
                retrySpecFactory,
                instanceIdGenerator,
                Executors.defaultThreadFactory(),
                tokenManager,
                httpCaller,
                DEFAULT_CLUSTER_OP);

        val spec = localServiceSpec();
        val serviceId = ControllerUtils.deployableObjectId(spec);

        val blacklistedExecutor = ControllerTestUtils.executorHost(1, 8080, List.of(), List.of(), List.of(), ExecutorState.BLACKLISTED);
        val removedExecutor = ControllerTestUtils.executorHost(2, 8081, List.of(), List.of(), List.of(), ExecutorState.REMOVED);

        when(clusterResourcesDB.currentSnapshot(anyBoolean())).thenReturn(List.of(
                blacklistedExecutor,
                removedExecutor
        ));

        when(stateDB.instances(any(), any(), anyBoolean())).thenReturn(List.<LocalServiceInstanceInfo>of());

        val currentState = StateData.create(
                LocalServiceState.ACTIVE,
                new LocalServiceInfo(serviceId,
                                    spec,
                                    2,
                                    ActivationState.ACTIVE,
                                    new Date(),
                                    new Date()));

        val operation = new LocalServiceAdjustInstancesOperation(serviceId, null);
        val context = new LocalServiceActionContext(UUID.randomUUID().toString(), null);

        val result = action.jobsToRun(context, currentState, operation);

        assertFalse(result.isPresent());
    }

    @Test
    void testUnreadyExecutorReceivesLocalServiceInstances() {
        val jobExecutor = mock(JobExecutor.class);
        val stateDB = mock(LocalServiceStateDB.class);
        val clusterResourcesDB = mock(ClusterResourcesDB.class);
        val scheduler = mock(InstanceScheduler.class);
        val communicator = mock(ControllerCommunicator.class);
        val retrySpecFactory = mock(ControllerRetrySpecFactory.class);
        val instanceIdGenerator = mock(InstanceIdGenerator.class);
        val tokenManager = mock(ApplicationInstanceTokenManager.class);
        val httpCaller = mock(HttpCaller.class);

        val action = new AdjustInstancesLocalServiceAction(
                jobExecutor,
                stateDB,
                clusterResourcesDB,
                scheduler,
                communicator,
                retrySpecFactory,
                instanceIdGenerator,
                Executors.defaultThreadFactory(),
                tokenManager,
                httpCaller,
                DEFAULT_CLUSTER_OP);

        val spec = localServiceSpec();
        val serviceId = ControllerUtils.deployableObjectId(spec);

        val unreadyExecutor = ControllerTestUtils.executorHost(1, 8080, List.of(), List.of(), List.of(), ExecutorState.UNREADY);

        when(clusterResourcesDB.currentSnapshot(anyBoolean())).thenReturn(List.of(unreadyExecutor));
        when(stateDB.instances(any(), any(), anyBoolean())).thenReturn(List.<LocalServiceInstanceInfo>of());

        val currentState = StateData.create(
                LocalServiceState.ACTIVE,
                new LocalServiceInfo(serviceId,
                                    spec,
                                    1,
                                    ActivationState.ACTIVE,
                                    new Date(),
                                    new Date()));

        val operation = new LocalServiceAdjustInstancesOperation(serviceId, null);
        val context = new LocalServiceActionContext(UUID.randomUUID().toString(), null);

        val result = action.jobsToRun(context, currentState, operation);

        assertTrue(result.isPresent());
    }

    @Test
    void testExtraInstancesOnBlacklistedExecutorsAreNotHandled() {
        val jobExecutor = mock(JobExecutor.class);
        val stateDB = mock(LocalServiceStateDB.class);
        val clusterResourcesDB = mock(ClusterResourcesDB.class);
        val scheduler = mock(InstanceScheduler.class);
        val communicator = mock(ControllerCommunicator.class);
        val retrySpecFactory = mock(ControllerRetrySpecFactory.class);
        val instanceIdGenerator = mock(InstanceIdGenerator.class);
        val tokenManager = mock(ApplicationInstanceTokenManager.class);
        val httpCaller = mock(HttpCaller.class);

        val action = new AdjustInstancesLocalServiceAction(
                jobExecutor,
                stateDB,
                clusterResourcesDB,
                scheduler,
                communicator,
                retrySpecFactory,
                instanceIdGenerator,
                Executors.defaultThreadFactory(),
                tokenManager,
                httpCaller,
                DEFAULT_CLUSTER_OP);

        val spec = localServiceSpec();
        val serviceId = ControllerUtils.deployableObjectId(spec);

        val blacklistedExecutor = ControllerTestUtils.executorHost(1, 8080, List.of(), List.of(), List.of(), ExecutorState.BLACKLISTED);

        when(clusterResourcesDB.currentSnapshot(anyBoolean())).thenReturn(List.of(blacklistedExecutor));
        when(stateDB.instances(any(), any(), anyBoolean())).thenReturn(List.<LocalServiceInstanceInfo>of());

        val currentState = StateData.create(
                LocalServiceState.ACTIVE,
                new LocalServiceInfo(serviceId,
                                    spec,
                                    1,
                                    ActivationState.ACTIVE,
                                    new Date(),
                                    new Date()));

        val operation = new LocalServiceAdjustInstancesOperation(serviceId, null);
        val context = new LocalServiceActionContext(UUID.randomUUID().toString(), null);

        val result = action.jobsToRun(context, currentState, operation);

        assertFalse(result.isPresent());
    }

    @Test
    void testScalingDownOnlyConsidersAcceptableExecutors() {
        val jobExecutor = mock(JobExecutor.class);
        val stateDB = mock(LocalServiceStateDB.class);
        val clusterResourcesDB = mock(ClusterResourcesDB.class);
        val scheduler = mock(InstanceScheduler.class);
        val communicator = mock(ControllerCommunicator.class);
        val retrySpecFactory = mock(ControllerRetrySpecFactory.class);
        val instanceIdGenerator = mock(InstanceIdGenerator.class);
        val tokenManager = mock(ApplicationInstanceTokenManager.class);
        val httpCaller = mock(HttpCaller.class);

        val action = new AdjustInstancesLocalServiceAction(
                jobExecutor,
                stateDB,
                clusterResourcesDB,
                scheduler,
                communicator,
                retrySpecFactory,
                instanceIdGenerator,
                Executors.defaultThreadFactory(),
                tokenManager,
                httpCaller,
                DEFAULT_CLUSTER_OP);

        val spec = localServiceSpec();
        val serviceId = ControllerUtils.deployableObjectId(spec);

        val activeInstance1 = ControllerTestUtils.generateLocalServiceInstanceInfo(spec);
        val activeInstance2 = ControllerTestUtils.generateLocalServiceInstanceInfo(spec);
        val activeInstance3 = ControllerTestUtils.generateLocalServiceInstanceInfo(spec);

        val activeExecutor = ControllerTestUtils.executorHost(
                1, 8080,
                List.of(),
                List.of(),
                List.of(activeInstance1, activeInstance2, activeInstance3),
                ExecutorState.ACTIVE);

        when(clusterResourcesDB.currentSnapshot(anyBoolean())).thenReturn(List.of(activeExecutor));
        when(stateDB.instances(any(), any(), anyBoolean())).thenReturn(List.of(
                activeInstance1,
                activeInstance2,
                activeInstance3
        ));

        val currentState = StateData.create(
                LocalServiceState.ACTIVE,
                new LocalServiceInfo(serviceId,
                                    spec,
                                    1,
                                    ActivationState.ACTIVE,
                                    new Date(),
                                    new Date()));

        val operation = new LocalServiceAdjustInstancesOperation(serviceId, null);
        val context = new LocalServiceActionContext(UUID.randomUUID().toString(), null);

        val result = action.jobsToRun(context, currentState, operation);

        assertTrue(result.isPresent());
    }

    @Test
    void testConfigTestingStateSelectsOneExecutor() {
        val jobExecutor = mock(JobExecutor.class);
        val stateDB = mock(LocalServiceStateDB.class);
        val clusterResourcesDB = mock(ClusterResourcesDB.class);
        val scheduler = mock(InstanceScheduler.class);
        val communicator = mock(ControllerCommunicator.class);
        val retrySpecFactory = mock(ControllerRetrySpecFactory.class);
        val instanceIdGenerator = mock(InstanceIdGenerator.class);
        val tokenManager = mock(ApplicationInstanceTokenManager.class);
        val httpCaller = mock(HttpCaller.class);

        val action = new AdjustInstancesLocalServiceAction(
                jobExecutor,
                stateDB,
                clusterResourcesDB,
                scheduler,
                communicator,
                retrySpecFactory,
                instanceIdGenerator,
                Executors.defaultThreadFactory(),
                tokenManager,
                httpCaller,
                DEFAULT_CLUSTER_OP);

        val spec = localServiceSpec();
        val serviceId = ControllerUtils.deployableObjectId(spec);

        val activeExecutor = ControllerTestUtils.executorHost(1, 8080, List.of(), List.of(), List.of(), ExecutorState.ACTIVE);
        val unreadyExecutor = ControllerTestUtils.executorHost(2, 8081, List.of(), List.of(), List.of(), ExecutorState.UNREADY);

        when(clusterResourcesDB.currentSnapshot(anyBoolean())).thenReturn(List.of(activeExecutor, unreadyExecutor));
        when(stateDB.instances(any(), any(), anyBoolean())).thenReturn(List.<LocalServiceInstanceInfo>of());

        val currentState = StateData.create(
                LocalServiceState.CONFIG_TESTING,
                new LocalServiceInfo(serviceId,
                                    spec,
                                    1,
                                    ActivationState.CONFIG_TESTING,
                                    new Date(),
                                    new Date()));

        val operation = new LocalServiceAdjustInstancesOperation(serviceId, null);
        val context = new LocalServiceActionContext(UUID.randomUUID().toString(), null);

        val result = action.jobsToRun(context, currentState, operation);

        assertTrue(result.isPresent());
    }

    @Test
    void testConfigTestingWithOnlyBlacklistedExecutors() {
        val jobExecutor = mock(JobExecutor.class);
        val stateDB = mock(LocalServiceStateDB.class);
        val clusterResourcesDB = mock(ClusterResourcesDB.class);
        val scheduler = mock(InstanceScheduler.class);
        val communicator = mock(ControllerCommunicator.class);
        val retrySpecFactory = mock(ControllerRetrySpecFactory.class);
        val instanceIdGenerator = mock(InstanceIdGenerator.class);
        val tokenManager = mock(ApplicationInstanceTokenManager.class);
        val httpCaller = mock(HttpCaller.class);

        val action = new AdjustInstancesLocalServiceAction(
                jobExecutor,
                stateDB,
                clusterResourcesDB,
                scheduler,
                communicator,
                retrySpecFactory,
                instanceIdGenerator,
                Executors.defaultThreadFactory(),
                tokenManager,
                httpCaller,
                DEFAULT_CLUSTER_OP);

        val spec = localServiceSpec();
        val serviceId = ControllerUtils.deployableObjectId(spec);

        val blacklistedExecutor = ControllerTestUtils.executorHost(1, 8080, List.of(), List.of(), List.of(), ExecutorState.BLACKLISTED);

        when(clusterResourcesDB.currentSnapshot(anyBoolean())).thenReturn(List.of(blacklistedExecutor));
        when(stateDB.instances(any(), any(), anyBoolean())).thenReturn(List.<LocalServiceInstanceInfo>of());

        val currentState = StateData.create(
                LocalServiceState.CONFIG_TESTING,
                new LocalServiceInfo(serviceId,
                                    spec,
                                    1,
                                    ActivationState.CONFIG_TESTING,
                                    new Date(),
                                    new Date()));

        val operation = new LocalServiceAdjustInstancesOperation(serviceId, null);
        val context = new LocalServiceActionContext(UUID.randomUUID().toString(), null);

        val result = action.jobsToRun(context, currentState, operation);

        assertFalse(result.isPresent());
    }

    @Test
    void testNoAdjustmentNeededWhenInstancesMatchRequirement() {
        val jobExecutor = mock(JobExecutor.class);
        val stateDB = mock(LocalServiceStateDB.class);
        val clusterResourcesDB = mock(ClusterResourcesDB.class);
        val scheduler = mock(InstanceScheduler.class);
        val communicator = mock(ControllerCommunicator.class);
        val retrySpecFactory = mock(ControllerRetrySpecFactory.class);
        val instanceIdGenerator = mock(InstanceIdGenerator.class);
        val tokenManager = mock(ApplicationInstanceTokenManager.class);
        val httpCaller = mock(HttpCaller.class);

        val action = new AdjustInstancesLocalServiceAction(
                jobExecutor,
                stateDB,
                clusterResourcesDB,
                scheduler,
                communicator,
                retrySpecFactory,
                instanceIdGenerator,
                Executors.defaultThreadFactory(),
                tokenManager,
                httpCaller,
                DEFAULT_CLUSTER_OP);

        val spec = localServiceSpec();
        val serviceId = ControllerUtils.deployableObjectId(spec);

        val instance1 = ControllerTestUtils.generateLocalServiceInstanceInfo(spec);
        val instance2 = ControllerTestUtils.generateLocalServiceInstanceInfo(spec);

        val activeExecutor = ControllerTestUtils.executorHost(
                1, 8080,
                List.of(),
                List.of(),
                List.of(instance1, instance2),
                ExecutorState.ACTIVE);

        when(clusterResourcesDB.currentSnapshot(anyBoolean())).thenReturn(List.of(activeExecutor));
        when(stateDB.instances(any(), any(), anyBoolean())).thenReturn(List.of(instance1, instance2));

        val currentState = StateData.create(
                LocalServiceState.ACTIVE,
                new LocalServiceInfo(serviceId,
                                    spec,
                                    2,
                                    ActivationState.ACTIVE,
                                    new Date(),
                                    new Date()));

        val operation = new LocalServiceAdjustInstancesOperation(serviceId, null);
        val context = new LocalServiceActionContext(UUID.randomUUID().toString(), null);

        val result = action.jobsToRun(context, currentState, operation);

        assertFalse(result.isPresent());
    }
}
