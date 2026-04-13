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

package com.phonepe.drove.models.application.checks;

import com.phonepe.drove.models.common.HTTPVerb;
import com.phonepe.drove.models.common.Protocol;
import io.dropwizard.util.Duration;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.Set;

/**
 * HTTP-based health check specification
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Jacksonized
@Builder
@Schema(description = "HTTP-based health check specification that calls an HTTP endpoint to verify instance health")
public class HTTPCheckModeSpec extends CheckModeSpec {

    @Schema(description = "Protocol to use for the HTTP check (defaults to HTTP)", example = "HTTP")
    Protocol protocol;

    @NotEmpty
    @Schema(description = "Name of the port to use for the health check (must match a port name in PortSpec)",
            example = "main", requiredMode = Schema.RequiredMode.REQUIRED)
    String portName;

    @NotEmpty
    @Schema(description = "URL path to call for the health check", example = "/health",
            requiredMode = Schema.RequiredMode.REQUIRED)
    String path;

    @NotNull
    @Schema(description = "HTTP verb to use for the health check request", requiredMode = Schema.RequiredMode.REQUIRED)
    HTTPVerb verb;

    @NotEmpty
    @NotNull
    @Schema(description = "Set of HTTP status codes that indicate a healthy response",
            example = "[200, 201, 204]", requiredMode = Schema.RequiredMode.REQUIRED)
    Set<Integer> successCodes;

    @Schema(description = "Optional payload to send with the request (for POST/PUT verbs)")
    String payload;

    @Schema(description = "Connection timeout for the HTTP request", example = "5 seconds")
    Duration connectionTimeout;

    @Schema(description = "Whether to skip TLS certificate verification for HTTPS checks", example = "false")
    boolean insecure;

    @SuppressWarnings("java:S107")
    public HTTPCheckModeSpec(
            Protocol protocol,
            String portName,
            String path,
            HTTPVerb verb,
            Set<Integer> successCodes,
            String payload,
            Duration connectionTimeout,
            boolean insecure) {
        super(CheckMode.HTTP);
        this.protocol = protocol;
        this.portName = portName;
        this.path = path;
        this.verb = verb;
        this.successCodes = successCodes;
        this.payload = payload;
        this.connectionTimeout = connectionTimeout;
        this.insecure = insecure;
    }

    @Override
    public <T> T accept(CheckModeSpecVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
