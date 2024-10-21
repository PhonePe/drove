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

package com.phonepe.drove.executor.statemachine.localservice;

import com.github.dockerjava.api.DockerClient;
import com.phonepe.drove.common.model.LocalServiceInstanceSpec;
import com.phonepe.drove.executor.ExecutorActionFactory;
import com.phonepe.drove.executor.model.ExecutorLocalServiceInstanceInfo;
import com.phonepe.drove.executor.statemachine.ExecutorActionBase;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.executor.statemachine.localservice.actions.*;
import com.phonepe.drove.models.instance.LocalServiceInstanceState;
import com.phonepe.drove.statemachine.StateData;
import com.phonepe.drove.statemachine.StateMachine;
import com.phonepe.drove.statemachine.Transition;
import lombok.NonNull;

import java.util.List;

import static com.phonepe.drove.models.instance.LocalServiceInstanceState.*;

/**
 * State machine for local service instances
 */
public class LocalServiceInstanceStateMachine extends StateMachine<ExecutorLocalServiceInstanceInfo, Void, LocalServiceInstanceState, InstanceActionContext<LocalServiceInstanceSpec>, ExecutorActionBase<ExecutorLocalServiceInstanceInfo, LocalServiceInstanceState, LocalServiceInstanceSpec>> {
    private static final List<Transition<ExecutorLocalServiceInstanceInfo, Void, LocalServiceInstanceState, InstanceActionContext<LocalServiceInstanceSpec>, ExecutorActionBase<ExecutorLocalServiceInstanceInfo, LocalServiceInstanceState, LocalServiceInstanceSpec>>> transitions
            = List.of(
            new Transition<>(PENDING,
                             LocalServiceInstanceSpecValidator.class,
                             PROVISIONING,
                             STOPPING),
            new Transition<>(PROVISIONING,
                             LocalServiceInstanceExecutableFetchAction.class,
                             STARTING,
                             PROVISIONING_FAILED),
            new Transition<>(STARTING,
                             LocalServiceInstanceRunAction.class,
                             UNREADY,
                             START_FAILED),
            new Transition<>(UNKNOWN,
                             LocalServiceInstanceRecoveryAction.class,
                             UNREADY,
                             STOPPED),
            new Transition<>(UNREADY,
                             LocalServiceInstanceReadinessCheckAction.class,
                             READY,
                             READINESS_CHECK_FAILED,
                             STOPPING),
            new Transition<>(READY,
                             LocalServiceInstanceSingularHealthCheckAction.class,
                             HEALTHY,
                             STOPPING),
            new Transition<>(HEALTHY,
                             LocalServiceInstanceHealthcheckAction.class,
                             UNHEALTHY,
                             STOPPING),
            new Transition<>(STOPPING,
                             LocalServiceInstanceStopAction.class,
                             DEPROVISIONING),
            new Transition<>(UNHEALTHY,
                             LocalServiceInstanceStopAction.class,
                             DEPROVISIONING),
            new Transition<>(PROVISIONING_FAILED,
                             LocalServiceInstanceDestroyAction.class,
                             DEPROVISIONING),
            new Transition<>(START_FAILED,
                             LocalServiceInstanceDestroyAction.class,
                             DEPROVISIONING),
            new Transition<>(READINESS_CHECK_FAILED,
                             LocalServiceInstanceStopAction.class,
                             DEPROVISIONING),
            new Transition<>(DEPROVISIONING,
                             LocalServiceExecutableCleanupAction.class,
                             STOPPED));

    public LocalServiceInstanceStateMachine(
            String executorId,
            LocalServiceInstanceSpec instanceSpec,
            @NonNull StateData<LocalServiceInstanceState, ExecutorLocalServiceInstanceInfo> initalState,
            ExecutorActionFactory<ExecutorLocalServiceInstanceInfo,
                    LocalServiceInstanceState, LocalServiceInstanceSpec> actionFactory,
            DockerClient client,
            boolean recovered) {
        super(initalState, new InstanceActionContext<>(executorId, instanceSpec, client, recovered), actionFactory, transitions);
    }
}
