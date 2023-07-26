package com.phonepe.drove.executor.managed;

import com.google.common.collect.Sets;
import com.phonepe.drove.common.AbstractTestBase;
import com.phonepe.drove.common.CommonTestUtils;
import com.phonepe.drove.executor.ContainerHelperExtension;
import com.phonepe.drove.executor.ExecutorTestingUtils;
import com.phonepe.drove.executor.discovery.ClusterClient;
import com.phonepe.drove.executor.engine.ApplicationInstanceEngine;
import com.phonepe.drove.executor.engine.TaskInstanceEngine;
import com.phonepe.drove.models.internal.KnownInstancesData;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.phonepe.drove.executor.ExecutorTestingUtils.DOCKER_CLIENT;
import static com.phonepe.drove.executor.ExecutorTestingUtils.containerExists;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 *
 */
@ExtendWith(ContainerHelperExtension.class)
class ZombieInstanceReaperTest extends AbstractTestBase {
    @Test
    @SneakyThrows
    void testPruneAppInstancesWithoutDocker() {
        val ids = new LinkedHashSet<String>();
        IntStream.rangeClosed(0,100).forEach(i -> ids.add("AI" + i));
        val ae = mock(ApplicationInstanceEngine.class);
        when(ae.instanceIds(any())).thenReturn(ids);
        when(ae.stopInstance(anyString())).thenAnswer(invocationOnMock -> ids.remove(invocationOnMock.getArgument(0, String.class)));
        val cc = mock(ClusterClient.class);
        when(cc.currentKnownInstances()).thenReturn(KnownInstancesData.EMPTY);
        val te = mock(TaskInstanceEngine.class);
        val zr = new ZombieInstanceReaper(DOCKER_CLIENT, ae, te, Duration.ofSeconds(1), cc);
        zr.start();
        CommonTestUtils.waitUntil(ids::isEmpty);
        assertTrue(ids.isEmpty());
        zr.stop();
    }

    @Test
    @SneakyThrows
    void testPruneTaskInstancesWithoutDocker() {
        val ids = new LinkedHashSet<String>();
        IntStream.rangeClosed(0,100).forEach(i -> ids.add("TI" + i));
        val ae = mock(ApplicationInstanceEngine.class);
        val te = mock(TaskInstanceEngine.class);
        when(te.instanceIds(any())).thenReturn(ids);
        when(te.stopInstance(anyString())).thenAnswer(invocationOnMock -> ids.remove(invocationOnMock.getArgument(0, String.class)));
        val cc = mock(ClusterClient.class);
        when(cc.currentKnownInstances()).thenReturn(KnownInstancesData.EMPTY);
        val zr = new ZombieInstanceReaper(DOCKER_CLIENT, ae, te, Duration.ofSeconds(1), cc);
        zr.start();
        CommonTestUtils.waitUntil(ids::isEmpty);
        assertTrue(ids.isEmpty());
        zr.stop();
    }

    @Test
    @SneakyThrows
    void testPruneUnknownAppDocker() {
        val appSpec = ExecutorTestingUtils.testAppInstanceSpec();
        val appInstanceData = ExecutorTestingUtils.createExecutorAppInstanceInfo(appSpec, 8080);
        val cId = ExecutorTestingUtils.startTestAppContainer(appSpec, appInstanceData, MAPPER);
        val ae = mock(ApplicationInstanceEngine.class);
        val te = mock(TaskInstanceEngine.class);
        val cc = mock(ClusterClient.class);
        when(cc.currentKnownInstances()).thenReturn(KnownInstancesData.EMPTY);
        when(te.instanceIds(any())).thenReturn(Set.of());
        when(te.stopInstance(anyString())).thenReturn(false);
        val zr = new ZombieInstanceReaper(DOCKER_CLIENT, ae, te, Duration.ofSeconds(1), cc);
        zr.start();
        CommonTestUtils.waitUntil(() -> !containerExists(cId));
        assertFalse(containerExists(cId));
        zr.stop();
    }

    @Test
    @SneakyThrows
    void testPruneUnknownTaskDocker() {
        val taskSpec = ExecutorTestingUtils.testTaskInstanceSpec();
        val taskInstanceData = ExecutorTestingUtils.createExecutorTaskInfo(taskSpec);
        val cId = ExecutorTestingUtils.startTestTaskContainer(taskSpec, taskInstanceData, MAPPER);
        val ae = mock(ApplicationInstanceEngine.class);
        val te = mock(TaskInstanceEngine.class);
        val cc = mock(ClusterClient.class);
        when(cc.currentKnownInstances()).thenReturn(KnownInstancesData.EMPTY);
        when(te.instanceIds(any())).thenReturn(Set.of());
        when(te.stopInstance(anyString())).thenReturn(false);
        val zr = new ZombieInstanceReaper(DOCKER_CLIENT, ae, te, Duration.ofSeconds(1), cc);
        zr.start();
        CommonTestUtils.waitUntil(() -> !containerExists(cId));
        assertFalse(containerExists(cId));
        zr.stop();
    }


    @Test
    @SneakyThrows
    void testOtherAppUnaffected() {
        val taskSpec = ExecutorTestingUtils.testTaskInstanceSpec();
        val taskInstanceData = ExecutorTestingUtils.createExecutorTaskInfo(taskSpec);
        val cId = ExecutorTestingUtils.startTestTaskContainer(taskSpec, taskInstanceData, MAPPER);

        //Following should not get killed
        val appSpec = ExecutorTestingUtils.testAppInstanceSpec();
        val appInstanceData = ExecutorTestingUtils.createExecutorAppInstanceInfo(appSpec, 8080);
        val appContainer = ExecutorTestingUtils.startTestAppContainer(appSpec, appInstanceData, MAPPER);

        val ae = mock(ApplicationInstanceEngine.class);
        when(ae.instanceIds(any())).thenReturn(Set.of(appInstanceData.getInstanceId()));

        val te = mock(TaskInstanceEngine.class);
        val cc = mock(ClusterClient.class);
        when(cc.currentKnownInstances()).thenReturn(KnownInstancesData.EMPTY);
        val zr = new ZombieInstanceReaper(DOCKER_CLIENT, ae, te, Duration.ofSeconds(1), cc);
        zr.start();
        CommonTestUtils.waitUntil(() -> !containerExists(cId));
        assertFalse(containerExists(cId));
        assertTrue(containerExists(appContainer));
        zr.stop();
        DOCKER_CLIENT.killContainerCmd(appContainer).exec();
    }

    @Test
    @SneakyThrows
    void testOtherTaskUnaffected() {
        //Following should not get killed
        val taskSpec = ExecutorTestingUtils.testTaskInstanceSpec();
        val taskInstanceData = ExecutorTestingUtils.createExecutorTaskInfo(taskSpec);
        val cId = ExecutorTestingUtils.startTestTaskContainer(taskSpec, taskInstanceData, MAPPER);

        val appSpec = ExecutorTestingUtils.testAppInstanceSpec();
        val appInstanceData = ExecutorTestingUtils.createExecutorAppInstanceInfo(appSpec, 8080);
        val appContainer = ExecutorTestingUtils.startTestAppContainer(appSpec, appInstanceData, MAPPER);

        val ae = mock(ApplicationInstanceEngine.class);

        val te = mock(TaskInstanceEngine.class);
        when(te.instanceIds(any())).thenReturn(Set.of(taskInstanceData.getTaskId()));
        val cc = mock(ClusterClient.class);
        when(cc.currentKnownInstances()).thenReturn(KnownInstancesData.EMPTY);

        val zr = new ZombieInstanceReaper(DOCKER_CLIENT, ae, te, Duration.ofSeconds(1), cc);
        zr.start();
        CommonTestUtils.waitUntil(() -> !containerExists(appContainer));
        assertTrue(containerExists(cId));
        assertFalse(containerExists(appContainer));
        zr.stop();
        DOCKER_CLIENT.killContainerCmd(cId).exec();
    }

    @Test
    @SneakyThrows
    void testKillAppLessInstance() {
        val ids = new LinkedHashSet<String>();
        IntStream.rangeClosed(0,100).forEach(i -> ids.add("AI" + i));
        val killedIds = ids.stream().limit(10).collect(Collectors.toSet()); //Kill 10
        val ae = mock(ApplicationInstanceEngine.class);
        when(ae.instanceIds(any())).thenReturn(ids);
        when(ae.stopInstance(anyString())).thenAnswer(invocationOnMock -> {
            val id = invocationOnMock.getArgument(0, String.class);
            if(killedIds.contains(id)) {
                ids.remove(id);
            }
            return true;
        });
        val cc = mock(ClusterClient.class);
        when(cc.currentKnownInstances()).thenReturn(new KnownInstancesData(Set.copyOf(Sets.difference(ids, killedIds)),
                                                                           killedIds,
                                                                           Set.of(),
                                                                           Set.of()));
        val te = mock(TaskInstanceEngine.class);
        val zr = new ZombieInstanceReaper(DOCKER_CLIENT, ae, te, Duration.ofSeconds(1), cc);
        zr.start();
        CommonTestUtils.waitUntil(() -> Sets.intersection(ids, killedIds).isEmpty());
        assertTrue(Sets.intersection(ids, killedIds).isEmpty());
        zr.stop();
    }
}