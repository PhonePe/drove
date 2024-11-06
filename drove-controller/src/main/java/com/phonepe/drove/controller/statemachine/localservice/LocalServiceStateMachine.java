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

import com.phonepe.drove.controller.statemachine.localservice.actions.*;
import com.phonepe.drove.models.localservice.LocalServiceInfo;
import com.phonepe.drove.models.localservice.LocalServiceState;
import com.phonepe.drove.models.operation.LocalServiceOperation;
import com.phonepe.drove.statemachine.*;
import lombok.NonNull;

import java.util.List;

import static com.phonepe.drove.models.localservice.LocalServiceState.*;

/**
 *
 */
public class LocalServiceStateMachine extends StateMachine<LocalServiceInfo, LocalServiceOperation, LocalServiceState, LocalServiceActionContext, Action<LocalServiceInfo, LocalServiceState, LocalServiceActionContext, LocalServiceOperation>> {
    private static final List<Transition<LocalServiceInfo, LocalServiceOperation, LocalServiceState, LocalServiceActionContext, Action<LocalServiceInfo, LocalServiceState, LocalServiceActionContext, LocalServiceOperation>>> TRANSITIONS;

    static {
        TRANSITIONS = List.of(
                new Transition<>(INIT,
                                 CreateLocalServiceAction.class,
                                 INACTIVE,
                                 ACTIVE,
                                 DESTROYED),
                new Transition<>(ACTIVE,
                                 RoutingLocalServiceAction.class,
                                 ACTIVATION_REQUESTED,
                                 DEACTIVATION_REQUESTED,
                                 SCALING,
                                 RESTARTING,
                                 DESTROY_REQUESTED,
                                 DESTROYED),
                new Transition<>(INACTIVE,
                                 RoutingLocalServiceAction.class,
                                 INACTIVE,
                                 ACTIVATION_REQUESTED,
                                 DEACTIVATION_REQUESTED,
                                 DESTROY_REQUESTED,
                                 SCALING,
                                 RESTARTING,
                                 DESTROYED),
                new Transition<>(ACTIVATION_REQUESTED,
                                 ActivateLocalServiceAction.class,
                                 ACTIVE,
                                 DESTROYED),
                new Transition<>(DEACTIVATION_REQUESTED,
                                 DeactivateLocalServiceAction.class,
                                 INACTIVE,
                                 DESTROYED),
                new Transition<>(SCALING,
                                 ScaleLocalServiceAction.class,
                                 ACTIVE,
                                 INACTIVE),
                new Transition<>(RESTARTING,
                                 ReplaceInstancesLocalServiceAction.class,
                                 RESTARTING,
                                 ACTIVE,
                                 INACTIVE),
                new Transition<>(DESTROY_REQUESTED,
                                 DestroyLocalServiceAction.class,
                                 DESTROYED));
    }

    public LocalServiceStateMachine(
            @NonNull StateData<LocalServiceState, LocalServiceInfo> initalState,
            LocalServiceActionContext context,
            ActionFactory<LocalServiceInfo, LocalServiceOperation, LocalServiceState, LocalServiceActionContext, Action<LocalServiceInfo, LocalServiceState, LocalServiceActionContext, LocalServiceOperation>> actionFactory) {
        super(initalState, context, actionFactory, TRANSITIONS);
    }

}
