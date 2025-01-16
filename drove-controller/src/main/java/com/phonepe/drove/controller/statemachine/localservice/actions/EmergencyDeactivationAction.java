/*
 *  Copyright (c) 2025 Original Author(s), PhonePe India Pvt. Ltd.
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

import com.phonepe.drove.controller.statemachine.localservice.LocalServiceAction;
import com.phonepe.drove.controller.statemachine.localservice.LocalServiceActionContext;
import com.phonepe.drove.models.localservice.LocalServiceInfo;
import com.phonepe.drove.models.localservice.LocalServiceState;
import com.phonepe.drove.models.operation.LocalServiceOperation;
import com.phonepe.drove.models.operation.LocalServiceOperationType;
import com.phonepe.drove.statemachine.StateData;
import dev.failsafe.Failsafe;
import dev.failsafe.RetryPolicy;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.inject.Inject;
import java.time.Duration;
import java.util.Optional;

/**
 *
 */
@Slf4j
@NoArgsConstructor(onConstructor_ = {@Inject})
public class EmergencyDeactivationAction extends LocalServiceAction {
    @Override
    public StateData<LocalServiceState, LocalServiceInfo> execute(
            LocalServiceActionContext context,
            StateData<LocalServiceState, LocalServiceInfo> currentState) {
        val opType = Failsafe.with(RetryPolicy.<Optional<LocalServiceOperation>>builder()
                              .withDelay(Duration.ofSeconds(1))
                              .withMaxAttempts(-1)
                              .handleResultIf(Optional::isEmpty)
                              .onFailedAttempt(attempt -> log.warn("Waiting for operation to be issued. Attempt: {}", attempt.getAttemptCount()))
                              .build())
                .get(context::getUpdate)
                .map(LocalServiceOperation::getType)
                .orElse(null);
        if(null != opType && opType.equals(LocalServiceOperationType.DEACTIVATE)) {
            log.info("Deactivation command received will move to proper state now.");
            return StateData.from(currentState, LocalServiceState.DEACTIVATION_REQUESTED);
        }
        return StateData.from(currentState, LocalServiceState.EMERGENCY_DEACTIVATION_REQUESTED);
    }
}
