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

package com.phonepe.drove.controller.statemachine.localservice.actions;

import com.phonepe.drove.common.model.utils.Pair;
import com.phonepe.drove.controller.resourcemgmt.ClusterResourcesDB;
import com.phonepe.drove.controller.statedb.LocalServiceStateDB;
import com.phonepe.drove.controller.statemachine.localservice.LocalServiceAction;
import com.phonepe.drove.controller.statemachine.localservice.LocalServiceActionContext;
import com.phonepe.drove.models.instance.LocalServiceInstanceState;
import com.phonepe.drove.models.localservice.LocalServiceInfo;
import com.phonepe.drove.models.localservice.LocalServiceInstanceInfo;
import com.phonepe.drove.models.localservice.LocalServiceState;
import com.phonepe.drove.statemachine.StateData;
import lombok.val;

import javax.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;

/**
 *
 */
public class ScaleLocalServiceAction extends LocalServiceAction {
    private final LocalServiceStateDB stateDB;
    private final ClusterResourcesDB clusterResourcesDB;

    @Inject
    public ScaleLocalServiceAction(LocalServiceStateDB stateDB, ClusterResourcesDB clusterResourcesDB) {
        this.stateDB = stateDB;
        this.clusterResourcesDB = clusterResourcesDB;
    }

    @Override
    public StateData<LocalServiceState, LocalServiceInfo> execute(
            LocalServiceActionContext context,
            StateData<LocalServiceState, LocalServiceInfo> currentState) {
        val executors = clusterResourcesDB.currentSnapshot(true);
        val currInfo = currentState.getData();
        val currInstances = stateDB.instances(currInfo.getServiceId(),
                                              LocalServiceInstanceState.ACTIVE_STATES);
        val instancesByExecutor = currInstances.stream()
                .collect(Collectors.groupingBy(LocalServiceInstanceInfo::getExecutorId));
        val pending = executors.stream()
                .map(executorHostInfo -> Pair.of(executorHostInfo.getExecutorId(),
                                                    Math.max(0,
                                                             currInfo.getInstancesPerHost()
                                                              - instancesByExecutor.getOrDefault(executorHostInfo.getExecutorId(),
                                                                                  List.of()).size())))
                .filter(pair -> pair.getSecond() > 0)
                .toList();
        if (pending.isEmpty()) {
            return StateData.from(currentState, LocalServiceState.ACTIVE);
        }
        return null;
    }
}
