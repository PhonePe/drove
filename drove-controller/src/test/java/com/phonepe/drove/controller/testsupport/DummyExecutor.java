package com.phonepe.drove.controller.testsupport;

import com.phonepe.drove.common.model.MessageDeliveryStatus;
import com.phonepe.drove.common.model.MessageResponse;
import com.phonepe.drove.common.model.executor.*;
import com.phonepe.drove.controller.ControllerTestUtils;
import com.phonepe.drove.controller.resourcemgmt.ClusterResourcesDB;
import com.phonepe.drove.controller.statedb.ApplicationInstanceInfoDB;
import com.phonepe.drove.models.application.PortSpec;
import com.phonepe.drove.models.info.ExecutorResourceSnapshot;
import com.phonepe.drove.models.info.nodedata.ExecutorNodeData;
import com.phonepe.drove.models.info.nodedata.NodeTransportType;
import com.phonepe.drove.models.info.resources.allocation.CPUAllocation;
import com.phonepe.drove.models.info.resources.allocation.MemoryAllocation;
import com.phonepe.drove.models.info.resources.allocation.ResourceAllocationVisitor;
import com.phonepe.drove.models.info.resources.available.AvailableCPU;
import com.phonepe.drove.models.info.resources.available.AvailableMemory;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.instance.InstancePort;
import com.phonepe.drove.models.instance.InstanceState;
import com.phonepe.drove.models.instance.LocalInstanceInfo;
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
    private final ClusterResourcesDB clusterResourcesDB;
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private Future<?> jobFuture;

    private final Map<String, InstanceInfo> instances = new ConcurrentHashMap<>();
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
    public DummyExecutor(ApplicationInstanceInfoDB instanceInfoDB, ClusterResourcesDB clusterResourcesDB) {
        this.instanceInfoDB = instanceInfoDB;
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

    public void dropInstance(final String instanceId) {
        lock.lock();
        try {
            val instance = instances.get(instanceId);
            if(null == instance) {
                return;
            }
            instances.remove(instanceId);
            freeupResources(instance);
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
                instances.put(instanceId, instanceInfo);
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
                val instance = instances.get(stopInstanceMessage.getInstanceId());
                Objects.requireNonNull(instance);
                freeupResources(instance);
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
        });
    }

    private void freeupResources(InstanceInfo instance) {
        instance.getResources()
                .forEach(r -> r.accept(new ResourceAllocationVisitor<Void>() {
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
                                                                             Map.of(0, usedCPUs)),
                                                            new AvailableMemory(Map.of(0,
                                                                                       availableMemory.get()),
                                                                                Map.of(0,
                                                                                       TOTAL_MEMORY - availableMemory.get())));
        clusterResourcesDB.update(List.of(new ExecutorNodeData("localhost",
                                                               8080,
                                                               NodeTransportType.HTTP,
                                                               new Date(),
                                                               resourceSnapshot,
                                                               List.copyOf(instances.values()),
                                                               List.of(),
                                                               Set.of(),
                                                               false)));
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
