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

package com.phonepe.drove.controller.testsupport;

import com.phonepe.drove.common.model.MessageDeliveryStatus;
import com.phonepe.drove.common.model.MessageResponse;
import com.phonepe.drove.common.model.executor.*;
import com.phonepe.drove.controller.ControllerTestUtils;
import com.phonepe.drove.controller.resourcemgmt.ClusterResourcesDB;
import com.phonepe.drove.controller.statedb.ApplicationInstanceInfoDB;
import com.phonepe.drove.controller.statedb.LocalServiceStateDB;
import com.phonepe.drove.models.application.PortSpec;
import com.phonepe.drove.models.info.ExecutorResourceSnapshot;
import com.phonepe.drove.models.info.nodedata.ExecutorNodeData;
import com.phonepe.drove.models.info.nodedata.ExecutorState;
import com.phonepe.drove.models.info.nodedata.NodeTransportType;
import com.phonepe.drove.models.info.resources.PhysicalLayout;
import com.phonepe.drove.models.info.resources.allocation.CPUAllocation;
import com.phonepe.drove.models.info.resources.allocation.MemoryAllocation;
import com.phonepe.drove.models.info.resources.allocation.ResourceAllocation;
import com.phonepe.drove.models.info.resources.allocation.ResourceAllocationVisitor;
import com.phonepe.drove.models.info.resources.available.AvailableCPU;
import com.phonepe.drove.models.info.resources.available.AvailableMemory;
import com.phonepe.drove.models.instance.*;
import com.phonepe.drove.models.localservice.LocalServiceInstanceInfo;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.phonepe.drove.models.instance.InstanceState.HEALTHY;
import static com.phonepe.drove.models.instance.InstanceState.STOPPED;

/**
 *
 */
@Slf4j
@Singleton
public class DummyExecutor implements Runnable, AutoCloseable {
    private static final int NUM_CPUS = 8;
    private static final long TOTAL_MEMORY = 8 * 512L;

    private final ApplicationInstanceInfoDB instanceInfoDB;
    private final LocalServiceStateDB localServiceStateDB;
    private final ClusterResourcesDB clusterResourcesDB;
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private Future<?> jobFuture;

    private final Map<String, InstanceInfo> appInstances = new ConcurrentHashMap<>();
    private final Map<String, LocalServiceInstanceInfo> serviceInstances = new ConcurrentHashMap<>();
    private final Lock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private boolean stopRequested = false;

    private final AtomicReference<ExecutorMessage> message = new AtomicReference<>();

    private final Set<Integer> availableCPUs = new HashSet<>(IntStream.range(0, NUM_CPUS)
                                                                     .boxed()
                                                                     .collect(Collectors.toSet()));
    private final Set<Integer> usedCPUs = new HashSet<>();
    private final AtomicLong availableMemory = new AtomicLong(TOTAL_MEMORY);

    @Inject
    public DummyExecutor(
            ApplicationInstanceInfoDB instanceInfoDB,
            LocalServiceStateDB localServiceStateDB,
            ClusterResourcesDB clusterResourcesDB) {
        this.instanceInfoDB = instanceInfoDB;
        this.localServiceStateDB = localServiceStateDB;
        this.clusterResourcesDB = clusterResourcesDB;
    }

    public void start() {
        jobFuture = executorService.submit(this);
        updateSnapshot();
        log.info("Started...");
    }

    public void stop() {
        lock.lock();
        try {
            stopRequested = true;
            condition.signalAll();
        }
        finally {
            lock.unlock();
        }
        try {
            jobFuture.get();
            log.info("Stopped");

        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        catch (ExecutionException e) {
            log.error("Error in dummy executor: " + e.getCause().getMessage(), e);
        }
        executorService.shutdownNow();
    }

    @Override
    public void run() {
        while (true) {
            lock.lock();
            try {
                while (!stopRequested && null == message.get()) {
                    condition.await();
                }
                if (stopRequested) {
                    return;
                }
                handleMessage(message.get());
                message.set(null);
            }
            catch (Exception e) {
                log.error("Error", e);
            }
            finally {
                lock.unlock();
            }
        }
    }


    public MessageResponse receiveMessage(final ExecutorMessage message) {
        lock.lock();
        try {
            if (null == this.message.get()) {
                this.message.set(message);
                condition.signalAll();
                return new MessageResponse(message.getHeader(), MessageDeliveryStatus.ACCEPTED);
            }
        }
        finally {
            lock.unlock();
        }
        log.warn("Message is already being processed: {}", message);
        return new MessageResponse(message.getHeader(), MessageDeliveryStatus.REJECTED);
    }

    public void dropAppInstance(final String instanceId) {
        lock.lock();
        try {
            val instance = appInstances.get(instanceId);
            if (null == instance) {
                return;
            }
            appInstances.remove(instanceId);
            freeupResources(instance.getResources());
            updateSnapshot();
        }
        finally {
            lock.unlock();
        }
    }

    public void dropServiceInstance(final String instanceId) {
        lock.lock();
        try {
            val instance = serviceInstances.get(instanceId);
            if (null == instance) {
                return;
            }
            serviceInstances.remove(instanceId);
            freeupResources(instance.getResources());
            updateSnapshot();
        }
        finally {
            lock.unlock();
        }
    }

    private void handleMessage(final ExecutorMessage message) {
        log.info("Processing message of type {}", message.getType());
        message.accept(new ExecutorMessageVisitor<Void>() {
            @Override
            public Void visit(StartInstanceMessage startInstanceMessage) {
                val spec = startInstanceMessage.getSpec();
                val random = new Random();
                val ports = new LocalInstanceInfo(
                        "localhost",
                        spec.getPorts()
                                .stream()
                                .collect(Collectors.toMap(
                                        PortSpec::getName, p -> new InstancePort(
                                                p.getPort(), random.nextInt(65_535), p.getType()))));
                val appId = spec.getAppId();
                val instanceId = spec.getInstanceId();
                val instanceInfo = new InstanceInfo(
                        appId,
                        spec.getAppName(),
                        instanceId,
                        ControllerTestUtils.EXECUTOR_ID,
                        ports,
                        spec.getResources(),
                        HEALTHY,
                        Map.of(),
                        "",
                        new Date(),
                        new Date());
                appInstances.put(instanceId, instanceInfo);
                spec.getResources()
                        .forEach(r -> r.accept(new ResourceAllocationVisitor<Void>() {
                            @Override
                            public Void visit(CPUAllocation cpu) {
                                usedCPUs.addAll(cpu.getCores().get(0));
                                availableCPUs.removeAll(usedCPUs);
                                return null;
                            }

                            @Override
                            public Void visit(MemoryAllocation memory) {
                                availableMemory.updateAndGet(v -> v - memory.getMemoryInMB().get(0));
                                return null;
                            }
                        }));
                updateSnapshot();
                instanceInfoDB.updateInstanceState(appId, instanceId, instanceInfo);
                return null;
            }

            @Override
            public Void visit(StopInstanceMessage stopInstanceMessage) {
                val instance = appInstances.get(stopInstanceMessage.getInstanceId());
                Objects.requireNonNull(instance);
                freeupResources(instance.getResources());
                instanceInfoDB.updateInstanceState(instance.getAppId(),
                                                   instance.getInstanceId(),
                                                   createInstanceInfo(instance, STOPPED));
                updateSnapshot();
                return null;
            }

            @Override
            public Void visit(StartTaskMessage startTaskMessage) {
                return null;
            }

            @Override
            public Void visit(StopTaskMessage stopTaskMessage) {
                return null;
            }

            @Override
            public Void visit(BlacklistExecutorMessage blacklistExecutorMessage) {
                return null;
            }

            @Override
            public Void visit(UnBlacklistExecutorMessage unBlacklistExecutorMessage) {
                return null;
            }

            @Override
            public Void visit(StartLocalServiceInstanceMessage startLocalServiceInstanceMessage) {
                val spec = startLocalServiceInstanceMessage.getSpec();
                val random = new Random();
                val ports = new LocalInstanceInfo(
                        "localhost",
                        spec.getPorts()
                                .stream()
                                .collect(Collectors.toMap(
                                        PortSpec::getName, p -> new InstancePort(
                                                p.getPort(), random.nextInt(65_535), p.getType()))));
                val serviceId = spec.getServiceId();
                val instanceId = spec.getInstanceId();
                val instanceInfo = new LocalServiceInstanceInfo(
                        serviceId,
                        spec.getServiceName(),
                        instanceId,
                        ControllerTestUtils.EXECUTOR_ID,
                        ports,
                        spec.getResources(),
                        LocalServiceInstanceState.HEALTHY,
                        Map.of(),
                        "",
                        new Date(),
                        new Date());
                serviceInstances.put(instanceId, instanceInfo);
                spec.getResources()
                        .forEach(r -> r.accept(new ResourceAllocationVisitor<Void>() {
                            @Override
                            public Void visit(CPUAllocation cpu) {
                                usedCPUs.addAll(cpu.getCores().get(0));
                                availableCPUs.removeAll(usedCPUs);
                                return null;
                            }

                            @Override
                            public Void visit(MemoryAllocation memory) {
                                availableMemory.updateAndGet(v -> v - memory.getMemoryInMB().get(0));
                                return null;
                            }
                        }));
                updateSnapshot();
                localServiceStateDB.updateInstanceState(serviceId, instanceId, instanceInfo);
                return null;
            }

            @Override
            public Void visit(StopLocalServiceInstanceMessage stopLocalServiceInstanceMessage) {
                val instance = serviceInstances.get(stopLocalServiceInstanceMessage.getInstanceId());
                Objects.requireNonNull(instance);
                freeupResources(instance.getResources());
                localServiceStateDB.updateInstanceState(instance.getServiceId(),
                                                        instance.getInstanceId(),
                                                        instance.withState(LocalServiceInstanceState.STOPPED));
                updateSnapshot();
                return null;
            }
        });
    }

    private void freeupResources(final List<ResourceAllocation> resources) {
        resources.forEach(r -> r.accept(new ResourceAllocationVisitor<Void>() {
            @Override
            public Void visit(CPUAllocation cpu) {
                usedCPUs.removeAll(cpu.getCores().get(0));
                availableCPUs.addAll(cpu.getCores().get(0));
                return null;
            }

            @Override
            public Void visit(MemoryAllocation memory) {
                availableMemory.updateAndGet(v -> v + memory.getMemoryInMB().get(0));
                return null;
            }
        }));
    }

    private void updateSnapshot() {
        val resourceSnapshot = new ExecutorResourceSnapshot(ControllerTestUtils.EXECUTOR_ID,
                                                            new AvailableCPU(Map.of(0, availableCPUs),
                                                                             Map.of(1, usedCPUs)),
                                                            new AvailableMemory(Map.of(0,
                                                                                       availableMemory.get()),
                                                                                Map.of(1,
                                                                                       TOTAL_MEMORY - availableMemory.get())),
                                                            new PhysicalLayout(Map.of(0,
                                                                                      availableCPUs,
                                                                                      1,
                                                                                      availableCPUs),
                                                                               Map.of(0,
                                                                                      TOTAL_MEMORY,
                                                                                      1,
                                                                                      TOTAL_MEMORY)));
        clusterResourcesDB.update(List.of(new ExecutorNodeData("localhost",
                                                               8080,
                                                               NodeTransportType.HTTP,
                                                               new Date(),
                                                               resourceSnapshot,
                                                               List.copyOf(appInstances.values()),
                                                               List.of(),
                                                               List.copyOf(serviceInstances.values()),
                                                               Set.of("localhost"),
                                                               ExecutorState.ACTIVE)));
        log.info("Snapshot updated");
    }

    private InstanceInfo createInstanceInfo(
            final InstanceInfo from, final InstanceState newState) {
        return new InstanceInfo(from.getAppId(),
                                from.getAppName(),
                                from.getInstanceId(),
                                from.getExecutorId(),
                                from.getLocalInfo(),
                                from.getResources(),
                                newState,
                                from.getMetadata(),
                                from.getErrorMessage(),
                                from.getCreated(),
                                new Date());
    }

    @Override
    public void close() {
        stop();
    }
}
