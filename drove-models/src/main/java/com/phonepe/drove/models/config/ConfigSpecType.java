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

package com.phonepe.drove.models.config;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Source of configuration for the application or task instance
 */
@Schema(description = "Source type for configuration data injection into containers")
public enum ConfigSpecType {
    @Schema(description = "Configuration data is provided inline in the spec")
    INLINE,
    @Schema(description = "Configuration is read from a local file on the executor host")
    EXECUTOR_LOCAL_FILE,
    @Schema(description = "Configuration is fetched by controller via HTTP and sent to executor")
    CONTROLLER_HTTP_FETCH,
    @Schema(description = "Configuration is fetched by executor directly via HTTP at container startup")
    EXECUTOR_HTTP_FETCH
}
