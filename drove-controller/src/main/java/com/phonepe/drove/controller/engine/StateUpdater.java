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

import static com.codahale.metrics.MetricRegistry.name;
import static com.phonepe.drove.controller.utils.EventUtils.instanceMetadata;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.phonepe.drove.common.discovery.NodeDataStore;
import com.phonepe.drove.controller.config.ControllerOptions;
import com.phonepe.drove.controller.event.DroveEventBus;
import com.phonepe.drove.controller.resourcemgmt.ClusterResourcesDB;
import com.phonepe.drove.controller.statedb.ApplicationInstanceInfoDB;
import com.phonepe.drove.controller.statedb.LocalServiceStateDB;
import com.phonepe.drove.controller.statedb.TaskDB;
import com.phonepe.drove.controller.utils.ControllerUtils;
import com.phonepe.drove.models.events.events.DroveInstanceStateChangeEvent;
import com.phonepe.drove.models.events.events.DroveLocalServiceInstanceStateChangeEvent;
import com.phonepe.drove.models.events.events.DroveTaskStateChangeEvent;
import com.phonepe.drove.models.info.ExecutorResourceSnapshot;
import com.phonepe.drove.models.info.nodedata.ExecutorNodeData;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.interfaces.DeployedInstanceInfo;
import com.phonepe.drove.models.interfaces.DeployedInstanceInfoVisitor;
import com.phonepe.drove.models.localservice.LocalServiceInstanceInfo;
import com.phonepe.drove.models.taskinstance.TaskInfo;

import io.appform.functionmetrics.MonitoredFunction;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.val;
import lombok.extern.slf4j.Slf4j;

/**
 *
 */
@Slf4j
@Singleton
public class StateUpdater implements AutoCloseable {
    private final ClusterResourcesDB resourcesDB;
    private final TaskDB taskDB;
    private final ApplicationInstanceInfoDB instanceInfoDB;
    private final LocalServiceStateDB localServiceStateDB;
    private final NodeDataStore nodeDataStore;

    private final DroveEventBus droveEventBus;

    private final Map<Integer, Partition> updates;

    private final ExecutorService executor;
    private final Timer updateDeliveryDelayTimer;
    private final int parallelism;
    private Partitioner partitioner;

    @RequiredArgsConstructor
    @Data
    private static final class Partition {
        private final int partitionId;
        private final LinkedBlockingDeque<UpdateData> queue = new LinkedBlockingDeque<>();
        Future<?> future;
    }

    @Getter
    private abstract class UpdateData {

        enum Priority {
            HIGH(1),
            LOW(2);

            @Getter
            private final int value;

            Priority(int value) {
                this.value = value;
            }
        }

        interface UpdateDataVisitor<T> {

            T visit(ExecutorSnapshotUpdateData executorSnapshot);

            T visit(InstanceUpdateData instanceData);

            T visit(RemoveExecutorUpdateData removeExecutors);
        }

        private final String id;
        private final Priority priority;
        private final long incrementalTime = System.nanoTime();

        abstract <T> T accept(final UpdateDataVisitor<T> visitor);

        protected UpdateData(String id, Priority priority) {
            this.id = id;
            this.priority = priority;
        }
    }

    @Getter
    private class ExecutorSnapshotUpdateData extends UpdateData {

        private final ExecutorNodeData node;
        private final boolean bulkUpdate;

        @Override
        <T> T accept(UpdateDataVisitor<T> visitor) {
            return visitor.visit(this);
        }

        ExecutorSnapshotUpdateData(ExecutorNodeData node, boolean bulkUpdate) {
            super("ExecutorSnapshotUpdate-" + Priority.LOW + "-" + System.currentTimeMillis() + "-" + node.getState().getExecutorId(),
                  Priority.LOW);
            this.node = node;
            this.bulkUpdate = bulkUpdate;
        }
    }

    @Getter
    private class RemoveExecutorUpdateData extends UpdateData {
        private final String executorId;

        private RemoveExecutorUpdateData(String executorId) {
            super("ExecutorRemovalUpdate-" + Priority.LOW + "-" + System.currentTimeMillis() + "-" + executorId,
                  Priority.LOW);
            this.executorId = executorId;
        }

        @Override
        <T> T accept(UpdateDataVisitor<T> visitor) {
            return visitor.visit(this);
        }

    }

    @Getter
    private class InstanceUpdateData extends UpdateData {

        private final ExecutorResourceSnapshot snapshot;
        private final DeployedInstanceInfo instanceInfo;


        protected InstanceUpdateData(
                ExecutorResourceSnapshot snapshot,
                DeployedInstanceInfo instanceInfo) {
            super("InstanceUpdate-" + Priority.HIGH
                          + "-" + System.currentTimeMillis() + "-" + ControllerUtils.deployableObjectId(instanceInfo),
                  Priority.HIGH);
            this.snapshot = snapshot;
            this.instanceInfo = instanceInfo;
        }

        @Override
        <T> T accept(UpdateDataVisitor<T> visitor) {
            return visitor.visit(this);
        }

    }

    @AllArgsConstructor
    private class UpdateHandler implements Callable<Void>, UpdateData.UpdateDataVisitor<Boolean> {

        private final Partition partition;

        @Override
        public Boolean visit(ExecutorSnapshotUpdateData executorSnapshot) {
            val node = executorSnapshot.getNode();
            var hasAnyData = false;
            var anyDataUpdated = false;
            val instances = node.getInstances();
            if (instances != null && !instances.isEmpty()) {
                hasAnyData = true;
                anyDataUpdated = updateAllAppInstances(anyDataUpdated, instances);
            }

            val tasks = node.getTasks();
            if (tasks != null && !tasks.isEmpty()) {
                hasAnyData = true;
                anyDataUpdated = updateAllTasks(anyDataUpdated, tasks);
            }

            val serviceInstances = node.getServiceInstances();
            if (serviceInstances != null && !serviceInstances.isEmpty()) {
                hasAnyData = true;
                anyDataUpdated = updateAllServiceInstances(anyDataUpdated, serviceInstances);
            }
            if(!executorSnapshot.isBulkUpdate()) {
                nodeDataStore.updateNodeData(node);
            }
            if (!hasAnyData || anyDataUpdated) {
                resourcesDB.update(List.of(node));
                return true;
            }
            return false;
        }


        @Override
        public Boolean visit(InstanceUpdateData instanceData) {
            val data = instanceData.getInstanceInfo();
            val updated = data.accept(new DeployedInstanceInfoVisitor<Boolean>() {
                @Override
                public Boolean visit(InstanceInfo applicationInstanceInfo) {
                    return updateInstanceInfo(applicationInstanceInfo);
                }

                @Override
                public Boolean visit(TaskInfo taskInfo) {
                    return updateTask(taskInfo);
                }

                @Override
                public Boolean visit(LocalServiceInstanceInfo localServiceInstanceInfo) {
                    return updateInstanceInfo(localServiceInstanceInfo);
                }
            });
            if (updated) {
                resourcesDB.update(instanceData.getSnapshot());
            }
            else {
                log.debug("Resource update skipped as instance info was ignored");
            }
            return updated;
        }

        @Override
        public Boolean visit(RemoveExecutorUpdateData removeExecutor) {
            log.debug("Processing update to remove executor {}",
                    removeExecutor.getExecutorId());
            val executorId = removeExecutor.getExecutorId();
            val hostInfo = resourcesDB.currentSnapshot(executorId).orElse(null);
            if(null == hostInfo) {
                log.warn("Received update to remove executor {}, but it was not found in resourcesDB.", executorId);
                return true; // We don't want to hold things up for this
            }
            final var nodeData = hostInfo.getNodeData();
            nodeData.getInstances()
                    .forEach(instance -> instanceInfoDB.deleteInstanceState(
                            instance.getAppId(), instance.getInstanceId()));
            nodeData.getServiceInstances()
                    .forEach(localServiceInstance -> localServiceStateDB.deleteInstanceState(
                            localServiceInstance.getServiceId(), localServiceInstance.getInstanceId()));
            resourcesDB.remove(List.of(executorId));
            log.info("Stopped tracking executor {}", executorId);
            return true;
        }

        @Override
        @SuppressWarnings("java:S2189")
        public Void call() {
            while (true) {
               processUpdateData(); 
            }
        }

        @MonitoredFunction
        private void processUpdateData() {
            val update = new AtomicReference<UpdateData>();
            try {
                update.set(partition.getQueue().take());
                val processingDelay = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - update.get().getIncrementalTime());
                log.debug("Pickup delay for {} update: {} ms", update.getClass().getSimpleName(), processingDelay);
                updateDeliveryDelayTimer.update(processingDelay, TimeUnit.MILLISECONDS);
                val updateStatus = update.get().accept(this);
                log.trace("Update status for {}: {}", update.get().getId(), updateStatus);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            catch (Throwable t) {
                if (null == update.get()) {
                    log.error("Error processing unknown update : " + t.getMessage(), t);
                }
                else {
                    log.error("Error processing update " + update.get().getId() + ": " + t.getMessage(), t);
                }
            }
        }

        private boolean updateAllTasks(boolean anyDataUpdated, List<TaskInfo> tasks) {
            for (val taskInfo : tasks) {
                if (updateTask(taskInfo)) {
                    anyDataUpdated = true;
                }
            }
            return anyDataUpdated;
        }

        private boolean updateAllAppInstances(boolean anyDataUpdated, List<InstanceInfo> instances) {
            for (val instanceInfo : instances) {
                if (updateInstanceInfo(instanceInfo)) {
                    anyDataUpdated = true;
                }
            }
            return anyDataUpdated;
        }

        private boolean updateAllServiceInstances(boolean anyDataUpdated, List<LocalServiceInstanceInfo> instances) {
            for (val instanceInfo : instances) {
                if (updateInstanceInfo(instanceInfo)) {
                    anyDataUpdated = true;
                }
            }
            return anyDataUpdated;
        }

        private boolean updateInstanceInfo(InstanceInfo instanceInfo) {
            val appId = instanceInfo.getAppId();
            val instanceId = instanceInfo.getInstanceId();
            val existing = instanceInfoDB.instance(appId, instanceId).orElse(null);
            val isNewUpdate = null == existing || existing.getUpdated().before(instanceInfo.getUpdated());
            if (!isNewUpdate) {
                log.trace("Ignoring stale state update for instance {}/{}. Existing: {} Current: {}",
                          appId, instanceId, existing.getUpdated().getTime(), instanceInfo.getUpdated().getTime());
                return false;
            }
            val accepted = instanceInfoDB.updateInstanceState(appId, instanceId, instanceInfo);
            if (accepted && (null == existing || !existing.getState().equals(instanceInfo.getState()))) {
                droveEventBus.publish(new DroveInstanceStateChangeEvent(instanceMetadata(instanceInfo)));
            }
            return accepted;
        }

        private boolean updateTask(TaskInfo instanceInfo) {
            val sourceAppName = instanceInfo.getSourceAppName();
            val taskId = instanceInfo.getTaskId();
            val existing = taskDB.task(sourceAppName, taskId).orElse(null);
            val isNewUpdate = null == existing || existing.getUpdated().before(instanceInfo.getUpdated());
            if (!isNewUpdate) {
                log.trace("Ignoring stale state update for task {}/{}. Existing: {} Current: {}",
                          sourceAppName, taskId, existing.getUpdated().getTime(), instanceInfo.getUpdated().getTime());
                return false;
            }
            val accepted = taskDB.updateTask(sourceAppName, taskId, instanceInfo);
            if (accepted && (null == existing || !existing.getState().equals(instanceInfo.getState()))) {
                droveEventBus.publish(new DroveTaskStateChangeEvent(instanceMetadata(instanceInfo)));
            }
            return accepted;
        }

        private boolean updateInstanceInfo(LocalServiceInstanceInfo instanceInfo) {
            val serviceId = instanceInfo.getServiceId();
            val instanceId = instanceInfo.getInstanceId();
            val existing = localServiceStateDB.instance(serviceId, instanceId).orElse(null);
            val isNewUpdate = null == existing || existing.getUpdated().before(instanceInfo.getUpdated());
            if (!isNewUpdate) {
                log.trace("Ignoring stale state update for local service instance {}/{}. Existing: {} Current: {}",
                          serviceId, instanceId, existing.getUpdated().getTime(), instanceInfo.getUpdated().getTime());
                return false;
            }
            val accepted = localServiceStateDB.updateInstanceState(serviceId, instanceId, instanceInfo);
            if (accepted && (null == existing || !existing.getState().equals(instanceInfo.getState()))) {
                droveEventBus.publish(new DroveLocalServiceInstanceStateChangeEvent(instanceMetadata(instanceInfo)));
            }
            return accepted;
        }
    }

    private static final class Partitioner implements UpdateData.UpdateDataVisitor<Boolean> {

        private final Map<Integer, Partition> updates;
        private final int parallelism;

        public Partitioner(Map<Integer, Partition> updates) {
            this.updates = updates;
            this.parallelism = updates.size();
        }

        public boolean add(UpdateData updateData) {
            return updateData.accept(this);
        }

        @Override
        public Boolean visit(ExecutorSnapshotUpdateData executorSnapshot) {
            return addToPartition(executorSnapshot.getNode().getState().getExecutorId(), executorSnapshot);
        }

        @Override
        public Boolean visit(InstanceUpdateData instanceData) {
            return addToPartition(instanceData.getSnapshot().getExecutorId(), instanceData);
        }

        @Override
        public Boolean visit(RemoveExecutorUpdateData removeExecutor) {
            return addToPartition(removeExecutor.getExecutorId(), removeExecutor);
        }

        private boolean addToPartition(String key, UpdateData updateData) {
            val partitionId = partition(key);
            val partition = updates.get(partitionId);
            Objects.requireNonNull(partition,
                    () -> "No partition found for key: %s. Computed: %s, Allowed: [0-%d]"
                        .formatted(key, partitionId, parallelism - 1));
            return partition.queue.add(updateData);
        }

        private int partition(String value) {
            return (value.hashCode() & 0x7fffffff) % parallelism;
        }
    }
        

    @Inject
    public StateUpdater(
            ClusterResourcesDB resourcesDB,
            TaskDB taskDB,
            ApplicationInstanceInfoDB instanceInfoDB,
            LocalServiceStateDB localServiceStateDB,
            NodeDataStore nodeDataStore,
            DroveEventBus droveEventBus,
            ControllerOptions controllerOptions,
            MetricRegistry metricRegistry) {
        this.resourcesDB = resourcesDB;
        this.taskDB = taskDB;
        this.instanceInfoDB = instanceInfoDB;
        this.localServiceStateDB = localServiceStateDB;
        this.nodeDataStore = nodeDataStore;
        this.droveEventBus = droveEventBus;
        this.updateDeliveryDelayTimer = metricRegistry.timer(
                name( "com", "phonepe", "drove", "controller", "stateUpdater", "updateDeliveryDelay"));
        this.parallelism = Objects.requireNonNullElse(controllerOptions.getDataUpdateParallelism(), ControllerOptions.DEFAULT_DATA_UPDATE_PARALLELISM);
        this.executor = Executors.newFixedThreadPool(this.parallelism);
        this.updates = new ConcurrentHashMap<>();
        IntStream.range(0, this.parallelism)
                .forEach(i -> {
                    val partition = new Partition(i);
                    partition.future = executor.submit(new UpdateHandler(partition));
                    updates.put(i, partition);
                });
        this.partitioner = new Partitioner(updates);
    }

    public void bulkUpdateClusterResources(final List<ExecutorNodeData> children) {
        if (children.isEmpty()) {
            log.warn("No children found from ZK.");
            return;
        }
        children.forEach(child -> partitioner.add(new ExecutorSnapshotUpdateData(child, true)));
    }

    public void updateClusterResource(final ExecutorNodeData nodeData) {
        partitioner.add(new ExecutorSnapshotUpdateData(nodeData, false));
    }

    public void remove(Collection<String> executorIds) {
        executorIds.forEach(executorId -> {
            partitioner.add(new RemoveExecutorUpdateData(executorId));
        });
    }

    public boolean updateSingle(final ExecutorResourceSnapshot snapshot, final InstanceInfo instanceInfo) {
        return partitioner.add(new InstanceUpdateData(snapshot, instanceInfo));
    }

    public boolean updateSingle(final ExecutorResourceSnapshot snapshot, final TaskInfo instanceInfo) {
        return partitioner.add(new InstanceUpdateData(snapshot, instanceInfo));
    }

    public boolean updateSingle(final ExecutorResourceSnapshot snapshot, final LocalServiceInstanceInfo instanceInfo) {
        return partitioner.add(new InstanceUpdateData(snapshot, instanceInfo));
    }

    @Override
    public void close() {
        for (val partition : updates.values()) {
            partition.future.cancel(true);
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {

                executor.shutdownNow();

                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    log.warn("ExecutorService did not terminate cleanly.");
                }
            }
        }
        catch (InterruptedException ie) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
