package com.phonepe.drove.controller.engine.jobs;

import com.google.common.util.concurrent.MoreExecutors;
import com.phonepe.drove.common.model.MessageDeliveryStatus;
import com.phonepe.drove.common.model.MessageResponse;
import com.phonepe.drove.common.model.executor.StopInstanceMessage;
import com.phonepe.drove.controller.ControllerTestBase;
import com.phonepe.drove.controller.ControllerTestUtils;
import com.phonepe.drove.controller.engine.BooleanResponseCombiner;
import com.phonepe.drove.controller.engine.ControllerCommunicator;
import com.phonepe.drove.controller.engine.ControllerRetrySpecFactory;
import com.phonepe.drove.controller.jobexecutor.JobExecutor;
import com.phonepe.drove.controller.resourcemgmt.AllocatedExecutorNode;
import com.phonepe.drove.controller.resourcemgmt.ClusterResourcesDB;
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
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.phonepe.drove.controller.ControllerTestUtils.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 *
 */
class StopSingleInstanceJobTest extends ControllerTestBase {
    private static final ApplicationSpec APP_SPEC = appSpec();

    @Test
    void testJobSuccess() {
        val instanceInfoDB = mock(InstanceInfoDB.class);
        val comm = mock(ControllerCommunicator.class);
        val resourcesDB = mock(ClusterResourcesDB.class);
        val instanceId = "TEST_INSTANCE";
        val allocatedExecutorNode = ControllerTestUtils.allocatedExecutorNode(8080);
        when(resourcesDB.currentSnapshot(anyString()))
                .thenReturn(Optional.of(executorHost(8080)));
        when(comm.send(any(StopInstanceMessage.class)))
                .thenAnswer((Answer<MessageResponse>) invocationOnMock
                        -> new MessageResponse(invocationOnMock.<StopInstanceMessage>getArgument(0).getHeader(),
                                               MessageDeliveryStatus.ACCEPTED));
        val appId = ControllerUtils.appId(APP_SPEC);
        when(instanceInfoDB.instance(appId, instanceId))
                .thenAnswer((Answer<Optional<InstanceInfo>>) invocationOnMock
                        -> Optional.of(instanceInfo(allocatedExecutorNode, appId, invocationOnMock)));
        val rf = mock(ControllerRetrySpecFactory.class);
        when(rf.jobStartRetrySpec()).thenReturn(NO_RETRY_SPEC);
        when(rf.instanceStateCheckRetrySpec(any(Long.class))).thenReturn(NO_RETRY_SPEC);
        val job = new StopSingleInstanceJob(appId,
                                            instanceId,
                                            ClusterOpSpec.DEFAULT,
                                            instanceInfoDB,
                                            resourcesDB,
                                            comm,
                                            rf);
        val testStatus = new AtomicBoolean();
        val exec = new JobExecutor<Boolean>(MoreExecutors.newDirectExecutorService());
        exec.schedule(Collections.singletonList(job), new BooleanResponseCombiner(), r -> {
            testStatus.set(r.getResult());
        });
        assertTrue(testStatus.get());
    }

    @Test
    void testJobNoInstance() {
        val instanceInfoDB = mock(InstanceInfoDB.class);
        val comm = mock(ControllerCommunicator.class);
        val resourcesDB = mock(ClusterResourcesDB.class);
        val instanceId = "TEST_INSTANCE";
        val allocatedExecutorNode = ControllerTestUtils.allocatedExecutorNode(8080);
        when(resourcesDB.currentSnapshot(anyString()))
                .thenReturn(Optional.of(executorHost(8080)));
        when(comm.send(any(StopInstanceMessage.class)))
                .thenAnswer((Answer<MessageResponse>) invocationOnMock
                        -> new MessageResponse(invocationOnMock.<StopInstanceMessage>getArgument(0).getHeader(),
                                               MessageDeliveryStatus.ACCEPTED));
        val appId = ControllerUtils.appId(APP_SPEC);
        when(instanceInfoDB.instance(appId, instanceId))
                .thenReturn(Optional.empty());
        val rf = mock(ControllerRetrySpecFactory.class);
        when(rf.jobStartRetrySpec()).thenReturn(NO_RETRY_SPEC);
        when(rf.instanceStateCheckRetrySpec(any(Long.class))).thenReturn(NO_RETRY_SPEC);
        val job = new StopSingleInstanceJob(appId,
                                            instanceId,
                                            ClusterOpSpec.DEFAULT,
                                            instanceInfoDB,
                                            resourcesDB,
                                            comm,
                                            rf);
        val testStatus = new AtomicBoolean();
        val exec = new JobExecutor<Boolean>(MoreExecutors.newDirectExecutorService());
        exec.schedule(Collections.singletonList(job), new BooleanResponseCombiner(), r -> {
            testStatus.set(r.getResult());
        });
        assertTrue(testStatus.get());
    }

    @Test
    void testJobNoExecutor() {
        val instanceInfoDB = mock(InstanceInfoDB.class);
        val comm = mock(ControllerCommunicator.class);
        val resourcesDB = mock(ClusterResourcesDB.class);
        val instanceId = "TEST_INSTANCE";
        val allocatedExecutorNode = ControllerTestUtils.allocatedExecutorNode(8080);
        when(resourcesDB.currentSnapshot(anyString()))
                .thenReturn(Optional.empty());
        when(comm.send(any(StopInstanceMessage.class)))
                .thenAnswer((Answer<MessageResponse>) invocationOnMock
                        -> new MessageResponse(invocationOnMock.<StopInstanceMessage>getArgument(0).getHeader(),
                                               MessageDeliveryStatus.ACCEPTED));
        val appId = ControllerUtils.appId(APP_SPEC);
        when(instanceInfoDB.instance(appId, instanceId))
                .thenAnswer((Answer<Optional<InstanceInfo>>) invocationOnMock
                        -> Optional.of(instanceInfo(allocatedExecutorNode, appId, invocationOnMock)));
        val rf = mock(ControllerRetrySpecFactory.class);
        when(rf.jobStartRetrySpec()).thenReturn(NO_RETRY_SPEC);
        when(rf.instanceStateCheckRetrySpec(any(Long.class))).thenReturn(NO_RETRY_SPEC);
        val job = new StopSingleInstanceJob(appId,
                                            instanceId,
                                            ClusterOpSpec.DEFAULT,
                                            instanceInfoDB,
                                            resourcesDB,
                                            comm,
                                            rf);
        val testStatus = new AtomicBoolean(true);
        val exec = new JobExecutor<Boolean>(MoreExecutors.newDirectExecutorService());
        exec.schedule(Collections.singletonList(job), new BooleanResponseCombiner(), r -> {
            testStatus.set(r.getResult());
        });
        assertFalse(testStatus.get());
    }

    @Test
    void testJobMsgThrow() {
        val instanceInfoDB = mock(InstanceInfoDB.class);
        val comm = mock(ControllerCommunicator.class);
        val resourcesDB = mock(ClusterResourcesDB.class);
        val sessionId = UUID.randomUUID().toString();
        val instanceId = "TEST_INSTANCE";
        val allocatedExecutorNode = ControllerTestUtils.allocatedExecutorNode(8080);
        when(resourcesDB.currentSnapshot(anyString()))
                .thenReturn(Optional.of(executorHost(8080)));
        when(comm.send(any(StopInstanceMessage.class)))
                .thenThrow(new IllegalArgumentException("Test error"));
        val appId = ControllerUtils.appId(APP_SPEC);
        when(instanceInfoDB.instance(appId, instanceId))
                .thenAnswer((Answer<Optional<InstanceInfo>>) invocationOnMock
                        -> Optional.of(instanceInfo(allocatedExecutorNode, appId, invocationOnMock)));
        val rf = mock(ControllerRetrySpecFactory.class);
        when(rf.jobStartRetrySpec()).thenReturn(NO_RETRY_SPEC);
        when(rf.instanceStateCheckRetrySpec(any(Long.class))).thenReturn(NO_RETRY_SPEC);
        val job = new StopSingleInstanceJob(appId,
                                            instanceId,
                                            ClusterOpSpec.DEFAULT,
                                            instanceInfoDB,
                                            resourcesDB,
                                            comm,
                                            rf);
        val testStatus = new AtomicBoolean(true);
        val exec = new JobExecutor<Boolean>(MoreExecutors.newDirectExecutorService());
        exec.schedule(Collections.singletonList(job), new BooleanResponseCombiner(), r -> {
            testStatus.set(r.getResult());
        });
        assertFalse(testStatus.get());
    }
    @Test
    void testJobMsgMsgNoAccepted() {
        val instanceInfoDB = mock(InstanceInfoDB.class);
        val comm = mock(ControllerCommunicator.class);
        val resourcesDB = mock(ClusterResourcesDB.class);
        val sessionId = UUID.randomUUID().toString();
        val instanceId = "TEST_INSTANCE";
        val allocatedExecutorNode = ControllerTestUtils.allocatedExecutorNode(8080);
        when(resourcesDB.currentSnapshot(anyString()))
                .thenReturn(Optional.of(executorHost(8080)));
        when(comm.send(any(StopInstanceMessage.class)))
                .thenAnswer((Answer<MessageResponse>) invocationOnMock
                        -> new MessageResponse(invocationOnMock.<StopInstanceMessage>getArgument(0).getHeader(),
                                               MessageDeliveryStatus.REJECTED));        val appId = ControllerUtils.appId(APP_SPEC);
        when(instanceInfoDB.instance(appId, instanceId))
                .thenAnswer((Answer<Optional<InstanceInfo>>) invocationOnMock
                        -> Optional.of(instanceInfo(allocatedExecutorNode, appId, invocationOnMock)));
        val rf = mock(ControllerRetrySpecFactory.class);
        when(rf.jobStartRetrySpec()).thenReturn(NO_RETRY_SPEC);
        when(rf.instanceStateCheckRetrySpec(any(Long.class))).thenReturn(NO_RETRY_SPEC);
        val job = new StopSingleInstanceJob(appId,
                                            instanceId,
                                            ClusterOpSpec.DEFAULT,
                                            instanceInfoDB,
                                            resourcesDB,
                                            comm,
                                            rf);
        val testStatus = new AtomicBoolean(true);
        val exec = new JobExecutor<Boolean>(MoreExecutors.newDirectExecutorService());
        exec.schedule(Collections.singletonList(job), new BooleanResponseCombiner(), r -> {
            testStatus.set(r.getResult());
        });
        assertFalse(testStatus.get());
    }

    private InstanceInfo instanceInfo(
            AllocatedExecutorNode allocatedExecutorNode,
            String appId,
            InvocationOnMock invocationOnMock) {
        return new InstanceInfo(appId,
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
                                InstanceState.STOPPED,
                                Collections.emptyMap(),
                                null,
                                new Date(),
                                new Date());
    }
}