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

package com.phonepe.drove.models.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Value;

import java.util.Date;

/**
 * Current state of the cluster with timestamp
 */
@Value
@Schema(description = "Current cluster state with the timestamp of the last state change")
public class ClusterStateData {
    @Schema(description = "Current state of the cluster")
    ClusterState state;

    @Schema(description = "Timestamp when the state was last updated")
    Date updated;
}
