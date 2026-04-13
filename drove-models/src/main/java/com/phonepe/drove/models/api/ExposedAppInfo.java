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

package com.phonepe.drove.models.api;

import com.phonepe.drove.models.application.PortType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Value;

import java.util.Collection;
import java.util.Map;

/**
 * Information about exposed application endpoints
 */
@Value
@Schema(description = "Information about exposed endpoints for an application")
public class ExposedAppInfo {
    @Value
    @Schema(description = "Host and port information for an exposed endpoint")
    public static class ExposedHost {
        @Schema(description = "Hostname or IP address", example = "192.168.1.100")
        String host;

        @Schema(description = "Port number", example = "8080")
        int port;

        @Schema(description = "Type of port (HTTP, HTTPS, TCP, UDP)")
        PortType portType;
    }

    @Schema(description = "Application name", example = "my-service")
    String appName;

    @Schema(description = "Application ID", example = "my-service-1234abcd")
    String appId;

    @Schema(description = "Virtual host for routing", example = "my-service.example.com")
    String vhost;

    @Schema(description = "Application tags")
    Map<String, String> tags;

    @Schema(description = "List of hosts exposing this application")
    Collection<ExposedHost> hosts;
}
