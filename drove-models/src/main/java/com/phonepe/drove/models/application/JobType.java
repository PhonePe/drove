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

package com.phonepe.drove.models.application;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Type of job/workload being deployed
 */
@Schema(description = "Type of job/workload being deployed in the cluster")
public enum JobType {
    @Schema(description = "Long-running service application that runs continuously")
    SERVICE,
    @Schema(description = "One-time computation task that runs to completion")
    COMPUTATION,
    @Schema(description = "Service running locally on executor nodes")
    LOCAL_SERVICE
}
