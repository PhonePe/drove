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

package com.phonepe.drove.controller.statemachine.common.actions;

import com.phonepe.drove.statemachine.ActionContext;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * A context that stores job information
 */
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public abstract class JobEnabledContext<D> extends ActionContext<D> {
    private String jobId;
    private String schedulingSessionId;
}
