package com.phonepe.drove.controller.engine;

import com.phonepe.drove.common.discovery.nodedata.ExecutorNodeData;
import com.phonepe.drove.common.model.ExecutorResourceSnapshot;
import com.phonepe.drove.controller.resources.ClusterResourcesDB;
import com.phonepe.drove.controller.statedb.ApplicationStateDB;
import com.phonepe.drove.models.instance.InstanceInfo;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

/**
 *
 */
@Slf4j
@Singleton
public class StateUpdater {
    private final ClusterResourcesDB resourcesDB;
    private final ApplicationStateDB applicationStateDB;

    @Inject
    public StateUpdater(
            ClusterResourcesDB resourcesDB,
            ApplicationStateDB applicationStateDB) {
        this.resourcesDB = resourcesDB;
        this.applicationStateDB = applicationStateDB;
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

    public boolean updateSingle(final ExecutorResourceSnapshot snapshot, final InstanceInfo instanceInfo) {
        resourcesDB.update(snapshot);
        return updateInstanceInfo(instanceInfo);
    }


    private boolean updateInstanceInfo(InstanceInfo instanceInfo) {
        return applicationStateDB.updateInstanceState(instanceInfo.getAppId(),
                                                      instanceInfo.getInstanceId(),
                                                      instanceInfo);
    }
}
