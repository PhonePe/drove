package com.phonepe.drove.controller.engine;

import com.phonepe.drove.controller.resourcemgmt.ClusterResourcesDB;
import com.phonepe.drove.controller.statedb.ApplicationInstanceInfoDB;
import com.phonepe.drove.controller.statedb.TaskDB;
import com.phonepe.drove.models.info.ExecutorResourceSnapshot;
import com.phonepe.drove.models.info.nodedata.ExecutorNodeData;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.taskinstance.TaskInfo;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 *
 */
@Slf4j
@Singleton
public class StateUpdater {
    private final ClusterResourcesDB resourcesDB;
    private final TaskDB taskDB;
    private final ApplicationInstanceInfoDB instanceInfoDB;

    private final Lock lock = new ReentrantLock();

    @Inject
    public StateUpdater(
            ClusterResourcesDB resourcesDB,
            TaskDB taskDB, ApplicationInstanceInfoDB instanceInfoDB) {
        this.resourcesDB = resourcesDB;
        this.taskDB = taskDB;
        this.instanceInfoDB = instanceInfoDB;
    }

    public void updateClusterResources(final List<ExecutorNodeData> children) {
        if (children.isEmpty()) {
            log.warn("No children found from ZK.");
            return;
        }
        lock.lock();
        try {
            var hasAnyData = false;
            var anyDataUpdated = false;
            for (val node : children) {
                val instances = node.getInstances();
                if (instances != null && !instances.isEmpty()) {
                    hasAnyData = true;
                    for (val instanceInfo : instances) {
                        if (updateInstanceInfo(instanceInfo)) {
                            anyDataUpdated = true;
                        }
                    }
                }
                val tasks = node.getTasks();
                if (tasks != null && !tasks.isEmpty()) {
                    hasAnyData = true;
                    for (val taskInfo : tasks) {
                        if (updateTask(taskInfo)) {
                            anyDataUpdated = true;
                        }
                    }
                }
            }
            if (!hasAnyData || anyDataUpdated) {
                resourcesDB.update(children);
            }
        }
        finally {
            lock.unlock();
        }
    }

    public void remove(Collection<String> executorIds) {
        lock.lock();
        try {
            executorIds.stream()
                    .map(executorId -> resourcesDB.currentSnapshot(executorId).orElse(null))
                    .filter(Objects::nonNull)
                    .flatMap(hostInfo -> hostInfo.getNodeData().getInstances().stream())
                    .forEach(instance -> instanceInfoDB.deleteInstanceState(instance.getAppId(), instance.getInstanceId()));
            resourcesDB.remove(executorIds);
        }
        finally {
            lock.unlock();
        }
    }

    public boolean updateSingle(final ExecutorResourceSnapshot snapshot, final InstanceInfo instanceInfo) {
        val locked = lock.tryLock();
        var status = false;
        if(locked) {
            try {
                status = updateInstanceInfo(instanceInfo);
                if (status) {
                    resourcesDB.update(snapshot);
                }
            }
            finally {
                lock.unlock();
            }
        }
        return locked && status;
    }

    public boolean updateSingle(final ExecutorResourceSnapshot snapshot, final TaskInfo instanceInfo) {
        val locked = lock.tryLock();
        var status = false;
        if(locked) {
            try {
                status = updateTask(instanceInfo);
                if (status) {
                    resourcesDB.update(snapshot);
                }
            }
            finally {
                lock.unlock();
            }
        }
        return locked && status;
    }

    private boolean updateInstanceInfo(InstanceInfo instanceInfo) {
        val appId = instanceInfo.getAppId();
        val instanceId = instanceInfo.getInstanceId();
        val existing = instanceInfoDB.instance(appId, instanceId).orElse(null);
        val isNewUpdate = null == existing || existing.getUpdated().before(instanceInfo.getUpdated());
        if (!isNewUpdate) {
            log.info("Ignoring stale state update for instance {}/{}", appId, instanceId);
            return false;
        }
        return instanceInfoDB.updateInstanceState(appId, instanceId, instanceInfo);
    }

    private boolean updateTask(TaskInfo instanceInfo) {
        val sourceAppName = instanceInfo.getSourceAppName();
        val taskId = instanceInfo.getTaskId();
        val existing = taskDB.task(sourceAppName, taskId).orElse(null);
        val isNewUpdate = null == existing || existing.getUpdated().before(instanceInfo.getUpdated());
        if (!isNewUpdate) {
            log.info("Ignoring stale state update for task {}/{}", sourceAppName, taskId);
            return false;
        }
        return taskDB.updateTask(sourceAppName,
                                 taskId,
                                 instanceInfo);
    }
}
