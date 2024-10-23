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

package com.phonepe.drove.controller.statemachine.applications;

import com.phonepe.drove.controller.statemachine.applications.actions.*;
import com.phonepe.drove.models.application.ApplicationInfo;
import com.phonepe.drove.models.application.ApplicationState;
import com.phonepe.drove.models.operation.ApplicationOperation;
import com.phonepe.drove.statemachine.*;
import lombok.NonNull;

import java.util.List;

import static com.phonepe.drove.models.application.ApplicationState.*;

/**
 *
 */
public class ApplicationStateMachine extends StateMachine<ApplicationInfo, ApplicationOperation, ApplicationState, AppActionContext, Action<ApplicationInfo, ApplicationState, AppActionContext, ApplicationOperation>> {
    private static final List<Transition<ApplicationInfo, ApplicationOperation, ApplicationState, AppActionContext, Action<ApplicationInfo, ApplicationState, AppActionContext, ApplicationOperation>>> TRANSITIONS;

    static {
        TRANSITIONS = List.of(
                new Transition<>(INIT,
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
                                 DESTROYED));
    }

    public ApplicationStateMachine(
            @NonNull StateData<ApplicationState, ApplicationInfo> initalState,
            AppActionContext context,
            ActionFactory<ApplicationInfo, ApplicationOperation, ApplicationState, AppActionContext, Action<ApplicationInfo, ApplicationState, AppActionContext, ApplicationOperation>> actionFactory) {
        super(initalState, context, actionFactory, TRANSITIONS);
    }

}
