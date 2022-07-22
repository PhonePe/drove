package com.phonepe.drove.controller.engine;

import com.phonepe.drove.controller.resourcemgmt.ClusterResourcesDB;
import com.phonepe.drove.controller.statedb.ApplicationInstanceInfoDB;
import com.phonepe.drove.models.info.ExecutorResourceSnapshot;
import com.phonepe.drove.models.info.nodedata.ExecutorNodeData;
import com.phonepe.drove.models.instance.InstanceInfo;
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
    private final ApplicationInstanceInfoDB instanceInfoDB;

    @Inject
    public StateUpdater(
            ClusterResourcesDB resourcesDB,
            ApplicationInstanceInfoDB instanceInfoDB) {
        this.resourcesDB = resourcesDB;
        this.instanceInfoDB = instanceInfoDB;
    }

    public void updateClusterResources(final List<ExecutorNodeData> children) {
        if (children.isEmpty()) {
            log.warn("No children found from ZK.");
            return;
        }
        resourcesDB.update(children);
        children.forEach(node -> node.getInstances()
                .forEach(this::updateInstanceInfo));
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


    private boolean updateInstanceInfo(InstanceInfo instanceInfo) {
        return instanceInfoDB.updateInstanceState(instanceInfo.getAppId(),
                                                  instanceInfo.getInstanceId(),
                                                  instanceInfo);
    }
}
