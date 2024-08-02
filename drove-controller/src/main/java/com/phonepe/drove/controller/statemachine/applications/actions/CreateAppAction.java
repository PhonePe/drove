/*
 *  Copyright (c) 2022 Original Author(s), PhonePe India Pvt. Ltd.
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

package com.phonepe.drove.controller.statemachine.applications.actions;

import com.phonepe.drove.controller.statedb.ApplicationStateDB;
import com.phonepe.drove.controller.statemachine.applications.AppActionContext;
import com.phonepe.drove.controller.statemachine.applications.OperationDrivenAppAction;
import com.phonepe.drove.models.application.ApplicationInfo;
import com.phonepe.drove.models.application.ApplicationState;
import com.phonepe.drove.models.operation.ApplicationOperation;
import io.appform.functionmetrics.MonitoredFunction;
import com.phonepe.drove.statemachine.StateData;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.inject.Inject;

/**
 *
 */
@Slf4j
public class CreateAppAction extends OperationDrivenAppAction {
    private final ApplicationStateDB applicationStateDB;

    @Inject
    public CreateAppAction(ApplicationStateDB applicationStateDB) {
        this.applicationStateDB = applicationStateDB;
    }

    @Override
    @MonitoredFunction
    public StateData<ApplicationState, ApplicationInfo> commandReceived(
            AppActionContext context,
            StateData<ApplicationState, ApplicationInfo> currentState,
            ApplicationOperation operation) {
        applicationStateDB.updateApplicationState(context.getAppId(), currentState.getData());
        val expectedInstances = applicationStateDB.application(context.getAppId())
                .map(ApplicationInfo::getInstances)
                .orElse(0L);
        if (expectedInstances > 0) {
            log.info("{} instances are expected for this app.", expectedInstances);
            return StateData.from(currentState, ApplicationState.RUNNING);
        }
        return StateData.from(currentState, ApplicationState.MONITORING);
    }
}
