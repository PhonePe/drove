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

package com.phonepe.drove.controller.resourcemgmt;

import com.google.common.base.Strings;
import com.phonepe.drove.controller.statemachine.common.actions.AsyncActionPlugin;
import com.phonepe.drove.controller.statemachine.common.actions.JobEnabledContext;
import com.phonepe.drove.jobexecutor.JobExecutionResult;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.util.UUID;

/**
 * Generates session ID and finalizes session
 */
@Slf4j
@AllArgsConstructor
public class SchedulerSessionManagementPlugin<C extends JobEnabledContext<D>, D> implements AsyncActionPlugin<C,D> {

    private final InstanceScheduler scheduler;

    @Override
    public void beforeTopologyCreation(C context, D operation) {
        val schedulingSessionId = UUID.randomUUID().toString();
        context.setSchedulingSessionId(schedulingSessionId);
        log.info("Started scheduling session: {}", schedulingSessionId);
    }

    @Override
    public void afterResultGenerated(C context, D operation, JobExecutionResult<Boolean> executionResult) {
        if (!Strings.isNullOrEmpty(context.getSchedulingSessionId())) {
            scheduler.finaliseSession(context.getSchedulingSessionId());
            log.debug("Scheduling session {} is now closed", context.getSchedulingSessionId());
            context.setSchedulingSessionId(null);
        }
    }
}
