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

package com.phonepe.drove.models.localservice;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Value;
import lombok.With;

import java.util.Date;

/**
 * Information about a local service deployed in the cluster.
 */
@Value
@With
@Schema(description = "Information about a local service deployed in the cluster")
public class LocalServiceInfo {
    @Schema(description = "Unique identifier of the local service", example = "MY_LOCAL_SERVICE")
    String serviceId;

    @Schema(description = "Specification used to deploy this local service")
    LocalServiceSpec spec;

    @Schema(description = "Number of instances running on each executor host", example = "1")
    int instancesPerHost;

    @Schema(description = "Current activation state of the service")
    ActivationState activationState;

    @Schema(description = "Timestamp when the service was created")
    Date created;

    @Schema(description = "Timestamp when the service was last updated")
    Date updated;
}
