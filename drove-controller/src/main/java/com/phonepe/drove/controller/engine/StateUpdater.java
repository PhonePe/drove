package com.phonepe.drove.controller.engine;

import com.phonepe.drove.controller.resourcemgmt.ClusterResourcesDB;
import com.phonepe.drove.controller.statedb.ApplicationInstanceInfoDB;
import com.phonepe.drove.controller.statedb.TaskDB;
import com.phonepe.drove.models.info.ExecutorResourceSnapshot;
import com.phonepe.drove.models.info.nodedata.ExecutorNodeData;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.taskinstance.TaskInfo;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 *
 */
@Slf4j
@Singleton
public class StateUpdater {
    private final ClusterResourcesDB resourcesDB;
    private final TaskDB taskDB;
    private final ApplicationInstanceInfoDB instanceInfoDB;

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
        resourcesDB.update(children);
        children.forEach(node -> Objects.requireNonNullElse(node.getInstances(), List.<InstanceInfo>of())
                .forEach(this::updateInstanceInfo));
        children.forEach(node -> Objects.requireNonNullElse(node.getTasks(), List.<TaskInfo>of())
                .forEach(this::updateTask));
    }

    public void remove(Collection<String> executorIds) {
        executorIds.stream()
                .map(executorId -> resourcesDB.currentSnapshot(executorId).orElse(null))
                .filter(Objects::nonNull)
                .flatMap(hostInfo -> hostInfo.getNodeData().getInstances().stream())
                .forEach(instance -> instanceInfoDB.deleteInstanceState(instance.getAppId(), instance.getInstanceId()));

        resourcesDB.remove(executorIds);
    }

    public boolean updateSingle(final ExecutorResourceSnapshot snapshot, final InstanceInfo instanceInfo) {
        resourcesDB.update(snapshot);
        return updateInstanceInfo(instanceInfo);
    }
    public boolean updateSingle(final ExecutorResourceSnapshot snapshot, final TaskInfo instanceInfo) {
        resourcesDB.update(snapshot);
        return updateTask(instanceInfo);
    }


    private boolean updateInstanceInfo(InstanceInfo instanceInfo) {
        return instanceInfoDB.updateInstanceState(instanceInfo.getAppId(),
                                                  instanceInfo.getInstanceId(),
                                                  instanceInfo);
    }
    private boolean updateTask(TaskInfo instanceInfo) {
        return taskDB.updateTask(instanceInfo.getSourceAppName(),
                                     instanceInfo.getTaskId(),
                                     instanceInfo);
    }
}
