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

package com.phonepe.drove.controller.statemachine.common.actions;

import com.phonepe.drove.jobexecutor.JobExecutionResult;
import com.phonepe.drove.jobexecutor.JobTopology;

/**
 * This will be called in path before a topology is started and in process result
 */
public interface AsyncActionPlugin<T, S extends Enum<S>, C extends JobEnabledContext<D>, D> {

    default void beforeTopologyCreation(final C context, final D operation) {
        //Nothing to do here
    }

    default void beforeTopologySubmission(final C context, final D operation, final JobTopology<Boolean> topology) {
        //Nothing to do here
    }

    default void afterResultGenerated(final C context, final D operation, JobExecutionResult<Boolean> executionResult) {
        //Nothing to do here
    }
}
