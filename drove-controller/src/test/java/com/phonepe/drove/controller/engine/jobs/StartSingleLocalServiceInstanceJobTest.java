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
import static com.phonepe.drove.controller.ControllerTestUtils.localServiceSpec;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.google.common.util.concurrent.MoreExecutors;
import com.phonepe.drove.auth.config.ApplicationAuthConfig;
import com.phonepe.drove.auth.core.ApplicationInstanceTokenManager;
import com.phonepe.drove.auth.core.JWTApplicationInstanceTokenManager;
import com.phonepe.drove.common.model.LocalServiceInstanceSpec;
import com.phonepe.drove.common.model.MessageDeliveryStatus;
import com.phonepe.drove.common.model.MessageResponse;
import com.phonepe.drove.common.model.executor.StartLocalServiceInstanceMessage;
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
import com.phonepe.drove.controller.statedb.LocalServiceStateDB;
import com.phonepe.drove.controller.utils.ControllerUtils;
import com.phonepe.drove.jobexecutor.JobExecutor;
import com.phonepe.drove.models.application.placement.policies.MatchTagPlacementPolicy;
import com.phonepe.drove.models.localservice.ActivationState;
import com.phonepe.drove.models.localservice.LocalServiceInfo;
import com.phonepe.drove.models.localservice.LocalServiceInstanceInfo;
import com.phonepe.drove.models.localservice.LocalServiceSpec;
import com.phonepe.drove.models.policies.ContainerSecurityProfile;

import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

import lombok.val;

/**
 * Tests for StartSingleLocalServiceInstanceJob.
 */
class StartSingleLocalServiceInstanceJobTest extends ControllerTestBase {

    private static final LocalServiceSpec LOCAL_SERVICE_SPEC = localServiceSpec();

    private final InstanceIdGenerator instanceIdGenerator = new RandomInstanceIdGenerator();
    private final ApplicationInstanceTokenManager tokenManager =
            new JWTApplicationInstanceTokenManager(ApplicationAuthConfig.DEFAULT);


    @Test
    void testJobSuccess() {
        val scheduler = mock(InstanceScheduler.class);
        val instanceInfoDB = mock(LocalServiceStateDB.class);
        val comm = mock(ControllerCommunicator.class);
        val sessionId = UUID.randomUUID().toString();

        val allocatedNode = ControllerTestUtils.allocatedExecutorNode(8080);
        val executor = ControllerTestUtils.executorHost(8080);
        val serviceId = ControllerUtils.deployableObjectId(LOCAL_SERVICE_SPEC);
        val localServiceInfo = new LocalServiceInfo(serviceId, LOCAL_SERVICE_SPEC, 1,
                                                    ActivationState.INACTIVE, new Date(), new Date());

        when(scheduler.schedule(eq(sessionId), anyString(), eq(LOCAL_SERVICE_SPEC),
                                any(MatchTagPlacementPolicy.class), anySet()))
                .thenReturn(Optional.of(allocatedNode));
        when(comm.send(any(StartLocalServiceInstanceMessage.class)))
                .thenAnswer((Answer<MessageResponse>) inv ->
                        new MessageResponse(inv.<StartLocalServiceInstanceMessage>getArgument(0).getHeader(),
                                            MessageDeliveryStatus.ACCEPTED));
        when(instanceInfoDB.instance(eq(serviceId), anyString()))
                .thenAnswer((Answer<Optional<LocalServiceInstanceInfo>>) inv ->
                        Optional.of(ControllerTestUtils.generateLocalServiceInstanceInfo(LOCAL_SERVICE_SPEC)));

        val rf = mock(ControllerRetrySpecFactory.class);
        when(rf.jobRetrySpec()).thenReturn(ControllerTestUtils.NO_RETRY_SPEC);
        when(rf.instanceStateCheckRetrySpec(anyLong())).thenReturn(ControllerTestUtils.NO_RETRY_SPEC);

        val job = new StartSingleLocalServiceInstanceJob(localServiceInfo,
                                                         ControllerTestUtils.DEFAULT_CLUSTER_OP,
                                                         scheduler,
                                                         instanceInfoDB,
                                                         comm,
                                                         sessionId,
                                                         rf,
                                                         instanceIdGenerator,
                                                         tokenManager,
                                                         ControllerTestUtils.defaultEnforcer(),
                                                         httpCaller(),
                                                         executor);
        val testStatus = new AtomicBoolean();
        val exec = new JobExecutor<Boolean>(MoreExecutors.newDirectExecutorService());
        exec.schedule(Collections.singletonList(job), new BooleanResponseCombiner(),
                      r -> testStatus.set(r.getResult()));
        assertTrue(testStatus.get());
    }


    @Test
    void testSecuritySpecPropagated() {
        val scheduler = mock(InstanceScheduler.class);
        val instanceInfoDB = mock(LocalServiceStateDB.class);
        val comm = mock(ControllerCommunicator.class);
        val sessionId = UUID.randomUUID().toString();

        val allocatedNode = ControllerTestUtils.allocatedExecutorNode(8080);
        val executor = ControllerTestUtils.executorHost(8080);
        val serviceId = ControllerUtils.deployableObjectId(LOCAL_SERVICE_SPEC);
        val localServiceInfo = new LocalServiceInfo(serviceId, LOCAL_SERVICE_SPEC, 1,
                                                    ActivationState.INACTIVE, new Date(), new Date());

        when(scheduler.schedule(eq(sessionId), anyString(), eq(LOCAL_SERVICE_SPEC),
                                any(MatchTagPlacementPolicy.class), anySet()))
                .thenReturn(Optional.of(allocatedNode));

        val capturedSpec = new AtomicReference<LocalServiceInstanceSpec>();
        when(comm.send(any(StartLocalServiceInstanceMessage.class)))
                .thenAnswer((Answer<MessageResponse>) inv -> {
                    capturedSpec.set(inv.<StartLocalServiceInstanceMessage>getArgument(0).getSpec());
                    return new MessageResponse(
                            inv.<StartLocalServiceInstanceMessage>getArgument(0).getHeader(),
                            MessageDeliveryStatus.ACCEPTED);
                });
        when(instanceInfoDB.instance(eq(serviceId), anyString()))
                .thenAnswer((Answer<Optional<LocalServiceInstanceInfo>>) inv ->
                        Optional.of(ControllerTestUtils.generateLocalServiceInstanceInfo(LOCAL_SERVICE_SPEC)));

        val profile = ContainerSecurityProfile.builder()
                .policyName("privileged-local-service-policy")
                .enablePrivilegedMode(true)
                .build();
        val config = ContainerSecurityPolicyConfig.builder()
                .match(List.of("LOCAL_SERVICE/TEST_SPEC"))
                .profile(profile)
                .build();
        val enforcer = new SecurityPolicyEnforcer(
                List.of(config),
                ControllerOptions.DEFAULT);

        val rf = mock(ControllerRetrySpecFactory.class);
        when(rf.jobRetrySpec()).thenReturn(ControllerTestUtils.NO_RETRY_SPEC);
        when(rf.instanceStateCheckRetrySpec(anyLong())).thenReturn(ControllerTestUtils.NO_RETRY_SPEC);

        val job = new StartSingleLocalServiceInstanceJob(localServiceInfo,
                                                         ControllerTestUtils.DEFAULT_CLUSTER_OP,
                                                         scheduler,
                                                         instanceInfoDB,
                                                         comm,
                                                         sessionId,
                                                         rf,
                                                         instanceIdGenerator,
                                                         tokenManager,
                                                         enforcer,
                                                         httpCaller(),
                                                         executor);
        val testStatus = new AtomicBoolean();
        val exec = new JobExecutor<Boolean>(MoreExecutors.newDirectExecutorService());
        exec.schedule(Collections.singletonList(job), new BooleanResponseCombiner(),
                      r -> testStatus.set(r.getResult()));

        assertTrue(testStatus.get());
        assertNotNull(capturedSpec.get(), "StartLocalServiceInstanceMessage spec must be captured");
        assertNotNull(capturedSpec.get().getSecuritySpec(), "securitySpec must not be null");
        assertTrue(capturedSpec.get().getSecuritySpec().getEnablePrivilegedMode(),
                   "enablePrivilegedMode must be true for privileged-mode enforcer");
    }


    @Test
    void testJobNoNode() {
        val scheduler = mock(InstanceScheduler.class);
        val instanceInfoDB = mock(LocalServiceStateDB.class);
        val comm = mock(ControllerCommunicator.class);
        val sessionId = UUID.randomUUID().toString();

        val executor = ControllerTestUtils.executorHost(8080);
        val serviceId = ControllerUtils.deployableObjectId(LOCAL_SERVICE_SPEC);
        val localServiceInfo = new LocalServiceInfo(serviceId, LOCAL_SERVICE_SPEC, 1,
                                                    ActivationState.INACTIVE, new Date(), new Date());

        when(scheduler.schedule(eq(sessionId), anyString(), eq(LOCAL_SERVICE_SPEC),
                                any(MatchTagPlacementPolicy.class), anySet()))
                .thenReturn(Optional.empty());

        val rf = mock(ControllerRetrySpecFactory.class);
        when(rf.jobRetrySpec()).thenReturn(ControllerTestUtils.NO_RETRY_SPEC);
        when(rf.instanceStateCheckRetrySpec(anyLong())).thenReturn(ControllerTestUtils.NO_RETRY_SPEC);

        val job = new StartSingleLocalServiceInstanceJob(localServiceInfo,
                                                         ControllerTestUtils.DEFAULT_CLUSTER_OP,
                                                         scheduler,
                                                         instanceInfoDB,
                                                         comm,
                                                         sessionId,
                                                         rf,
                                                         instanceIdGenerator,
                                                         tokenManager,
                                                         ControllerTestUtils.defaultEnforcer(),
                                                         httpCaller(),
                                                         executor);
        assertJobFails(job);
    }


    @Test
    void testJobCommFailure() {
        val scheduler = mock(InstanceScheduler.class);
        val instanceInfoDB = mock(LocalServiceStateDB.class);
        val comm = mock(ControllerCommunicator.class);
        val sessionId = UUID.randomUUID().toString();

        val allocatedNode = ControllerTestUtils.allocatedExecutorNode(8080);
        val executor = ControllerTestUtils.executorHost(8080);
        val serviceId = ControllerUtils.deployableObjectId(LOCAL_SERVICE_SPEC);
        val localServiceInfo = new LocalServiceInfo(serviceId, LOCAL_SERVICE_SPEC, 1,
                                                    ActivationState.INACTIVE, new Date(), new Date());

        when(scheduler.schedule(eq(sessionId), anyString(), eq(LOCAL_SERVICE_SPEC),
                                any(MatchTagPlacementPolicy.class), anySet()))
                .thenReturn(Optional.of(allocatedNode));
        when(comm.send(any(StartLocalServiceInstanceMessage.class)))
                .thenAnswer((Answer<MessageResponse>) inv ->
                        new MessageResponse(inv.<StartLocalServiceInstanceMessage>getArgument(0).getHeader(),
                                            MessageDeliveryStatus.REJECTED));

        val rf = mock(ControllerRetrySpecFactory.class);
        when(rf.jobRetrySpec()).thenReturn(ControllerTestUtils.NO_RETRY_SPEC);
        when(rf.instanceStateCheckRetrySpec(anyLong())).thenReturn(ControllerTestUtils.NO_RETRY_SPEC);

        val job = new StartSingleLocalServiceInstanceJob(localServiceInfo,
                                                         ControllerTestUtils.DEFAULT_CLUSTER_OP,
                                                         scheduler,
                                                         instanceInfoDB,
                                                         comm,
                                                         sessionId,
                                                         rf,
                                                         instanceIdGenerator,
                                                         tokenManager,
                                                         ControllerTestUtils.defaultEnforcer(),
                                                         httpCaller(),
                                                         executor);
        assertJobFails(job);
    }

    private void assertJobFails(StartSingleLocalServiceInstanceJob job) {
        val testStatus = new AtomicBoolean(true);
        val exec = new JobExecutor<Boolean>(MoreExecutors.newDirectExecutorService());
        exec.schedule(Collections.singletonList(job), new BooleanResponseCombiner(),
                      r -> testStatus.set(r.getResult()));
        assertFalse(testStatus.get());
    }
}
