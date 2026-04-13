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
import lombok.Value;
import lombok.With;

import java.util.Date;

/**
 * Information about a deployed application in the cluster.
 */
@Value
@With
@Schema(description = "Information about a deployed application in the cluster")
public class ApplicationInfo {
    @Schema(description = "Unique identifier of the application (name-version)", example = "MY_APP-1")
    String appId;

    @Schema(description = "Specification used to deploy this application")
    ApplicationSpec spec;

    @Schema(description = "Desired number of instances for this application", example = "3")
    long instances;

    @Schema(description = "Timestamp when the application was created")
    Date created;

    @Schema(description = "Timestamp when the application was last updated")
    Date updated;
}
