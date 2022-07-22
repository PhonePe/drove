package com.phonepe.drove.controller.engine.jobs;

import com.google.common.util.concurrent.MoreExecutors;
import com.phonepe.drove.auth.config.ApplicationAuthConfig;
import com.phonepe.drove.auth.core.ApplicationInstanceTokenManager;
import com.phonepe.drove.auth.core.JWTApplicationInstanceTokenManager;
import com.phonepe.drove.common.model.MessageDeliveryStatus;
import com.phonepe.drove.common.model.MessageResponse;
import com.phonepe.drove.common.model.executor.StartInstanceMessage;
import com.phonepe.drove.controller.ControllerTestBase;
import com.phonepe.drove.controller.ControllerTestUtils;
import com.phonepe.drove.controller.engine.ControllerCommunicator;
import com.phonepe.drove.controller.engine.ControllerRetrySpecFactory;
import com.phonepe.drove.controller.engine.InstanceIdGenerator;
import com.phonepe.drove.controller.engine.RandomInstanceIdGenerator;
import com.phonepe.drove.jobexecutor.JobExecutor;
import com.phonepe.drove.controller.resourcemgmt.InstanceScheduler;
import com.phonepe.drove.controller.statedb.InstanceInfoDB;
import com.phonepe.drove.controller.utils.ControllerUtils;
import com.phonepe.drove.models.application.ApplicationSpec;
import com.phonepe.drove.models.application.PortType;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.instance.InstancePort;
import com.phonepe.drove.models.instance.InstanceState;
import com.phonepe.drove.models.instance.LocalInstanceInfo;
import com.phonepe.drove.models.operation.ClusterOpSpec;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.phonepe.drove.controller.ControllerTestUtils.appSpec;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 *
 */
class StartSingleInstanceJobTest extends ControllerTestBase {

    private static final ApplicationSpec APP_SPEC = appSpec();
    private final InstanceIdGenerator instanceIdGenerator = new RandomInstanceIdGenerator();
    private final ApplicationInstanceTokenManager tokenManager = new JWTApplicationInstanceTokenManager(ApplicationAuthConfig.DEFAULT);

    @Test
    void testJobSuccess() {
        val instanceScheduler = mock(InstanceScheduler.class);
        val instanceInfoDB = mock(InstanceInfoDB.class);
        val comm = mock(ControllerCommunicator.class);
        val sessionId = UUID.randomUUID().toString();
        val allocatedExecutorNode = ControllerTestUtils.allocatedExecutorNode(8080);
        when(instanceScheduler.schedule(sessionId, APP_SPEC))
                .thenReturn(Optional.of(allocatedExecutorNode));
        when(comm.send(any(StartInstanceMessage.class)))
                .thenAnswer((Answer<MessageResponse>) invocationOnMock
                        -> new MessageResponse(invocationOnMock.<StartInstanceMessage>getArgument(0).getHeader(),
                                               MessageDeliveryStatus.ACCEPTED));
        val appId = ControllerUtils.deployableObjectId(APP_SPEC);
        when(instanceInfoDB.instance(eq(appId), anyString()))
                .thenAnswer((Answer<Optional<InstanceInfo>>) invocationOnMock
                        -> Optional.of(new InstanceInfo(appId,
                                                        APP_SPEC.getName(),
                                                        invocationOnMock.getArgument(1),
                                                        allocatedExecutorNode.getExecutorId(),
                                                        new LocalInstanceInfo(allocatedExecutorNode.getHostname(),
                                                                              Collections.singletonMap("main",
                                                                                                       new InstancePort(
                                                                                                               8000,
                                                                                                               32000,
                                                                                                               PortType.HTTP))),
                                                        List.of(allocatedExecutorNode.getCpu(),
                                                                allocatedExecutorNode.getMemory()),
                                                        InstanceState.HEALTHY,
                                                        Collections.emptyMap(),
                                                        null,
                                                        new Date(),
                                                        new Date())));
        val rf = mock(ControllerRetrySpecFactory.class);
        when(rf.jobStartRetrySpec()).thenReturn(ControllerTestUtils.NO_RETRY_SPEC);
        when(rf.instanceStateCheckRetrySpec(any(Long.class))).thenReturn(ControllerTestUtils.NO_RETRY_SPEC);
        val job = new StartSingleInstanceJob(APP_SPEC,
                                             ClusterOpSpec.DEFAULT,
                                             instanceScheduler,
                                             instanceInfoDB,
                                             comm,
                                             sessionId,
                                             rf, instanceIdGenerator, tokenManager);
        val testStatus = new AtomicBoolean();
        val exec = new JobExecutor<Boolean>(MoreExecutors.newDirectExecutorService());
        exec.schedule(Collections.singletonList(job), new BooleanResponseCombiner(), r -> {
            testStatus.set(r.getResult());
        });
        assertTrue(testStatus.get());
    }

    @Test
    void testJobNoNode() {
        val appSpec = appSpec();
        val instanceScheduler = mock(InstanceScheduler.class);
        val instanceInfoDB = mock(InstanceInfoDB.class);
        val comm = mock(ControllerCommunicator.class);
        val sessionId = UUID.randomUUID().toString();
        when(instanceScheduler.schedule(sessionId, appSpec))
                .thenReturn(Optional.empty());
        val rf = mock(ControllerRetrySpecFactory.class);
        when(rf.jobStartRetrySpec()).thenReturn(ControllerTestUtils.NO_RETRY_SPEC);
        when(rf.instanceStateCheckRetrySpec(any(Long.class))).thenReturn(ControllerTestUtils.NO_RETRY_SPEC);
        val job = new StartSingleInstanceJob(appSpec,
                                             ClusterOpSpec.DEFAULT,
                                             instanceScheduler,
                                             instanceInfoDB,
                                             comm,
                                             sessionId,
                                             rf,
                                             instanceIdGenerator,
                                             tokenManager);
        assertFailure(job);
    }

    @Test
    void testJobUnhealthy() {
        val appSpec = appSpec();
        val instanceScheduler = mock(InstanceScheduler.class);
        val instanceInfoDB = mock(InstanceInfoDB.class);
        val comm = mock(ControllerCommunicator.class);
        val sessionId = UUID.randomUUID().toString();
        val allocatedExecutorNode = ControllerTestUtils.allocatedExecutorNode(8080);
        when(instanceScheduler.schedule(sessionId, appSpec))
                .thenReturn(Optional.of(allocatedExecutorNode));
        when(comm.send(any(StartInstanceMessage.class)))
                .thenAnswer((Answer<MessageResponse>) invocationOnMock
                        -> new MessageResponse(invocationOnMock.<StartInstanceMessage>getArgument(0).getHeader(),
                                               MessageDeliveryStatus.ACCEPTED));
        val appId = ControllerUtils.deployableObjectId(appSpec);
        when(instanceInfoDB.instance(eq(appId), anyString()))
                .thenAnswer((Answer<Optional<InstanceInfo>>) invocationOnMock
                        -> Optional.of(new InstanceInfo(appId,
                                                        appSpec.getName(),
                                                        invocationOnMock.getArgument(1),
                                                        allocatedExecutorNode.getExecutorId(),
                                                        new LocalInstanceInfo(allocatedExecutorNode.getHostname(),
                                                                              Collections.singletonMap("main",
                                                                                                       new InstancePort(
                                                                                                               8000,
                                                                                                               32000,
                                                                                                               PortType.HTTP))),
                                                        List.of(allocatedExecutorNode.getCpu(),
                                                                allocatedExecutorNode.getMemory()),
                                                        InstanceState.UNHEALTHY,
                                                        Collections.emptyMap(),
                                                        null,
                                                        new Date(),
                                                        new Date())));
        val rf = mock(ControllerRetrySpecFactory.class);
        when(rf.jobStartRetrySpec()).thenReturn(ControllerTestUtils.NO_RETRY_SPEC);
        when(rf.instanceStateCheckRetrySpec(any(Long.class))).thenReturn(ControllerTestUtils.NO_RETRY_SPEC);
        val job = new StartSingleInstanceJob(appSpec,
                                             ClusterOpSpec.DEFAULT,
                                             instanceScheduler,
                                             instanceInfoDB,
                                             comm,
                                             sessionId,
                                             rf,
                                             instanceIdGenerator,
                                             tokenManager);
        assertFailure(job);
    }

    @Test
    void testJobCommFailure() {
        val appSpec = appSpec();
        val instanceScheduler = mock(InstanceScheduler.class);
        val instanceInfoDB = mock(InstanceInfoDB.class);
        val comm = mock(ControllerCommunicator.class);
        val sessionId = UUID.randomUUID().toString();
        val allocatedExecutorNode = ControllerTestUtils.allocatedExecutorNode(8080);
        when(instanceScheduler.schedule(sessionId, appSpec))
                .thenReturn(Optional.of(allocatedExecutorNode));
        when(comm.send(any(StartInstanceMessage.class)))
                .thenAnswer((Answer<MessageResponse>) invocationOnMock
                        -> new MessageResponse(invocationOnMock.<StartInstanceMessage>getArgument(0).getHeader(),
                                               MessageDeliveryStatus.REJECTED));
        val appId = ControllerUtils.deployableObjectId(appSpec);
        val rf = mock(ControllerRetrySpecFactory.class);
        when(rf.jobStartRetrySpec()).thenReturn(ControllerTestUtils.NO_RETRY_SPEC);
        when(rf.instanceStateCheckRetrySpec(any(Long.class))).thenReturn(ControllerTestUtils.NO_RETRY_SPEC);
        val job = new StartSingleInstanceJob(appSpec,
                                             ClusterOpSpec.DEFAULT,
                                             instanceScheduler,
                                             instanceInfoDB,
                                             comm,
                                             sessionId,
                                             rf,
                                             instanceIdGenerator,
                                             tokenManager);
        assertFailure(job);
    }

    @Test
    void testJobTimeout() {
        val appSpec = appSpec();
        val instanceScheduler = mock(InstanceScheduler.class);
        val instanceInfoDB = mock(InstanceInfoDB.class);
        val comm = mock(ControllerCommunicator.class);
        val sessionId = UUID.randomUUID().toString();
        val allocatedExecutorNode = ControllerTestUtils.allocatedExecutorNode(8080);
        when(instanceScheduler.schedule(sessionId, appSpec))
                .thenReturn(Optional.of(allocatedExecutorNode));
        when(comm.send(any(StartInstanceMessage.class)))
                .thenThrow(new RuntimeException("Test Exception"));
        val appId = ControllerUtils.deployableObjectId(appSpec);
        when(instanceInfoDB.instance(eq(appId), anyString()))
                .thenAnswer((Answer<Optional<InstanceInfo>>) invocationOnMock
                        -> Optional.of(new InstanceInfo(appId,
                                                        appSpec.getName(),
                                                        invocationOnMock.getArgument(1),
                                                        allocatedExecutorNode.getExecutorId(),
                                                        new LocalInstanceInfo(allocatedExecutorNode.getHostname(),
                                                                              Collections.singletonMap("main",
                                                                                                       new InstancePort(
                                                                                                               8000,
                                                                                                               32000,
                                                                                                               PortType.HTTP))),
                                                        List.of(allocatedExecutorNode.getCpu(),
                                                                allocatedExecutorNode.getMemory()),
                                                        InstanceState.HEALTHY,
                                                        Collections.emptyMap(),
                                                        null,
                                                        new Date(),
                                                        new Date())));
        val rf = mock(ControllerRetrySpecFactory.class);
        when(rf.jobStartRetrySpec()).thenReturn(ControllerTestUtils.NO_RETRY_SPEC);
        when(rf.instanceStateCheckRetrySpec(any(Long.class))).thenReturn(ControllerTestUtils.NO_RETRY_SPEC);
        val job = new StartSingleInstanceJob(appSpec,
                                             ClusterOpSpec.DEFAULT,
                                             instanceScheduler,
                                             instanceInfoDB,
                                             comm,
                                             sessionId,
                                             rf, instanceIdGenerator, tokenManager);

        assertFailure(job);
    }

    private void assertFailure(StartSingleInstanceJob job) {
        val testStatus = new AtomicBoolean(true);
        val exec = new JobExecutor<Boolean>(MoreExecutors.newDirectExecutorService());
        exec.schedule(Collections.singletonList(job), new BooleanResponseCombiner(), r -> {
            testStatus.set(r.getResult());
        });
        assertFalse(testStatus.get());
    }
}