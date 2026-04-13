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

/**
 * HTTP methods/verbs for HTTP-based checks
 */
@Schema(description = "HTTP methods/verbs for HTTP-based health checks")
public enum HTTPVerb {
    @Schema(description = "HTTP GET request")
    GET,
    @Schema(description = "HTTP POST request")
    POST,
    @Schema(description = "HTTP PUT request")
    PUT
}
