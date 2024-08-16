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

import com.phonepe.drove.controller.event.DroveEventBus;
import com.phonepe.drove.models.events.events.DroveInstanceStateChangeEvent;
import com.phonepe.drove.models.events.events.DroveTaskStateChangeEvent;
import com.phonepe.drove.controller.resourcemgmt.ClusterResourcesDB;
import com.phonepe.drove.controller.statedb.ApplicationInstanceInfoDB;
import com.phonepe.drove.controller.statedb.TaskDB;
import com.phonepe.drove.controller.utils.ControllerUtils;
import com.phonepe.drove.models.info.ExecutorResourceSnapshot;
import com.phonepe.drove.models.info.nodedata.ExecutorNodeData;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.interfaces.DeployedInstanceInfo;
import com.phonepe.drove.models.interfaces.DeployedInstanceInfoVisitor;
import com.phonepe.drove.models.taskinstance.TaskInfo;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;

import static com.phonepe.drove.controller.utils.EventUtils.instanceMetadata;

/**
 *
 */
@Slf4j
@Singleton
public class StateUpdater {
    private final ClusterResourcesDB resourcesDB;
    private final TaskDB taskDB;
    private final ApplicationInstanceInfoDB instanceInfoDB;

    private final DroveEventBus droveEventBus;

    private final PriorityBlockingQueue<UpdateData> updates
            = new PriorityBlockingQueue<>(1024, Comparator.comparing(d -> d.getPriority().getValue()));

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

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

        abstract <T> T accept(final UpdateDataVisitor<T> visitor);

        protected UpdateData(String id, Priority priority) {
            this.id = id;
            this.priority = priority;
        }
    }

    private class ExecutorSnapshotUpdateData extends UpdateData {

        @Getter
        private final List<ExecutorNodeData> nodes;

        @Override
        <T> T accept(UpdateDataVisitor<T> visitor) {
            return visitor.visit(this);
        }

        ExecutorSnapshotUpdateData(List<ExecutorNodeData> nodes) {
            super("ExecutorSnapshotUpdate-" + Priority.LOW + "-" + System.currentTimeMillis() + "-" + nodes.size(),
                  Priority.LOW);
            this.nodes = nodes;
        }
    }

    @Getter
    private class RemoveExecutorUpdateData extends UpdateData {
        private final Collection<String> executorIds;

        private RemoveExecutorUpdateData(Collection<String> executorIds) {
            super("ExecutorRemovalUpdate-" + Priority.LOW + "-" + System.currentTimeMillis() + "-" + executorIds.size(),
                  Priority.LOW);
            this.executorIds = executorIds;
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

    private class UpdateHandler implements Callable<Void>, UpdateData.UpdateDataVisitor<Boolean> {

        @Override
        public Boolean visit(ExecutorSnapshotUpdateData executorSnapshot) {
            val children = executorSnapshot.getNodes();
            var hasAnyData = false;
            var anyDataUpdated = false;
            for (val node : children) {
                val instances = node.getInstances();
                if (instances != null && !instances.isEmpty()) {
                    hasAnyData = true;
                    anyDataUpdated = updateAllInstances(anyDataUpdated, instances);
                }
                val tasks = node.getTasks();
                if (tasks != null && !tasks.isEmpty()) {
                    hasAnyData = true;
                    anyDataUpdated = updateAllTasks(anyDataUpdated, tasks);
                }
            }
            if (!hasAnyData || anyDataUpdated) {
                resourcesDB.update(children);
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
        public Boolean visit(RemoveExecutorUpdateData removeExecutors) {
            val executorIds = removeExecutors.getExecutorIds();
            executorIds.stream()
                    .map(executorId -> resourcesDB.currentSnapshot(executorId).orElse(null))
                    .filter(Objects::nonNull)
                    .flatMap(hostInfo -> hostInfo.getNodeData().getInstances().stream())
                    .forEach(instance -> instanceInfoDB.deleteInstanceState(instance.getAppId(),
                                                                            instance.getInstanceId()));
            resourcesDB.remove(executorIds);
            return true;
        }

        @Override
        @SuppressWarnings("java:S2189")
        public Void call() {
            while (true) {
                val update = new AtomicReference<UpdateData>();
                try {
                    update.set(updates.take());
                    val updateStatus = update.get().accept(this);
                    log.trace("Update status for {}: {}", update.get().getId(), updateStatus);
                }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;
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
        }

        private boolean updateAllTasks(boolean anyDataUpdated, List<TaskInfo> tasks) {
            for (val taskInfo : tasks) {
                if (updateTask(taskInfo)) {
                    anyDataUpdated = true;
                }
            }
            return anyDataUpdated;
        }

        private boolean updateAllInstances(boolean anyDataUpdated, List<InstanceInfo> instances) {
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
    }

    @Inject
    public StateUpdater(
            ClusterResourcesDB resourcesDB,
            TaskDB taskDB,
            ApplicationInstanceInfoDB instanceInfoDB,
            DroveEventBus droveEventBus) {
        this.resourcesDB = resourcesDB;
        this.taskDB = taskDB;
        this.instanceInfoDB = instanceInfoDB;
        this.droveEventBus = droveEventBus;
        this.executor.submit(new UpdateHandler());
    }

    public void updateClusterResources(final List<ExecutorNodeData> children) {
        if (children.isEmpty()) {
            log.warn("No children found from ZK.");
            return;
        }
        updates.add(new ExecutorSnapshotUpdateData(children));
    }

    public void remove(Collection<String> executorIds) {
        updates.add(new RemoveExecutorUpdateData(executorIds));
    }

    public boolean updateSingle(final ExecutorResourceSnapshot snapshot, final InstanceInfo instanceInfo) {
        return updates.add(new InstanceUpdateData(snapshot, instanceInfo));
    }

    public boolean updateSingle(final ExecutorResourceSnapshot snapshot, final TaskInfo instanceInfo) {
        return updates.add(new InstanceUpdateData(snapshot, instanceInfo));
    }
}
