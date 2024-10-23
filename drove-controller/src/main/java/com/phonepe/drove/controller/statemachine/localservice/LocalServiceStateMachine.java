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

package com.phonepe.drove.controller.statemachine.localservice;

import com.phonepe.drove.models.localservice.LocalServiceInfo;
import com.phonepe.drove.models.localservice.LocalServiceState;
import com.phonepe.drove.models.operation.LocalServiceOperation;
import com.phonepe.drove.statemachine.*;
import lombok.NonNull;

import java.util.List;

/**
 *
 */
public class LocalServiceStateMachine extends StateMachine<LocalServiceInfo, LocalServiceOperation, LocalServiceState, LocalServiceActionContext, Action<LocalServiceInfo, LocalServiceState, LocalServiceActionContext, LocalServiceOperation>> {
    private static final List<Transition<LocalServiceInfo, LocalServiceOperation, LocalServiceState, LocalServiceActionContext, Action<LocalServiceInfo, LocalServiceState, LocalServiceActionContext, LocalServiceOperation>>> TRANSITIONS;

    static {
        TRANSITIONS = List.of(
                /*new Transition<>(INIT,
                                 CreateAppAction.class,
                                 MONITORING,
                                 RUNNING),
                new Transition<>(MONITORING,
                                 AppOperationRouterAction.class,
                                 OUTAGE_DETECTED,
                                 DESTROY_REQUESTED,
                                 SCALING_REQUESTED,
                                 MONITORING),
                new Transition<>(RUNNING,
                                 AppOperationRouterAction.class,
                                 STOP_INSTANCES_REQUESTED,
                                 SCALING_REQUESTED,
                                 REPLACE_INSTANCES_REQUESTED,
                                 OUTAGE_DETECTED,
                                 RUNNING),
                new Transition<>(OUTAGE_DETECTED,
                                 RecoverAppAction.class,
                                 SCALING_REQUESTED),
                new Transition<>(SCALING_REQUESTED,
                                 ScaleAppAction.class,
                                 SCALING_REQUESTED,
                                 RUNNING,
                                 MONITORING),
                new Transition<>(REPLACE_INSTANCES_REQUESTED,
                                 ReplaceInstancesAppAction.class,
                                 RUNNING,
                                 MONITORING),
                new Transition<>(STOP_INSTANCES_REQUESTED,
                                 StopAppInstancesAction.class,
                                 RUNNING,
                                 MONITORING),
                new Transition<>(DESTROY_REQUESTED,
                                 DestroyAppAction.class,
                                 DESTROYED)*/);
    }

    public LocalServiceStateMachine(
            @NonNull StateData<LocalServiceState, LocalServiceInfo> initalState,
            LocalServiceActionContext context,
            ActionFactory<LocalServiceInfo, LocalServiceOperation, LocalServiceState, LocalServiceActionContext, Action<LocalServiceInfo, LocalServiceState, LocalServiceActionContext, LocalServiceOperation>> actionFactory) {
        super(initalState, context, actionFactory, TRANSITIONS);
    }

}
